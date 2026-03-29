"""
Amen Bank — FastAPI Chatbot Service
Handles banking FAQ, balance queries, recent transactions, credit advice.
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import chat, health, analytics
from app.config import settings
from app.database import init_db


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(
    title="Amen Bank Chatbot API",
    description="Intelligent banking assistant with session memory",
    version="1.0.0",
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url=None,
    lifespan=lifespan,
)

# ─── Middleware ────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "X-Session-ID"],
)

# ─── Routers ──────────────────────────────────────────────────────────
app.include_router(health.router,    prefix="/health",    tags=["Health"])
app.include_router(chat.router,      prefix="/chat",      tags=["Chat"])
app.include_router(analytics.router, prefix="/analytics", tags=["Analytics"])
