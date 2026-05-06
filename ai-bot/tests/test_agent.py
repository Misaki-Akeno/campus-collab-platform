import pytest
from unittest.mock import AsyncMock, MagicMock, patch
import anthropic


@pytest.mark.asyncio
async def test_chat_end_turn_no_tools():
    """Agent 直接 end_turn，无需调用工具"""
    mock_resp = MagicMock()
    mock_resp.stop_reason = "end_turn"
    mock_resp.content = [MagicMock(type="text", text="你好，我是校园助手！")]

    with patch("app.agent.campus_agent._client") as mock_client:
        mock_client.messages.create = AsyncMock(return_value=mock_resp)
        from app.agent.campus_agent import chat
        result = await chat("你好")

    assert result == "你好，我是校园助手！"


@pytest.mark.asyncio
async def test_chat_tool_use_then_end_turn():
    """Agent 先调用 query_activity 工具，再 end_turn 给出回答"""
    tool_block = MagicMock()
    tool_block.type = "tool_use"
    tool_block.id = "toolu_01"
    tool_block.name = "query_activity"
    tool_block.input = {}

    first_resp = MagicMock()
    first_resp.stop_reason = "tool_use"
    first_resp.content = [tool_block]

    second_resp = MagicMock()
    second_resp.stop_reason = "end_turn"
    second_resp.content = [MagicMock(type="text", text="当前有以下活动：音乐节")]

    tool_result_str = "- 【音乐节】地点：大礼堂，活动时间：2026-04-20T19:00:00，剩余名额：123/500"

    with patch("app.agent.campus_agent._client") as mock_client, \
         patch("app.agent.campus_agent.run_tool", AsyncMock(return_value=tool_result_str)):
        mock_client.messages.create = AsyncMock(side_effect=[first_resp, second_resp])
        from app.agent.campus_agent import chat
        result = await chat("有什么活动可以报名？")

    assert "音乐节" in result


@pytest.mark.asyncio
async def test_chat_max_turns_exceeded():
    """超过 MAX_TURNS 返回兜底回复"""
    tool_block = MagicMock()
    tool_block.type = "tool_use"
    tool_block.id = "toolu_loop"
    tool_block.name = "query_activity"
    tool_block.input = {}

    loop_resp = MagicMock()
    loop_resp.stop_reason = "tool_use"
    loop_resp.content = [tool_block]

    with patch("app.agent.campus_agent._client") as mock_client, \
         patch("app.agent.campus_agent.run_tool", AsyncMock(return_value="some result")), \
         patch("app.agent.campus_agent.MAX_TURNS", 2):
        mock_client.messages.create = AsyncMock(return_value=loop_resp)
        from app.agent.campus_agent import chat
        result = await chat("一直调工具")

    assert result == "抱歉，我暂时无法完成此请求。"


@pytest.mark.asyncio
async def test_activity_tool_run_success():
    """ActivityTool 正常解析 Gateway 响应"""
    mock_data = {
        "data": {
            "records": [
                {
                    "title": "音乐节",
                    "location": "大礼堂",
                    "activityTime": "2026-04-20T19:00:00",
                    "availableStock": 123,
                    "totalStock": 500,
                    "endTime": "2026-04-18T23:59:59",
                }
            ]
        }
    }
    with patch("app.tools.activity_tool.get", AsyncMock(return_value=mock_data)):
        from app.tools.activity_tool import ActivityTool
        tool = ActivityTool()
        result = await tool.run({})

    assert "音乐节" in result
    assert "大礼堂" in result
    assert "123/500" in result


@pytest.mark.asyncio
async def test_activity_tool_run_failure():
    """ActivityTool 调用失败返回错误提示字符串，不抛异常"""
    with patch("app.tools.activity_tool.get", AsyncMock(side_effect=Exception("connection refused"))):
        from app.tools.activity_tool import ActivityTool
        tool = ActivityTool()
        result = await tool.run({})

    assert result.startswith("[工具调用失败:")


@pytest.mark.asyncio
async def test_user_tool_no_token():
    """UserTool 未提供 token 时给出提示"""
    from app.tools.user_tool import UserTool
    tool = UserTool()
    result = await tool.run({}, user_token=None)
    assert "未登录" in result
