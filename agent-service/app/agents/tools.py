from decimal import Decimal
from typing import Annotated, NamedTuple

from langchain.tools import tool
from langgraph.prebuilt import ToolRuntime
from pydantic import Field

from app.agents.schemas import InsuranceAgentContext
from app.infra.database import AsyncSessionFactory
from app.modules.insurance_plan.schemas import InsurancePlanCreate
from app.modules.insurance_plan.service import InsurancePlanService
from app.modules.product.repository import ProductRepository
from app.rag.models import ParentChunk


@tool
async def query_candidate_products(
    categories: Annotated[list[str], Field(min_length=1)],
    premium_min: Decimal | None = None,
    limit_per_category: int = 5,
) -> list[dict]:
    """根据险种和保费条件查询可用于推荐的候选保险产品。

    当用户咨询具体保险产品或需要保险产品推荐时使用。

    Args:
        categories: 产品分类列表，可选值为 medical、critical_illness、life、accident。
        premium_min: 最低保费上限，可选参数，只返回最低保费小于该值的产品。
        limit_per_category: 每个险种最多返回的产品数量，可选参数，默认5。
    """

    async with AsyncSessionFactory() as session:
        products = await ProductRepository(session).query_candidates(categories, premium_min, limit_per_category)

        # 保持工具原有出参：金额用字符串表示，无产品亮点时返回空列表。
        # 在 session 块内就把字段摘出来，别把 ORM 对象带到会话外面访问。
        return [
            {
                "id": product.id,
                "name": product.name,
                "clause_name": product.clause_name,
                "category": product.category,
                "insurer": product.insurer,
                "image_url": product.image_url,
                "description": product.description,
                "min_premium": str(product.min_premium) if product.min_premium is not None else None,
                "max_premium": str(product.max_premium) if product.max_premium is not None else None,
                "target_group": product.target_group,
                "highlights": product.highlights or [],
            }
            for product in products
        ]


class ProductBrief(NamedTuple):
    """在售目录里够用的几个字段，从会话里摘出来当值对象用。"""

    id: int
    name: str
    category: str
    min_premium: Decimal | None


async def _fetch_products() -> list[ProductBrief]:
    """读取 PostgreSQL 在售目录，用于条款产品匹配和方案保存前的校验。"""
    async with AsyncSessionFactory() as session:
        products = await ProductRepository(session).query_by_category()
        # 同样在会话内摘字段：ORM 对象带出 with 块后，一旦模型加了 relationship
        # 或把 expire_on_commit 改成 True，访问属性就会抛 DetachedInstanceError。
        return [ProductBrief(p.id, p.name, p.category, p.min_premium) for p in products]


def _normalize_name(name: str) -> str:
    return name.replace(" ", "").lower()


def _match_products(product_name: str, products: list[ProductBrief]) -> list[ProductBrief]:
    """按名称匹配产品。用户和模型常说简称（「达尔文12号」对「达尔文12号重大疾病保险」），
    所以先精确匹配，匹配不上再退化成「简称是否被全名包含」。"""
    target = _normalize_name(product_name)
    exact = [p for p in products if _normalize_name(p.name) == target]
    if exact:
        return exact
    return [p for p in products if target in _normalize_name(p.name)]


@tool
async def retrieve_insurance_clause(
    query: str,
    k: int,
    product_name: str,
) -> list[str]:
    """查询指定保险产品的条款内容。

    Args:
        query: 需要从保险条款中查询的问题。
        k: 返回的文档的数量
        product_name: 保险产品名称，填用户在售产品的完整名或简称，例如「达尔文12号」。
    """
    products = await _fetch_products()
    matched = _match_products(product_name, products)

    # 名称对不上就直接把在售清单给模型看，让它改用正确名字重查，而不是拿错产品的条款作答。
    if not matched:
        available = "、".join(p.name for p in products)
        return [f"没有找到名称包含「{product_name}」的产品。在售产品有：{available}"]
    if len(matched) > 1:
        candidates = "、".join(p.name for p in matched)
        return [f"「{product_name}」匹配到多个产品：{candidates}，请用更完整的名称再查一次"]

    product = matched[0]

    # 1.导入Retriever
    from app.rag import retriever

    # 2.检索
    chunks: list[ParentChunk] = await retriever.retrieve(query, k, product.id)

    # 3.把查询到的ParentChunk变成字符串，带上产品名方便模型核对答的是哪个产品
    return [
        f"product_name: {product.name}\nsection_path: {chunk.section_path}\n content:{chunk.content}"
        for chunk in chunks
    ]


@tool
async def save_insurance_plan(
    plan: InsurancePlanCreate,
    runtime: ToolRuntime[InsuranceAgentContext],
) -> dict[str, str]:
    """
    保存当前用户的保险推荐方案。
    当你查询完候选产品后，根据用户画像、候选产品生成保险组合方案，保存到数据库
    """

    # 保存前重新核对在售目录，并使用商品表中的名称、分类和起售价。
    products = {product.id: product for product in await _fetch_products()}
    if any(item.product_id not in products for item in plan.items):
        return {"message": "方案未保存：包含不存在或已下架的产品，请重新查询候选产品。"}
    plan = plan.model_copy(update={"items": [
        item.model_copy(update={
            "product_name": products[item.product_id].name,
            "category": products[item.product_id].category,
            "annual_premium_budget": products[item.product_id].min_premium,
        })
        for item in plan.items
    ]})

    async with AsyncSessionFactory() as session:
        service = InsurancePlanService(session)
        plan_id = await service.create_plan(
            user_id=runtime.context.user_id,
            data=plan,
        )

    return {
        "plan_id": str(plan_id),
        "message": "保险方案保存成功",
    }
