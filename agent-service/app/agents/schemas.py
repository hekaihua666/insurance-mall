from typing import Literal

from langgraph.graph import MessagesState
from pydantic import BaseModel, Field

# 路由意图常量
Intent = Literal[
    "chitchat",
    "recommendation_plan",
    "human_handoff",
    "fallback",
]


class RouteResult(BaseModel):
    """意图识别结果"""

    intent: Intent = Field(description="本轮消息应该进入的工作流")
    reason: str = Field(description="判断该意图的理由")


from dataclasses import dataclass

# 先看它解决的问题
# Agent 的工具（如 save_insurance_plan）需要知道「当前是哪个用户」，但 user_id 不能塞给大模型。
@dataclass(frozen=True)
class InsuranceAgentContext:
    """保险顾问Agent运行时上下文"""

    user_id: int

class InsuranceAgentState(MessagesState):
    """保险顾问主Graph状态"""

    previous_workflow: Intent
    active_workflow: Intent