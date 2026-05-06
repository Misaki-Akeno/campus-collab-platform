from app.tools.activity_tool import ActivityTool
from app.tools.club_tool import ClubTool
from app.tools.user_tool import UserTool

_TOOLS = [ActivityTool(), ClubTool(), UserTool()]

# Claude API 所需的 tool schema 列表
TOOL_SCHEMAS = [
    {
        "name": t.name,
        "description": t.description,
        "input_schema": t.input_schema,
    }
    for t in _TOOLS
]

_REGISTRY = {t.name: t for t in _TOOLS}


async def run_tool(name: str, input: dict, user_token: str | None) -> str:
    tool = _REGISTRY.get(name)
    if not tool:
        return f"[未知工具: {name}]"
    return await tool.run(input, user_token)
