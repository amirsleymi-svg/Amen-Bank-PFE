# ─── Amen Bank Chatbot — Local Startup Script ───────────────────────────────
# Usage: .\start-chatbot-local.ps1
# Requires: Ollama running on localhost:11434 with llama3.2:3b pulled

$ErrorActionPreference = 'Stop'

# ── Resolve Python from .venv312 (preferred) or system PATH ─────────────────
$VenvPython = Join-Path $PSScriptRoot '.venv312\Scripts\python.exe'
if (Test-Path $VenvPython) {
    $Python = $VenvPython
    Write-Host "[OK] Using venv Python: $Python"
} else {
    $Python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $Python) {
        Write-Error "[ERROR] Python not found. Install Python 3.12+ or recreate .venv312."
        exit 1
    }
    Write-Host "[WARN] .venv312 not found, using system Python: $Python"
}

# ── Check Ollama is reachable ────────────────────────────────────────────────
try {
    $ollamaResp = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 3
    $modelCount = $ollamaResp.models.Count
    if ($modelCount -eq 0) {
        Write-Host "[WARN] Ollama is running but NO models are installed."
        Write-Host "       Run: ollama pull llama3.2:3b"
        Write-Host "       Continuing — chatbot will use FAQ-only mode until a model is available."
    } else {
        Write-Host "[OK] Ollama running with $modelCount model(s): $($ollamaResp.models.name -join ', ')"
    }
} catch {
    Write-Host "[WARN] Ollama not reachable at http://localhost:11434 — LLM fallback disabled."
    Write-Host "       Start Ollama with: ollama serve"
}

# ── Environment ──────────────────────────────────────────────────────────────
$env:DEBUG              = 'true'
$env:BACKEND_URL        = 'http://localhost:8080/api/v1'
$env:REDIS_HOST         = 'localhost'
$env:REDIS_PORT         = '6379'
$env:REDIS_PASSWORD     = ''
$env:ALLOWED_ORIGINS    = '["http://localhost:4200"]'
$env:OLLAMA_ENABLED     = 'true'
$env:OLLAMA_BASE_URL    = 'http://localhost:11434'
$env:OLLAMA_MODEL       = 'llama3.2:3b'
$env:OLLAMA_TIMEOUT_SECONDS = '60'

Write-Host ""
Write-Host "Starting Amen Bank Chatbot on http://0.0.0.0:8000 ..."
Write-Host "  Docs : http://localhost:8000/docs"
Write-Host "  Model: $env:OLLAMA_MODEL @ $env:OLLAMA_BASE_URL"
Write-Host ""

Set-Location $PSScriptRoot
& $Python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
