from typing import Annotated

from sqlalchemy.ext.asyncio import AsyncSession

from fastapi import APIRouter, Depends, Request, Header
from fastapi.sse import EventSourceResponse
from app.infra.database import get_session
from .schema import ChatRequest
from .service import ChatService

router = APIRouter(prefix="/api/v1/chat", tags=["与AI对话接口"])


def get_chat_service(
    request: Request,
    session: AsyncSession = Depends(get_session)
):
    # 1.创建ChatService
    agent = request.app.state.agent
    return ChatService(session, agent)

async def require_chat_owner(
    request: ChatRequest,
    user_id: Annotated[int, Header(alias="x-user-id")],
    service: ChatService = Depends(get_chat_service),
):
    # 在发送 SSE 响应头之前检查归属，失败时返回原有的会话错误。
    await service.require_thread_owner(user_id, request.thread_id)


@router.post("", response_class=EventSourceResponse, dependencies=[Depends(require_chat_owner)])
async def chat(
    request: ChatRequest,
    user_id: Annotated[int, Header(alias="x-user-id")],
    service: ChatService=Depends(get_chat_service)
):
    # Generator, 用yield不断生成内容，形成动态列表
    async for sse in service.chat_stream(user_id, request):
        yield sse
