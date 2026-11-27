from uuid import UUID

from fastapi.sse import ServerSentEvent
from langchain.messages import HumanMessage
from langchain_core.messages import AIMessage
from langgraph.graph.state import CompiledStateGraph
from sqlalchemy.ext.asyncio import AsyncSession

from app.agents.schemas import InsuranceAgentContext
from app.core.logging import get_logger
from app.modules.chat_thread.repository import ChatThreadRepository
from app.modules.chat_thread.exceptions import ChatThreadNotFound
from .schema import ChatRequest

logger = get_logger(__name__)


class ChatService:

    def __init__(self, session: AsyncSession, agent: CompiledStateGraph):
        self.agent = agent
        self.repository = ChatThreadRepository(session)

    async def require_thread_owner(self, user_id: int, thread_id: UUID):
        chat_thread = await self.repository.required_owner(user_id, thread_id)
        if not chat_thread:
            raise ChatThreadNotFound()

    async def chat_stream(self, user_id: int, request: ChatRequest):
        """为已校验归属的会话生成流式回答。"""
        # 2.组织消息
        _input = {"messages": [HumanMessage(request.message)]}

        # 3.组织config，实现会话记忆
        config = {"configurable": {"thread_id": str(request.thread_id)}}

        # 4.组织Context，把当前登录用户传给Agent里的工具（不暴露给大模型）
        context = InsuranceAgentContext(user_id=user_id)

        # 5.调用Agent
        try:
            stream = await self.agent.astream_events(
                _input, config, context=context, version="v3"
            )
            # 6.返回结果
            async for event in stream:
                if event['method'] == 'messages':
                    data = event['params']['data'][0]
                    if isinstance(data, AIMessage):
                        yield ServerSentEvent(data=data.text, event="message")
                    # 必须先确认是 dict：节点若往 state 里塞 HumanMessage/SystemMessage，
                    # 整条消息也会作为 payload 出现，那种对象没有 .get，直接调会 AttributeError。
                    elif isinstance(data, dict) and (delta := data.get('delta')) and delta.get('text'):
                        yield ServerSentEvent(data=delta['text'], event="message")
        except Exception as exc:
            # SSE 响应头发出后无法改 HTTP 状态，用 error 事件告知前端本轮失败。
            logger.error("agent_stream_failed", error_type=type(exc).__name__)
            yield ServerSentEvent(data="回复生成失败，请稍后重试", event="error")
            return

        yield ServerSentEvent(data="END", event="done")
