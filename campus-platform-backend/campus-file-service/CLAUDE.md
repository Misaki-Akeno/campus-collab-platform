# campus-file-service 开发指南

## 职责

大文件管理（端口 **8084**）：分片上传初始化、断点续传、MD5 秒传校验、OSS 分片合并回调。

## 技术栈

Spring WebMVC + JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`）
Redis + MinIO / 阿里云 OSS + MySQL + MyBatis-Plus

## 三种上传入口（由 `/upload/init` 统一判断）

| 场景 | 判断条件 | 处理 |
|------|---------|------|
| **秒传** | `file_meta` 存在且 `upload_status=1` | 直接返回 `fileUrl` |
| **断点续传** | `file_meta` 存在但 `upload_status=0` | 查 Redis 返回未完成分片列表 |
| **全新上传** | `file_meta` 不存在 | 创建 OSS Multipart Upload，返回所有分片预签名 URL |

## 核心 Redis Key

`file:chunk:{uploadId}`：Hash 结构，`field=partNumber value=ETag`，TTL 24h

## 数据库表（完整 DDL 见白皮书 §3.2-D）

| 表 | 说明 |
|----|------|
| `file_meta` | `file_id=MD5`（主键，天然秒传去重），`upload_status` 0-上传中/1-已完成 |
| `file_reference` | 解耦文件与业务（`biz_type`: IM_MSG/ANNOUNCEMENT/CLUB_LOGO/USER_AVATAR） |

## 上传限制

| 配置 | 值 |
|------|-----|
| 单文件最大 | 2 GB |
| 分片大小 | 5 MB（弱网建议 2 MB） |
| 客户端并发分片数 | 3 |
| 预签名 URL 有效期 | 1 小时 |
| 分片记录 TTL | 24 小时 |

## 核心类

| 类 | 职责 |
|----|------|
| `UploadController` | 分片上传全流程 3 个接口 |
| `UploadService` | 秒传/断点续传/全新上传逻辑 |
| `OssService` | MinIO/OSS SDK 封装（Multipart Upload / 预签名 URL） |
| `OssConfig` | OSS 连接配置（从 Nacos 读取） |

## 接口（详见 [docs/API.md §5](../../docs/API.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/file/api/v1/upload/init` | 初始化（含秒传判断） |
| POST | `/file/api/v1/upload/chunk/complete` | 上报分片完成 |
| POST | `/file/api/v1/upload/merge` | 触发 OSS 合并 |

## 错误码段

`5011-5020`，定义在 `campus-common` 的 `ErrorCode` 枚举。
