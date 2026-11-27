r"""RAG 灌库脚本：把 data/raw/kb 下的条款 PDF 解析后写进 Milvus 和 PostgreSQL。

用法（在 agent-service 目录下执行）：
    .venv\Scripts\python.exe scripts\ingest_rag.py            # 12 个产品全灌
    .venv\Scripts\python.exe scripts\ingest_rag.py --only 2 6 # 只灌指定产品
    .venv\Scripts\python.exe scripts\ingest_rag.py --dry-run  # 只看会灌哪些，不真写

设计要点：
1. MinerU 解析 + 大模型修 Markdown 是最慢最贵的一步，所以按 clause_name 缓存：
   个人版/转保版共用同一份 PDF，只解析一次。
2. 每个产品灌完立刻提交一次（pipeline 内部自己开事务），中途失败不影响已完成的。
3. 脚本结束打印汇总表，哪些成功、多少父块子块一目了然。
4. 代理变量由 app/__init__.py 清掉了，直接跑就行，不需要外面再清一遍。
"""

import argparse
import asyncio
import sys
import time
from pathlib import Path

# 脚本是当文件跑的（python scripts\ingest_rag.py），sys.path[0] 是 scripts 目录，
# 得先把 agent-service 这一层加进去，后面才能 import app。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# 这一行必须排在任何 langchain/ollama/httpx 导入之前：
# 执行 app/__init__.py 会清掉系统代理变量，否则 ollama 在自己 import 时
# 就会去建 httpx client，碰上 Windows 代理里的 [::1] 直接抛 Invalid port。
import app  # noqa: E402, F401

from langchain_milvus import BM25BuiltInFunction, Milvus  # noqa: E402
from langchain_ollama import OllamaEmbeddings  # noqa: E402

from app.core.config import settings  # noqa: E402
from app.core.logging import configure_logging, get_logger  # noqa: E402
from app.infra.database import AsyncSessionFactory  # noqa: E402
from app.modules.product.repository import ProductRepository  # noqa: E402
from app.rag.pipeline import RAGPipeline  # noqa: E402

configure_logging(settings.logging.level)
logger = get_logger("ingest_rag")


def build_vector_store() -> Milvus:
    embeddings = OllamaEmbeddings(
        model="qwen3-embedding:0.6b",
        dimensions=1024,
        base_url=settings.rag.ollama_url,
    )
    return Milvus(
        embedding_function=embeddings,
        collection_name="insurance_collection",
        builtin_function=BM25BuiltInFunction(analyzer_params={"type": "chinese"}),
        vector_field=["dense", "sparse"],
        connection_args={"uri": settings.rag.milvus_url},
        auto_id=True,
    )


async def main() -> int:
    parser = argparse.ArgumentParser(description="条款 PDF 灌进 RAG 知识库")
    parser.add_argument("--only", type=int, nargs="+", metavar="ID",
                        help="只灌这些产品 ID，不传则灌全部")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印将要灌哪些产品，不真正写库")
    args = parser.parse_args()

    # 1. 查出要灌的产品
    async with AsyncSessionFactory() as session:
        products = await ProductRepository(session).query_by_category()
    if args.only:
        wanted = set(args.only)
        products = [p for p in products if p.id in wanted]
        missing = wanted - {p.id for p in products}
        if missing:
            logger.warning(f"这些产品 ID 不存在或未上架，已跳过：{sorted(missing)}")
    if not products:
        logger.error("没有匹配到任何产品，退出")
        return 1

    logger.info(f"准备灌库 {len(products)} 个产品")
    for p in products:
        logger.info(f"  id={p.id} {p.name}  <-- {p.clause_name}")

    # 2. 按条款文件名分组，同一份 PDF 只解析一次
    groups: dict[str, list] = {}
    for p in products:
        groups.setdefault(p.clause_name, []).append(p)
    logger.info(f"共 {len(groups)} 份不同的条款 PDF（{len(products)} 个产品共用）")
    for clause_name, items in groups.items():
        if len(items) > 1:
            logger.info(f"  共用同一份：{[i.id for i in items]} -> {clause_name}")

    if args.dry_run:
        logger.info("--dry-run 模式，到此为止，没有写任何数据")
        return 0

    # 3. 逐份 PDF 解析 + 逐产品入库
    vector_store = build_vector_store()
    pipeline = RAGPipeline(vector_store)
    results: list[tuple[int, str, str, int, int, float]] = []
    started = time.time()

    try:
        for clause_name, items in groups.items():
            logger.info(f"=== 解析条款：{clause_name} ===")
            t0 = time.time()
            try:
                markdown = pipeline._parse_and_optimize(clause_name)  # noqa: SLF001
            except Exception as exc:
                logger.error(f"解析失败 {clause_name}: {type(exc).__name__}: {exc}")
                for p in items:
                    results.append((p.id, p.name, clause_name, 0, 0, time.time() - t0))
                continue
            logger.info(f"  解析完成，Markdown {len(markdown)} 字符，"
                        f"用时 {time.time() - t0:.1f}s")

            for p in items:
                t1 = time.time()
                logger.info(f"  入库 product {p.id} {p.name} ...")
                try:
                    parents, children = await pipeline.ingest_markdown(p, markdown)
                    cost = time.time() - t1
                    results.append((p.id, p.name, clause_name, parents, children, cost))
                    logger.info(f"  完成 product {p.id}：父块 {parents} 子块 {children}，"
                                f"{cost:.1f}s")
                except Exception as exc:
                    logger.error(f"入库失败 product {p.id}: {type(exc).__name__}: {exc}")
                    results.append((p.id, p.name, clause_name, 0, 0, time.time() - t1))
    finally:
        pipeline.close()
        vector_store.client.close()

    # 4. 汇总
    total = time.time() - started
    logger.info("=" * 62)
    logger.info(f"灌库汇总（总耗时 {total:.1f}s）")
    ok = [r for r in results if r[3] > 0]
    fail = [r for r in results if r[3] == 0]
    for pid, name, clause, parents, children, cost in results:
        flag = "✅" if parents > 0 else "❌"
        logger.info(f"  {flag} id={pid:<3} 父块={parents:<4} 子块={children:<5} "
                    f"{cost:5.1f}s  {name}")
    logger.info(f"成功 {len(ok)} 个 / 失败 {len(fail)} 个")
    logger.info("=" * 62)
    return 0 if not fail else 2


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
