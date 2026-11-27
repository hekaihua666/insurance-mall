"""包入口，只做一件事：清掉系统代理变量。

Windows 上系统代理可能带 [::1] 这类 IPv6 字面量，httpx 解析时报 Invalid port。
本服务只访问本机资源（PG/Milvus/Ollama）和 DeepSeek 官方接口，不需要走系统代理。

为什么必须放在这里：只要 import app 下任何子模块，都会先执行这个文件，
所以它比 app/core/config.py 更早，也比 langchain_ollama、ollama 这些
「在自己 import 时就去构造 httpx client」的第三方库更早。
比如 import app.rag 会先跑本文件，再去执行 app/rag/__init__.py 里的
from langchain_ollama import OllamaEmbeddings，这时代理已经清干净了。
"""

import os

for _proxy_var in ("NO_PROXY", "no_proxy", "HTTP_PROXY", "http_proxy",
                   "HTTPS_PROXY", "https_proxy", "ALL_PROXY", "all_proxy"):
    os.environ.pop(_proxy_var, None)
