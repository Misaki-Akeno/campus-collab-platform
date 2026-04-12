from fastapi import FastAPI
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("AI Bot service starting...")
    yield
    print("AI Bot service stopping...")

app = FastAPI(
    title="Campus AI Bot",
    description="校园社团协作平台 AI Agent 服务",
    version="1.0.0",
    lifespan=lifespan
)

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.post("/api/v1/chat")
async def chat(request: dict):
    return {"reply": "AI Agent 正在开发中..."}
