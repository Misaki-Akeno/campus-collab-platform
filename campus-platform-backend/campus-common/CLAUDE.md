# campus-common 开发指南

## 职责

全局公共基础层，被所有业务服务依赖。**不依赖任何业务服务**，修改需注意向后兼容性。

## 模块内容

| 包路径 | 内容 |
|--------|------|
| `common.result.Result<T>` | 统一响应包装，静态工厂 `ok(data)` / `fail(code, msg)` |
| `common.exception.BizException` | 业务异常（携带 ErrorCode），由 GlobalExceptionHandler 捕获 |
| `common.exception.ErrorCode` | 错误码枚举（码段分配见下表） |
| `common.exception.GlobalExceptionHandler` | `@RestControllerAdvice`，捕获 BizException + MethodArgumentNotValidException |
| `common.util.JwtUtil` | Spring `@Component`，JWT 生成/解析（HMAC-SHA256） |
| `common.util.SnowflakeIdUtil` | 雪花算法 ID 生成器 |
| `common.util.PageUtil` | 分页参数工具 |
| `common.model.BaseEntity` | 公共字段基类（id/create_time/update_time/is_deleted），配合 MetaObjectHandler |
| `common.model.PageRequest` | 分页请求 DTO |

## Result<T> 使用规范

- Controller 层**必须**返回 `Result.ok(data)` 或 `Result.fail(code, msg)`
- 禁止直接返回裸 POJO 或 void（通过 `@RestControllerAdvice` 统一处理）
- `traceId` 字段由 SkyWalking Agent 自动注入

## 错误码码段分配

| 码段 | 所属服务 |
|------|---------|
| `200` | 成功（全局） |
| `400-499` | 客户端错误（全局） |
| `500` | 系统内部错误 |
| `5001-5010` | seckill-service |
| `5011-5020` | file-service |
| `5021-5030` | im-service |
| `5031-5040` | club-service |

## JWT 配置

密钥通过 Nacos 配置中心 `campus-common.yml` 管理，本地开发见 `application.yml` 的 `campus.jwt.*` 配置项（由 `JwtProperties` 类绑定）。
