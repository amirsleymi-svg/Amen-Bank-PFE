from fastapi import APIRouter
from datetime import datetime

router = APIRouter()

@router.get("")
async def health():
    return {"status": "ok", "service": "amenbank-chatbot", "timestamp": datetime.utcnow().isoformat()}
