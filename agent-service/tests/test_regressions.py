"""边界校验、候选产品、条款匹配、方案金额及 SSE 错误回归，不连接数据库、Milvus 或付费模型。"""
import sys
import types
import unittest
from decimal import Decimal
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch
from uuid import uuid4

from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.modules.chat_thread.schemas import ChatThreadCreate
from app.modules.chat_thread.router import router, get_service
from app.modules.insurance_plan.schemas import InsurancePlanCreate

# 只导入 rag.models 的类型声明，跳过包入口中会建立 Milvus 连接的初始化代码。
rag_package = types.ModuleType("app.rag")
rag_package.__path__ = [str(Path(__file__).resolve().parents[1] / "app" / "rag")]
with patch.dict(sys.modules, {"app.rag": rag_package}):
    from app.agents import tools


class SchemaTests(unittest.TestCase):
    def test_title_rejects_non_string_as_validation_error(self):
        for value in (123, None, ["标题"]):
            with self.subTest(value=value), self.assertRaises(ValidationError):
                ChatThreadCreate(title=value)

    def test_title_strips_whitespace_and_rejects_blank(self):
        self.assertEqual(ChatThreadCreate(title="  我的咨询  ").title, "我的咨询")
        with self.assertRaises(ValidationError):
            ChatThreadCreate(title="   ")

    def test_malformed_thread_ids_return_422(self):
        app = FastAPI()
        app.include_router(router)
        # 无效参数应在进入业务前被拦截，不调用任何真实服务。
        app.dependency_overrides[get_service] = lambda: object()
        with TestClient(app, raise_server_exceptions=False) as client:
            for method, path, body in (
                ("DELETE", "/api/v1/chat-threads/not-a-uuid", None),
                ("PATCH", "/api/v1/chat-threads/not-a-uuid", {"title": "测试"}),
                ("GET", "/api/v1/chat-threads/not-a-uuid/messages", None),
            ):
                with self.subTest(method=method):
                    response = client.request(method, path, headers={"x-user-id": "9"}, json=body)
                    self.assertEqual(response.status_code, 422)


class CandidateToolTests(unittest.IsolatedAsyncioTestCase):
    async def test_candidate_query_rejects_missing_or_empty_categories(self):
        for params in ({}, {"categories": []}):
            with self.subTest(params=params), self.assertRaises(ValidationError):
                await tools.query_candidate_products.ainvoke(params)

    async def test_candidate_query_preserves_response_shape(self):
        product = SimpleNamespace(
            id=2, name="测试产品", clause_name="测试条款", category="medical", insurer="测试保险公司",
            image_url=None, description=None, min_premium=Decimal("561.00"), max_premium=None,
            target_group=None, highlights=None,
        )
        with patch.object(tools, "AsyncSessionFactory") as sessions, \
                patch.object(tools, "ProductRepository") as repository:
            session = sessions.return_value.__aenter__.return_value
            query = AsyncMock(return_value=[product])
            repository.return_value.query_candidates = query
            result = await tools.query_candidate_products.ainvoke(
                {"categories": ["medical"], "premium_min": "600.00"}
            )
            repository.assert_called_once_with(session)
            query.assert_awaited_once_with(["medical"], Decimal("600.00"), 5)

        self.assertEqual(result, [{
            "id": 2, "name": "测试产品", "clause_name": "测试条款", "category": "medical", "insurer": "测试保险公司",
            "image_url": None, "description": None, "min_premium": "561.00", "max_premium": None,
            "target_group": None, "highlights": [],
        }])


class ProductRepositorySqlTests(unittest.IsolatedAsyncioTestCase):
    """不连数据库，只核对查询语句本身：限量、去重、排序、过滤四条语义错了都会静默改结果。"""

    @staticmethod
    def _session():
        session = SimpleNamespace()
        session.sql = []

        async def scalars(statement):
            session.sql.append(str(statement.compile(compile_kwargs={"literal_binds": True})))
            return SimpleNamespace(all=lambda: [])

        session.scalars = scalars
        return session

    async def test_candidates_dedupe_categories_and_limit_each(self):
        session = self._session()
        await tools.ProductRepository(session).query_candidates(["medical", "accident", "medical"], None, 3)

        self.assertEqual(len(session.sql), 2, "重复险种只应查一次")
        for sql in session.sql:
            self.assertIn("LIMIT 3", sql)
            self.assertIn("products.status = 'active'", sql)
            self.assertIn("products.min_premium ASC NULLS LAST", sql)
            self.assertIn("products.id ASC", sql)

    async def test_candidates_skip_query_when_limit_not_positive(self):
        for limit in (0, -3):
            with self.subTest(limit=limit):
                session = self._session()
                result = await tools.ProductRepository(session).query_candidates(["medical"], None, limit)
                self.assertEqual(result, [])
                self.assertEqual(session.sql, [])

    async def test_candidates_filter_premium_only_when_given(self):
        session = self._session()
        await tools.ProductRepository(session).query_candidates(["medical"], Decimal("600.00"), 5)
        self.assertIn("products.min_premium < 600.00", session.sql[0])

        session = self._session()
        await tools.ProductRepository(session).query_candidates(["medical"], None, 5)
        self.assertNotIn("min_premium <", session.sql[0])


class ClauseToolTests(unittest.IsolatedAsyncioTestCase):
    async def test_product_alias_resolves_to_catalog_id(self):
        product = SimpleNamespace(id=2, name="达尔文12号重大疾病保险", category="critical_illness", min_premium=Decimal("561.00"))
        chunk = SimpleNamespace(section_path="保障范围", content="条款正文")
        retriever = SimpleNamespace(retrieve=AsyncMock(return_value=[chunk]))
        with patch.object(tools, "AsyncSessionFactory") as sessions, \
                patch.object(tools, "ProductRepository") as repository, \
                patch.dict(sys.modules, {"app.rag": SimpleNamespace(retriever=retriever)}):
            session = sessions.return_value.__aenter__.return_value
            query = AsyncMock(return_value=[product])
            repository.return_value.query_by_category = query
            result = await tools.retrieve_insurance_clause.ainvoke(
                {"query": "保什么", "k": 3, "product_name": "达尔文12号"}
            )
            repository.assert_called_once_with(session)
            query.assert_awaited_once_with()
            retriever.retrieve.assert_awaited_once_with("保什么", 3, 2)
        self.assertIn("达尔文12号重大疾病保险", result[0])
        self.assertIn("条款正文", result[0])


class PlanToolTests(unittest.IsolatedAsyncioTestCase):
    async def test_saved_starting_price_comes_from_catalog_instead_of_model(self):
        plan = InsurancePlanCreate(plan_name="测试方案", items=[{
            "product_id": 2, "product_name": "模型填写的名称", "category": "life",
            "annual_premium_budget": "0.01",
        }])
        product = SimpleNamespace(id=2, name="实际商品名", category="critical_illness", min_premium=Decimal("561.00"))
        with patch.object(tools, "ProductRepository") as repository, \
                patch.object(tools, "AsyncSessionFactory"), \
                patch.object(tools, "InsurancePlanService") as service_class:
            repository.return_value.query_by_category = AsyncMock(return_value=[product])
            service_class.return_value.create_plan = AsyncMock(return_value=uuid4())
            result = await tools.save_insurance_plan.coroutine(plan, SimpleNamespace(context=SimpleNamespace(user_id=9)))
            saved = service_class.return_value.create_plan.call_args.kwargs["data"].items[0]
            self.assertEqual(saved.annual_premium_budget, Decimal("561.00"))
            self.assertEqual(saved.product_name, "实际商品名")
            self.assertEqual(saved.category, "critical_illness")
            self.assertIn("plan_id", result)

    async def test_unavailable_product_is_not_saved(self):
        plan = InsurancePlanCreate(plan_name="测试方案", items=[{
            "product_id": 999, "product_name": "不存在的商品", "category": "life",
        }])
        with patch.object(tools, "ProductRepository") as repository, \
                patch.object(tools, "AsyncSessionFactory"), \
                patch.object(tools, "InsurancePlanService") as service_class:
            repository.return_value.query_by_category = AsyncMock(return_value=[])
            result = await tools.save_insurance_plan.coroutine(plan, SimpleNamespace(context=SimpleNamespace(user_id=9)))
            self.assertNotIn("plan_id", result)
            service_class.assert_not_called()


class StreamTests(unittest.IsolatedAsyncioTestCase):
    async def test_agent_failure_is_an_error_event_instead_of_success(self):
        from app.modules.chat.schema import ChatRequest
        from app.modules.chat.service import ChatService
        agent = Mock()
        agent.astream_events = AsyncMock(side_effect=RuntimeError("模拟模型不可用"))
        service = ChatService(AsyncMock(), agent)
        events = [event async for event in service.chat_stream(9, ChatRequest(thread_id=uuid4(), message="测试"))]
        self.assertEqual([event.event for event in events], ["error"])


if __name__ == "__main__":
    unittest.main()
