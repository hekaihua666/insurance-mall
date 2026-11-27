"""本机联调：验证 Java 产品接口、Python 工具和真实 SSE 对话。"""
import argparse
import asyncio
import sys
import time
from getpass import getpass
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "agent-service"))
from app.agents.tools import query_candidate_products
from app.infra.database import close_database


async def main(products_only: bool):
    async with httpx.AsyncClient(timeout=120) as client:
        # 前端开发服务将请求转发给 Java，检查在售目录和顾问候选查询。
        products = (await client.get("http://127.0.0.1:5173/api/v1/products")).raise_for_status().json()
        assert len(products) > 0
        try:
            candidates = await query_candidate_products.ainvoke({"categories": ["medical"], "limit_per_category": 2})
        finally:
            await close_database()
        assert len(candidates) == 2 and all(p["category"] == "medical" for p in candidates)
        print(f"前端→Java 产品 {len(products)} 条，Python 工具→PostgreSQL 候选产品 {len(candidates)} 条")
        if products_only:
            return

        # 使用测试账号登录，从正常入口取得 JWT。
        login = await client.post("http://127.0.0.1:5173/api/v1/auth/login", json={
            "username": input("测试账号用户名：").strip(),
            "password": getpass("测试账号密码："),
        })
        token = login.raise_for_status().json()["data"]["token"]
        headers = {"Authorization": f"Bearer {token}"}
        response = await client.post("http://127.0.0.1:5173/api/v1/chat-threads", headers=headers, json={"title": "拆分联调"})
        response.raise_for_status()
        thread = response.json()
        thread_id = str(thread["id"])
        started = time.monotonic()
        events = []
        first_chunk_time = None
        async with client.stream("POST", "http://127.0.0.1:5173/api/v1/chat", headers=headers,
                                 json={"thread_id": thread_id, "message": "你好，请用一句话介绍你自己"}) as stream:
            stream.raise_for_status()
            async for line in stream.aiter_lines():
                if line.startswith("event:"):
                    events.append(line)
                if line.startswith("data:") and line[5:].strip() and first_chunk_time is None:
                    first_chunk_time = time.monotonic() - started
        # 在隔离数据库中删除测试会话。
        deleted = await client.delete(f"http://127.0.0.1:5173/api/v1/chat-threads/{thread_id}", headers=headers)
        assert deleted.status_code == 204, deleted.status_code
        assert "event: message" in events and "event: done" in events, events
        print(f"真实模型 SSE 首段到达：{first_chunk_time:.1f} 秒，完整事件数：{len(events)}")
        print("会话创建、真实模型流式返回和删除：通过")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--products-only", action="store_true", help="仅验证产品查询，跳过登录和模型对话")
    asyncio.run(main(parser.parse_args().products_only))
