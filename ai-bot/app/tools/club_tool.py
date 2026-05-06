from app.utils.http_client import get


class ClubTool:
    name = "query_club"
    description = "查询校园社团信息，包括社团名称、分类、简介、成员人数等"
    input_schema = {
        "type": "object",
        "properties": {
            "category": {
                "type": "string",
                "description": "社团分类，如 学术、文艺、体育，不传则查询全部",
            },
            "keyword": {
                "type": "string",
                "description": "按社团名称关键字搜索，可选",
            },
        },
        "required": [],
    }

    async def run(self, input: dict, user_token: str | None = None) -> str:
        params: dict = {"page": 1, "size": 10}
        if category := input.get("category"):
            params["category"] = category
        if keyword := input.get("keyword"):
            params["keyword"] = keyword
        try:
            data = await get("/club/api/v1/clubs", params=params)
            records = data.get("data", {}).get("records", [])
            if not records:
                return "暂无符合条件的社团。"
            lines = []
            for r in records:
                lines.append(
                    f"- 【{r.get('name')}】分类：{r.get('category')}，"
                    f"成员：{r.get('memberCount')} 人，"
                    f"简介：{r.get('description') or '暂无'}"
                )
            return "\n".join(lines)
        except Exception as e:
            return f"[工具调用失败: {e}]"
