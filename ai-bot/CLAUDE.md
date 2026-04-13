# ai-bot 开发指南

## 技术栈

Python + FastAPI，通过 HTTP 调用后端 Gateway（端口 9000）访问各业务服务。
依赖：`anthropic`、`langchain-core`、`redis`、`aiomysql`、`aiokafka`（见 `requirements.txt`）

## 架构

```
用户 IM 消息 → Agent Router（意图识别）
                 ├── RAG Pipeline → VectorDB（Milvus/Chroma）→ LLM 生成回答
                 └── Tool Executor → 调用后端 API → LLM 整合结果
```

## AI Agent 能力

| 能力 | 实现方式 | 示例问题 |
|------|---------|---------|
| 智能问答 | RAG（文档嵌入 → 向量检索 → LLM 生成） | "编程社下周有什么活动？" |
| 活动推荐 | 用户标签 + 活动标签匹配 | "推荐我感兴趣的活动" |
| 自动摘要 | 消息聚合 → LLM 摘要 | "总结今天群里讨论的内容" |
| 信息查询 | Function Calling / Tool Use | "音乐节还剩多少名额？" |
| 通知提醒 | 定时任务 + AI 生成提醒文案 | 自动推送活动提醒 |

## Tool 定义规范

每个 Tool 三件套：`name`、`description`、`async run(input)`。  
参数类型使用 `Pydantic BaseModel`，参考 `app/tools/activity_tool.py`。

```python
class ActivityTool:
    name = "query_activity"
    description = "查询校园社团活动信息，包括名称、时间、地点、剩余名额"

    async def run(self, input: QueryActivityInput) -> str:
        # 调用 GET /seckill/api/v1/activities
        ...
```

## 工具调用路由（通过 Gateway 9000）

| 工具 | 后端接口 | 鉴权 |
|------|---------|------|
| `query_activity` | `GET /seckill/api/v1/activities` | 无需（公开接口） |
| `query_club` | `GET /club/api/v1/clubs` | 无需（公开接口） |
| `query_user_info` | `GET /user/api/v1/me` | 需服务账号 Token |

## 关键目录

| 路径 | 说明 |
|------|------|
| `app/main.py` | FastAPI 入口 |
| `app/agent/campus_agent.py` | 校园助手 Agent 主逻辑 |
| `app/agent/tool_registry.py` | Tool 注册与调用分发 |
| `app/rag/` | RAG 流水线（嵌入 / 检索 / 向量存储） |
| `app/tools/` | 各业务工具实现 |

## 接口与架构参考

- 后端 API 文档：[docs/API.md](../docs/API.md)
- 整体架构：[ARCHITECTURE.md](../ARCHITECTURE.md)（§10 AI Agent 赋能层）
