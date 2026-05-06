import os
import anthropic
from app.agent.tool_registry import TOOL_SCHEMAS, run_tool

MAX_TURNS = 5
MODEL = os.getenv("CLAUDE_MODEL", "claude-haiku-4-5-20251001")

_client = anthropic.AsyncAnthropic()

SYSTEM_PROMPT = """你是校园社团协作平台的智能助手，负责帮助学生查询社团信息、活动报名情况和个人信息。
回答要简洁友好，使用中文。如果查询到具体数据，请清晰地呈现给用户。"""


async def chat(user_message: str, user_token: str | None = None) -> str:
    messages = [{"role": "user", "content": user_message}]

    for _ in range(MAX_TURNS):
        resp = await _client.messages.create(
            model=MODEL,
            max_tokens=1024,
            system=SYSTEM_PROMPT,
            tools=TOOL_SCHEMAS,
            messages=messages,
        )

        if resp.stop_reason == "end_turn":
            return _extract_text(resp)

        # 执行所有 tool_use 块
        tool_results = []
        for block in resp.content:
            if block.type == "tool_use":
                result = await run_tool(block.name, block.input, user_token)
                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": block.id,
                    "content": result,
                })

        if not tool_results:
            return _extract_text(resp)

        messages.append({"role": "assistant", "content": resp.content})
        messages.append({"role": "user", "content": tool_results})

    return "抱歉，我暂时无法完成此请求。"


def _extract_text(resp: anthropic.types.Message) -> str:
    for block in resp.content:
        if hasattr(block, "text"):
            return block.text
    return ""
