from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional
from app.schemas.chat import ChatMessage, ChatResponse
from app.services.chatbot import chatbot_service
from app.config import settings
import time

router = APIRouter()
security = HTTPBearer(auto_error=False)

# Simple in-memory rate limiter
_rate_store: dict = {}


def check_rate_limit(session_id: str, limit: int = 20):
    now = time.time()
    window_start = now - 60
    calls = [t for t in _rate_store.get(session_id, []) if t > window_start]
    if len(calls) >= limit:
        raise HTTPException(status_code=429, detail="Trop de requêtes. Réessayez dans une minute.")
    calls.append(now)
    _rate_store[session_id] = calls


@router.post("", response_model=ChatResponse)
async def chat(
    payload: ChatMessage,
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security),
):
    check_rate_limit(payload.session_id, settings.RATE_LIMIT)
    token = credentials.credentials if credentials else None
    result = await chatbot_service.process(
        session_id=payload.session_id,
        message=payload.message,
        user_id=payload.user_id,
        token=token,
    )
    return result


@router.get("/session/{session_id}")
async def get_session(session_id: str):
    history = await chatbot_service.get_session(session_id)
    return {"session_id": session_id, "messages": history, "message_count": len(history)}


@router.delete("/session/{session_id}")
async def clear_session(session_id: str):
    await chatbot_service.clear_session(session_id)
    return {"message": "Session cleared"}
