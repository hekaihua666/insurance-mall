r"""只读复现 Milvus 空分支错误，升级前后可用同一探针验证。

从 agent-service 目录执行：
    .venv\Scripts\python.exe scripts/probe_milvus_empty_branch.py --output result.json

不修改集合，不生成 Embedding，也不调用生成或重排模型。
"""

import argparse
import json
import logging
from importlib.metadata import version
from pathlib import Path

from pymilvus import AnnSearchRequest, MilvusClient, RRFRanker


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--uri", default="http://127.0.0.1:19530")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    # 已预期的服务器错误由下方记录；避免 SDK 重复输出大段堆栈。
    logging.getLogger("pymilvus").setLevel(logging.CRITICAL)
    client = MilvusClient(uri=args.uri, timeout=15)
    collection = "insurance_collection"
    try:
        report = {
            "server": client.get_server_version(),
            "pymilvus": version("pymilvus"),
            "indexes": [client.describe_index(collection, name)
                        for name in client.list_indexes(collection)],
            "cases": [],
        }
        # 复用一条已有向量；这里只判断服务器能否处理空结果，不评估召回质量。
        dense = client.query(collection, filter="product_id == 1",
                             output_fields=["dense"], limit=1)[0]["dense"]

        for product_id in range(1, 13):
            expr = f"product_id == {product_id}"
            text = client.query(collection, filter=expr,
                                output_fields=["text"], limit=1)[0]["text"]
            # 同一产品分别验证普通问法、有命中文字、完全无命中文字。
            for label, query in [("waiting", "等待期是多久"),
                                 ("known_text", text[:100]),
                                 ("no_match", "zzqxnotinthecollectionzz")]:
                case = {"product_id": product_id, "case": label}
                sparse = client.search(
                    collection, data=[query], anns_field="sparse", filter=expr,
                    search_params={"metric_type": "BM25"}, limit=5,
                )
                case["sparse_hits"] = len(sparse[0])
                requests = [
                    AnnSearchRequest(data=[dense], anns_field="dense",
                                     param={"metric_type": "L2"}, limit=5, expr=expr),
                    AnnSearchRequest(data=[query], anns_field="sparse",
                                     param={"metric_type": "BM25"}, limit=5, expr=expr),
                ]
                # 允许失败并记录，才能把升级前后的整张对照表保存下来。
                try:
                    hits = client.hybrid_search(
                        collection, requests, ranker=RRFRanker(), limit=3,
                        output_fields=["product_id"],
                    )[0]
                    case["hybrid_hits"] = len(hits)
                    case["returned_products"] = sorted(
                        {hit["entity"]["product_id"] for hit in hits}
                    )
                except Exception as exc:
                    case["hybrid_error"] = str(exc)
                report["cases"].append(case)

        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2),
                               encoding="utf-8")
        failures = sum("hybrid_error" in case for case in report["cases"])
        print(f"Milvus {report['server']}：36 组测试，混合检索异常 {failures} 组。")
        print(f"结果已保存：{args.output.resolve()}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
