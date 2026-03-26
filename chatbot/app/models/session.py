"""Session model for chatbot conversation persistence."""
from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Optional

@dataclass
class Message:
    role: str          # 'user' | 'assistant'
    content: str
    topic: Optional[str] = None
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat())

@dataclass
class Session:
    session_id: str
    user_id: Optional[int] = None
    messages: List[Message] = field(default_factory=list)
    started_at: str = field(default_factory=lambda: datetime.utcnow().isoformat())
    message_count: int = 0
    resolved: bool = False
