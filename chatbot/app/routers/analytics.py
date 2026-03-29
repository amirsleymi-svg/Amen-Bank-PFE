from fastapi import APIRouter
from app.database import get_redis
from app.services.chatbot import chatbot_service

router = APIRouter()

@router.get("")
async def get_analytics():
    redis = get_redis()
    if redis is None:
        return chatbot_service.get_memory_analytics()

    total = await redis.get("analytics:total_messages") or 0

    # Top intents
    intents = {}
    for key in await redis.keys("analytics:intent:*"):
        intent = key.split(":")[-1]
        intents[intent] = int(await redis.get(key) or 0)

    # Top topics
    topics = {}
    for key in await redis.keys("analytics:topic:*"):
        topic = key.split(":")[-1]
        topics[topic] = int(await redis.get(key) or 0)

    return {
        "total_messages": int(total),
        "top_intents": sorted(intents.items(), key=lambda x: x[1], reverse=True)[:10],
        "top_topics":  sorted(topics.items(),  key=lambda x: x[1], reverse=True)[:10],
    }
