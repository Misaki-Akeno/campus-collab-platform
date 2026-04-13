# campus-api 开发指南

## 职责

服务间通信契约模块，被所有需要跨服务调用的服务依赖。**只包含接口定义和 DTO，无业务实现**。

## 模块内容

| 包 | 内容 |
|----|------|
| `api.user.UserFeignClient` | `@FeignClient("user-service")`，提供用户信息查询 |
| `api.club.ClubFeignClient` | `@FeignClient("club-service")`，提供社团信息查询 |
| `api.file.FileFeignClient` | `@FeignClient("file-service")`，提供文件元数据查询 |
| `api.user.dto.UserBasicDTO` | 跨服务传输的用户精简信息（userId/username/nickname/role） |

## 服务间通信规范

| 配置 | 值 | 说明 |
|------|-----|------|
| Feign 连接超时 | 2s | connect-timeout |
| Feign 读取超时 | 5s | read-timeout |
| 通信方式 | HTTP 同步（Feign） | 用于强一致性查询 |

## 新增 Feign 接口规范

1. 接口路径与目标服务 Controller 路径**完全一致**
2. GET 请求参数使用 `@RequestParam` / `@PathVariable`（Feign 不支持直接传 POJO 对象作 GET 参数）
3. 返回值用 `Result<T>` 包装，调用方需做 null 检查
4. Feign 接口定义在此模块，实现由目标服务提供

## 引用关系

```
campus-api 依赖 campus-common（使用 Result<T>）
所有业务服务 依赖 campus-api（调用其他服务时）
```
