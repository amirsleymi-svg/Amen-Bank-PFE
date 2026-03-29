import redis.asyncio as aioredis
import logging

from app.config import settings

_redis: aioredis.Redis | None = None
logger = logging.getLogger(__name__)


async def init_db():
    global _redis
    client = aioredis.Redis(
        host=settings.REDIS_HOST,
        port=settings.REDIS_PORT,
        password=settings.REDIS_PASSWORD or None,
        decode_responses=True,
    )
    try:
        await client.ping()
        _redis = client
    except Exception as exc:
        # Do not crash chatbot startup if Redis is unavailable.
        logger.warning("Redis unavailable, chatbot will use in-memory fallback: %s", exc)
        _redis = None


def get_redis() -> aioredis.Redis | None:
    return _redis
