from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import Field, field_validator, BaseModel


class ChatThreadCreate(BaseModel):
    """创建会话请求"""

    title: str = Field(default="新会话", min_length=1, max_length=200)

    @field_validator("title", mode="before")
    @classmethod
    def normalize_title(cls, value):
        # 非字符串交给 Pydantic 返回校验错误，避免 .strip() 导致 HTTP 500。
        return value.strip() if isinstance(value, str) else value


class ChatThreadResponse(BaseModel):
    """会话响应"""

    id: UUID
    title: str
    created_at: datetime
    updated_at: datetime


class ChatMessageResponse(BaseModel):
    """历史消息"""

    role: Literal["user", "assistant"]
    content: str


class ChatHistoryResponse(BaseModel):
    """会话历史响应"""

    thread_id: UUID
    messages: list[ChatMessageResponse]
