import uuid
from fastapi import FastAPI, Request
from contextlib import asynccontextmanager
from pydantic import BaseModel

from app.agent.campus_agent import chat


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("AI Bot service starting...")
    yield
    print("AI Bot service stopping...")


app = FastAPI(
    title="Campus AI Bot",
    description="校园社团协作平台 AI Agent 服务",
    version="1.0.0",
    lifespan=lifespan,
)


class ChatRequest(BaseModel):
    message: str
    conversation_id: str | None = None


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/api/v1/chat")
async def chat_endpoint(body: ChatRequest, request: Request):
    auth_header = request.headers.get("Authorization", "")
    user_token = auth_header.removeprefix("Bearer ").strip() or None

    reply = await chat(body.message, user_token)
    return {
        "reply": reply,
        "conversation_id": body.conversation_id or str(uuid.uuid4()),
    }
