from app.utils.http_client import get


class ActivityTool:
    name = "query_activity"
    description = "查询校园社团活动信息，包括活动标题、地点、时间、剩余名额、报名截止时间等"
    input_schema = {
        "type": "object",
        "properties": {
            "club_id": {
                "type": "string",
                "description": "社团 ID，可选，不传则查询全部活动",
            },
            "status": {
                "type": "integer",
                "description": "活动状态：1=报名中，2=进行中，3=已结束，不传则查全部",
            },
        },
        "required": [],
    }

    async def run(self, input: dict, user_token: str | None = None) -> str:
        params: dict = {"page": 1, "size": 10}
        if club_id := input.get("club_id"):
            params["clubId"] = club_id
        if status := input.get("status"):
            params["status"] = status
        try:
            data = await get("/seckill/api/v1/activities", params=params)
            records = data.get("data", {}).get("records", [])
            if not records:
                return "暂无符合条件的活动。"
            lines = []
            for r in records:
                lines.append(
                    f"- 【{r.get('title')}】地点：{r.get('location')}，"
                    f"活动时间：{r.get('activityTime')}，"
                    f"剩余名额：{r.get('availableStock')}/{r.get('totalStock')}，"
                    f"报名截止：{r.get('endTime')}"
                )
            return "\n".join(lines)
        except Exception as e:
            return f"[工具调用失败: {e}]"
