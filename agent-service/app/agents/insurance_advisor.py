from langchain.agents import create_agent
from langchain.chat_models import init_chat_model
from langgraph.graph.state import CompiledStateGraph

from app.agents.schemas import InsuranceAgentContext
from app.core.config import settings
from app.core.logging import get_logger
from .tools import query_candidate_products, retrieve_insurance_clause, save_insurance_plan

logger = get_logger(__name__)

SYSTEM_PROMPT = """
你是“安心保”的智能保险顾问。
你需要使用专业、准确、容易理解的语言回答用户的保险问题。
当信息不足时，应先向用户追问，不要编造保险产品或保障内容。
当用户询问保险条款问题时，必须调用工具查询条款，然后根据条款回答，不能自己编造。
查询条款时按产品名称调用 retrieve_insurance_clause，不要自己编产品 ID；工具返回的在售产品清单里有正确的完整名称。
检索为空时，明确说明当前知识库未覆盖该产品或问题，不将通用知识当成该产品的条款结论。
给用户推荐保险方案时，必须先查询候选产品，再根据用户需求给出推荐。
当用户明确要求保存，或同意保存上一轮的推荐方案时，调用 save_insurance_plan。
保存时沿用已确认的推荐内容和工具返回的产品 ID，不编造产品；只有工具确认保存成功后，才告知用户可在「我的方案」查看。

对于理赔相关问题（理赔责任、理赔材料、报案流程、理赔时效、多久到账等）：
- 通用流程和常识问题，直接用自己的知识回答。
- 涉及具体产品条款细节（赔付比例、免赔额、等待期、报销范围）时，必须先调用 retrieve_insurance_clause 查条款再回答，不能编造。
- 本商城暂不提供在线报案、提交理赔申请、查询理赔进度的功能；用户问到这些时，如实说明需要联系人工客服或保险公司办理。
"""


# agent在整个系统中，只初始化一次，
async def init_insurance_advisor() -> CompiledStateGraph:
    # 1.初始化模型
    model = init_chat_model(
        model=settings.llm.chat_model,
        api_key=settings.llm.api_key,
        extra_body={"thinking": {"type": "disabled"}}
    )

    # 2.创建Agent，绑定model、tools、Checkpointer、Context
    agent = create_agent(
        model=model,
        tools=[query_candidate_products, retrieve_insurance_clause, save_insurance_plan],
        checkpointer=True,
        # 告诉Agent本次运行会接收哪种Context，工具里才能取到 runtime.context.user_id
        context_schema=InsuranceAgentContext,
        system_prompt=SYSTEM_PROMPT
    )

    logger.info("保险顾问Agent初始化完毕~✅️")
    return agent
