from pydantic import BaseModel, Field, field_validator
from typing import Optional
import re


class ChatMessage(BaseModel):
    session_id: str = Field(..., min_length=8, max_length=64)
    message: str = Field(..., min_length=1, max_length=1000)
    user_id: Optional[int] = None

    @field_validator("message")
    @classmethod
    def sanitize(cls, v: str) -> str:
        # Strip HTML/script tags
        clean = re.sub(r"<[^>]+>", "", v)
        return clean.strip()


class ChatResponse(BaseModel):
    session_id: str
    message: str
    topic: Optional[str] = None
    suggested_actions: list[str] = Field(default_factory=list)
    timestamp: str


class SessionHistory(BaseModel):
    session_id: str
    messages: list[dict]
    message_count: int
