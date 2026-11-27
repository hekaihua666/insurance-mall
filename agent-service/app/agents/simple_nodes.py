"""转人工、兜底两个简单节点。

推荐、闲聊、理赔都是带工具或子图的完整 Agent，
而这两种意图不需要工具，单次调用大模型生成回复即可，
所以集中在这个文件里，共用一个模型实例。
"""

from langchain.chat_models import init_chat_model
from langchain_core.messages import SystemMessage

from app.core.config import settings
from .schemas import InsuranceAgentState

# 两个节点共用一个模型，避免每次请求都重新初始化
_model = init_chat_model(
    settings.llm.chat_model,
    api_key=settings.llm.api_key,
    extra_body={"thinking": {"type": "disabled"}},
)

_HANDOFF_PROMPT = """
你是《安心保》商城的客服，用户提出了投诉或明确要求人工服务，需要转人工。

要求：
1. 先安抚用户情绪，表达理解和歉意。
2. 如实说明目前没有自动转接或登记工单功能，需要用户联系商城客服或保险公司。
3. 不声称已登记、已转接或承诺有人回访；不编造客服电话和联系方式。
4. 语气真诚、专业，不超过三句话。
""".strip()

_FALLBACK_PROMPT = """
你是《安心保》商城的智能客服，用户的话没能被正确理解。

要求：
1. 礼貌说明自己没太理解用户的意思。
2. 请用户换个说法，或明确是咨询产品、投保还是理赔。
3. 语气友好，不超过两句话。
""".strip()


def _reply(state: InsuranceAgentState, prompt: str) -> dict:
    """把系统提示词和当前对话一起发给模型，返回一条 AI 回复"""

    response = _model.invoke([SystemMessage(content=prompt)] + state["messages"])
    return {"messages": [response]}


def human_handoff_node(state: InsuranceAgentState) -> dict:
    """人工服务指引：安抚情绪并说明真实的服务边界。"""

    return _reply(state, _HANDOFF_PROMPT)


def fallback_node(state: InsuranceAgentState) -> dict:
    """兜底：没听懂时引导用户重新表述"""

    return _reply(state, _FALLBACK_PROMPT)
