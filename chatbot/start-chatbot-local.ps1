$env:DEBUG='false'
$env:BACKEND_URL='http://localhost:8080/api/v1'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_PASSWORD=''
$env:ALLOWED_ORIGINS='["http://localhost:4200"]'
& '.\.venv312\Scripts\python.exe' -m uvicorn app.main:app --host 0.0.0.0 --port 8000
