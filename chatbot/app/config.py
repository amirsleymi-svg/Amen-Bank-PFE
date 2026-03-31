from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    DEBUG: bool = False
    BACKEND_URL: str = "http://localhost:8080/api/v1"
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = ""
    ALLOWED_ORIGINS: List[str] = ["http://localhost:4200"]
    RATE_LIMIT: int = 20           # requests per minute
    SESSION_TTL: int = 1800        # seconds
    MAX_HISTORY: int = 20          # messages kept in session
    OLLAMA_ENABLED: bool = True
    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_MODEL: str = "llama3.2:3b"
    OLLAMA_TIMEOUT_SECONDS: int = 60

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
