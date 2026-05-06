from app.utils.http_client import get


class UserTool:
    name = "query_user_info"
    description = "查询当前登录用户的个人信息，包括昵称、邮箱、已加入的社团列表（需要用户已登录）"
    input_schema = {
        "type": "object",
        "properties": {},
        "required": [],
    }

    async def run(self, input: dict, user_token: str | None = None) -> str:
        if not user_token:
            return "用户未登录，无法获取个人信息。"
        try:
            data = await get("/user/api/v1/me", token=user_token)
            u = data.get("data", {})
            clubs = u.get("clubs", [])
            club_str = "、".join(c["clubName"] for c in clubs) if clubs else "暂无"
            return (
                f"用户昵称：{u.get('nickname')}，"
                f"邮箱：{u.get('email')}，"
                f"已加入社团：{club_str}"
            )
        except Exception as e:
            return f"[工具调用失败: {e}]"
