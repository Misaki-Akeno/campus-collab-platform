# Campus Collab 可执行任务计划

> 更新日期：2026-09-05。静态审计基线：`main@ffe5f0b`。当前阶段：**P0 运行基线与变更协调**。
> 本文件是实施入口。`静态确认`只表示源码中能定位到问题，不表示已经复现或修复；只有附上运行证据后才能标记完成。
> 详细方案见 [Improvement Plan 1](docs/improvement-plans/improvement-plan-1.md)，后续优化门槛见 [优化与演进](docs/优化与演进.md)。

## 本轮边界与执行顺序

本轮交付分为六个可独立验收的垂直工作包。每个工作包都同时包含代码、迁移、调用方、测试和当前行为文档，不再把迁移与前端适配留到最后集中处理。

```text
P0 可运行基线
 ├─ P1 入口身份、ID 与连接替换
 ├─ P2 报名正确性
 ├─ P3 IM 保存与补拉
 └─ P4 文件上传与授权
        ↓
P5 最小业务闭环（依赖 P1–P4）
        ↓
P6 总体验收与文档收口
```

性能调优、多实例/中间件高可用和 AI 增强不属于 Improvement Plan 1。它们分别在存在可重复容量基线、单实例正确性证据和明确部署需求后另立方案，避免在当前约 6,400 行 Java 的项目中一次引入全部分布式机制。

## 完成状态规则

- `静态确认`：已定位当前源码行为，运行复现仍待执行。
- `进行中`：已有实现改动，但迁移、调用方、测试或文档至少一项未闭合。
- `已完成`：合入后的源码位置、迁移方式、验证命令和结果均已记录。
- 修复后从“当前缺陷”表移除误导性现状描述，在执行记录保留差异 ID 与证据；不得只改文档来接受丢消息、丢名额或越权行为。
- 并行 Agent 不同时改同一业务源码；开始前与结束前都检查 `git status --short`，保留用户和其他 Agent 的未提交改动。

## P0：基线、环境与差异清单

### P0.1 运行基线

| 项目 | 当前已知事实 | 必须补充的证据 | 状态 |
|---|---|---|---|
| Git | 分支 `main`，审计起点 `ffe5f0b`；开始时已有文档未提交改动 | 修复结束时的 commit/工作区定位及改动归属 | 进行中 |
| 后端 | JDK 21.0.6；用户级 Maven 3.9.16；仓库没有 Wrapper。`mvn -B -ntp test` 9/9 reactor 成功，152 tests、0 failures/errors/skipped（1:08）；`mvn -B -ntp package -DskipTests` 9/9 成功（1:46） | 证据见 `test-results/baseline-2026-09-05.md`；仍需 Wrapper 或明确安装步骤保证干净机复现 | 测试与打包通过 |
| 前端 | Node 22.14.0 / pnpm 11.19.0；`pnpm install --frozen-lockfile --ignore-scripts`、`pnpm exec tsc --noEmit`、`pnpm run build` 通过 | `pnpm run lint` 失败：Next 16.2.5 不再支持 `next lint`，需改 lint 脚本后复测 | 构建通过，lint 待修 |
| AI / Python | Python 3.10 隔离 venv 中 pytest 6/6 通过 | Python 3.12 下 `pydantic==2.6` 与 `langchain-core>=0.2` 解析冲突；本机 SOCKS 代理环境还需补 `socksio` 兼容 | 3.10 通过；3.12 待处理 |
| 基础设施 | Docker CLI 29.3.1 / Compose 5.1.1，Compose 静态校验通过；Docker Desktop 4.68 引擎因无法删除 stale `Docker\\run\\dockerInference` socket 而崩溃 | 修复 Desktop 引擎后记录健康检查、镜像和端口；当前不能做真实 MySQL/Redis/Kafka/MinIO 验收 | 阻塞，不等于通过 |
| API 冒烟 | `tests/bruno` 有 15 个请求文件；本次因 Docker 引擎不可用跳过 | 恢复基础设施后记录当前 Bruno 报告、测试账号与数据准备方式 | 未运行 |
| 单元测试 | 当前 Surefire 共 17 个报告，实际执行 152 tests，0 failures/errors/skipped | 这不包含因 Docker 引擎阻塞而未运行的真实基础设施故障场景 | 通过；集成仍阻塞 |

### P0.2 已定位差异

路径和符号基于 `ffe5f0b`；并行修复后必须复核，不使用会随编辑漂移的行号作为唯一证据。

| 差异 ID | 源码证据 | 当前行为 / 风险 | 本轮处理决定 | 工作包 | 状态 |
|---|---|---|---|---|---|
| D-EDGE-01 | `campus-gateway/.../JwtAuthFilter.validateTokenAndForward` | Builder 写入 JWT 身份头，但当前没有“客户端同名头 + JWT”的运行证据证明下游只收到唯一 claims 值 | 先补伪造头回归；若当前 Builder 已覆盖为唯一值则直接关闭，只有复现失败才改实现 | P1 | 待运行验证，不预判缺陷 |
| D-ID-01 | `campus-common/.../SnowflakeIdUtil` | 工作区已支持 property/env 显式节点 ID 和 0..31 校验；未配置时仍使用哈希 fallback，不能证明多实例不碰撞 | 3 个单测通过；仍需为实际部署显式分配不同值并补双实例样本，自动租约留给高可用方案 | P1 | 进行中：单测通过，部署验证待办 |
| D-WS-01 | `WsServer`、`WsSessionManager`、`ImOnlineRoute` | 工作区已用 nodeId/sessionId route token 和 compare-delete 防止旧连接删除新连接；在线 key 仍无 TTL/续租证据 | IM 模块 34 个单测通过，含旧连接关闭不移除替代连接；真实 WS/Redis 和节点异常恢复未验证 | P1 | 进行中：单测通过，集成待办 |
| D-BOOK-01 | `SeckillServiceImpl.book`、`SeckillActivityMapper.deductAvailableStock` | 工作区已移除报名 Redis/Kafka 成功边界，改为 MySQL 条件扣减与唯一订单同事务 | seckill 模块 25 个单测通过；仍需真实 MySQL 并发、唯一键竞争和事务回滚对账 | P2 | 进行中：单测通过，集成待办 |
| D-BOOK-02 | `SeckillController.book`、活动详情页、`docs/API.md` | 工作区 Controller/前端已改为同步成功，旧 Consumer/Lua/相关依赖已删除；API 的 v1 当前行为和 v2 异步 202 目标仍是旧叙述 | API/白皮书统一为首期同步终态；异步 202 仅保留在 O04 容量候选，不再写成既定 v2 | P2 | 进行中：代码已收口，文档待同步 |
| D-IM-01 | `ChatMsgHandler.handle` | 工作区已改为同步单条 mapper insert 完成后 ACK/推送，不再先发 Kafka；没有显式 `@Transactional`，当前成功边界是单条写入完成 | IM 模块 34 个单测通过，含插入失败不 ACK；真实 DB 提交与 ACK 丢失重试仍待集成验证 | P3 | 进行中：单测通过，集成待办 |
| D-IM-02 | `MessagePersistConsumer.consume` | 工作区已让非重复异常继续抛出；当前发送主路径已不生产该持久化事件 | 明确删除旧 Consumer/topic 或保留用途与重试/DLT 配置，避免形成无人使用的第二写路径 | P3 | 进行中：路径待清理 |
| D-IM-03 | `ImMessage.clientMsgId`、`ChatMsgHandler.findExisting`、`V1.1__im_message_idempotency.sql` | 工作区已持久化 `(sender_id, client_msg_id)` 并返回原 msgId；尚未比较重复请求的 conversation/content，且无 seq | 补冲突校验；seq 与游标由 D-IM-04 完成 | P3 | 进行中：部分实现 |
| D-IM-04 | `ImServiceImpl.syncMessages`、`im_message` 表 | 以 msgId 比较但按 createTime/msgId 排序；无稳定会话 seq，跨页边界可能遗漏或乱序 | 增加会话内 seq 和 `(conversation_id, seq)` 唯一索引，接口使用显式 cursor/limit | P3 | 静态确认 |
| D-FILE-01 | `OssServiceImpl.generatePresignedPutUrl` | 工作区已在签名前传入 `uploadId/partNumber`；初始化和 Complete 仍反射调用内部 API | file 模块 23 个单测通过；真实 PUT/ListParts/Complete/GET 仍因 Docker 阻塞未验证 | P4 | 进行中：单测通过，真实对象流待办 |
| D-FILE-02 | `UploadServiceImpl.initUpload`、`completeChunk` | 工作区续传已统一使用 uploadId，校验 owner/参数并重新签发 URL；owner、objectKey、provider uploadId 仍只存 24h Redis | 单测已覆盖局部逻辑；持久任务、Redis 过期和真实存储恢复继续实施 | P4 | 进行中：部分实现，集成待办 |
| D-FILE-03 | `file_meta` 表、`UploadServiceImpl.initUpload/getFileMeta` | 工作区增加同 uploader 秒传/续传限制，但 MD5 仍兼任任务/对象 ID，仍直接返回 URL且没有业务引用授权 | 拆分 upload_task、file_object、file_reference；下载校验引用后返回短期 URL | P4 | 静态确认：核心缺口未修 |
| D-FILE-04 | `UploadServiceImpl.merge` | 工作区增加同 uploader 的已完成快速返回；任务状态仍不持久，Complete 后 DB 失败不能恢复，也无并发合并抢占 | 持久任务、条件更新、ListParts/HeadObject 恢复 | P4 | 进行中：仅基础幂等 |
| D-FLOW-01 | 前端 `Sidebar`、`clubs/page.tsx` 与 `src/app` 目录 | UI 链接 `/clubs/create`，但页面不存在 | P5 补最小创建页并纳入浏览器 E2E | P5 | 静态确认 |
| D-FLOW-02 | `SeckillController`、`seckillApi` | 只有活动列表/详情/报名/订单查询，没有活动创建、发布、取消入口 | P5 增加社团管理者活动管理 API 与最小页面；创建时写 DB，发布时预热/刷新缓存 | P5 | 静态确认 |
| D-FLOW-03 | `ImController` 与前端 `src/app` | 后端仅会话列表/同步，前端没有聊天页面；审批成员不会同步社团主会话 | P5 建立 clubId↔主会话绑定，在入社审批/移除事务后可靠同步成员并补最小聊天页 | P5 | 静态确认 |
| D-FLOW-04 | `UploadController` 与前端 `src/app` | 只有上传 API 骨架，没有文件选择/续传页面，也没有社团业务引用 | P5 在社团内提供一个文件上传/下载入口；撤权后下载必须失败 | P5 | 静态确认 |

## P1：可信入口、ID 配置与连接替换

**范围：** D-EDGE-01、D-ID-01、D-WS-01。可与 P2–P4 并行，但 P5 前必须完成。

- [ ] 补外部同名身份头、GET 白名单、WebSocket token 和黑名单回归；只有证明下游能接收伪造/多值身份时才修改 Header 处理。
- [ ] `SnowflakeIdUtil` 的显式 property/env 与范围校验已有工作区实现；补本地 Compose 的不同值和双实例运行证据。哈希 fallback 仅供本地便利，不作为唯一性保证。
- [ ] WS 本地映射及 Redis route token 的 sessionId 条件删除已有工作区实现；补替换连接测试。若保留“节点异常自动恢复”目标，则还需 TTL/心跳续租。
- [ ] 同步受影响配置、Compose、测试和当前行为文档。

验收证据：伪造身份头仍以 JWT 身份执行；两个配置不同 nodeId 的生成器无重复样本；旧连接被替换并关闭后新连接仍可收发；命令、样本量和日志进入执行记录。

估算：1–2 人日。假设不实现生产级 workerId 自动租约，也不做跨机房时钟治理。

回滚：先回滚应用配置与单实例部署；配置项保持向后兼容。不得回滚到允许外部身份头覆盖的版本。

## P2：报名正确性（单实例首期方案）

**范围：** D-BOOK-01、D-BOOK-02。依赖 P0；可与 P3/P4 并行。

- [ ] MySQL 条件扣减、唯一订单与 SUCCESS 写入同事务已有工作区实现；补并发重复、插入失败回滚与库存对账测试。
- [ ] Controller、前端和旧 Consumer/Lua 已按同步终态收口；仍需修正 API/白皮书中的“当前 Redis/Kafka”和既定 v2 异步 202 叙述，并明确重复报名响应。
- [ ] Redis 库存改为读优化或删除；若保留，提交后刷新/失效，缓存丢失可从 DB 重建。Kafka 只承载通知等可重试副作用，不决定报名成功。
- [ ] 对旧 PROCESSING 订单提供一次性迁移/核对脚本：能从 DB 证明扣减的转 SUCCESS，其余转明确失败；迁移前备份，输出行数对账。
- [ ] 同步 Controller、前端状态处理、Bruno、API 和白皮书“当前实现”说明。

验收证据：并发数大于名额时 `SUCCESS <= total_stock` 且 DB 可用库存一致；同用户并发重试只有一单；事务在扣减后失败时订单和库存同时回滚；跨用户查订单返回 403。

估算：2–4 人日。假设校园规模先由单库承载；异步 202、Redis Stream 和报名 Outbox 只有在 O04 的容量证据达到触发条件后另立方案。

回滚：发布前备份相关表；保留旧状态枚举读取兼容一版。应用回滚前停写并核对订单成功数与库存，禁止用 Redis 数值覆盖 DB。

## P3：IM 成功 ACK、幂等与补拉

**范围：** D-IM-01～04。依赖 P0；P5 的聊天页依赖本包。

- [ ] 版本化迁移增加 `client_msg_id`、会话内 `seq`，唯一约束至少覆盖 `(conversation_id, client_msg_id)` 和 `(conversation_id, seq)`；为现有消息确定可重复的回填顺序并记录碰撞处理。
- [ ] 同步插入后 ACK/推送及持久 clientMsgId 已有工作区实现；补提交失败与 ACK 丢失测试。seq 尚未实现。
- [ ] 同 clientMsgId 同内容重试返回原记录；工作区当前未比较会话/内容，需补冲突校验。旧 Redis 5 分钟占位 ACK 已从主路径移除。
- [ ] 同步接口改为 `conversationId + afterSeq + limit`，稳定升序且返回 `nextCursor/hasMore`；成员权限在每次拉取和发送时验证。
- [ ] 若 Kafka 继续用于通知/跨节点事件，事件必须来源于已提交消息；异常可重试并有 DLT/重放入口，不再承担 ACK 前的唯一持久化。
- [ ] 适配现有客户端协议和文档；浏览器聊天页面在 P5 完成。

验收证据：提交前故障无成功 ACK；ACK 丢失后以相同 clientMsgId 重发得到相同 msgId/seq；超过两页补拉无重复遗漏；非成员发送/拉取为 403；持久化异常测试不会正常确认消费。

估算：4–7 人日。假设首期单 MySQL 主库分配会话 seq；接收设备 ACK、跨节点扇出优化和完整 Outbox 可在多实例方案中增量加入。

回滚：迁移仅新增列/索引，应用先支持旧/新 cursor 双读一版；回滚应用后保留新增列。禁止在确认旧客户端已退场前删除旧字段。

## P4：可恢复 Multipart 与业务授权

**范围：** D-FILE-01～04。依赖 P0；P5 的社团文件入口依赖本包。

- [ ] 新增 `upload_task`、`file_object`、`file_reference` 迁移；uploadId 使用随机任务 ID，不再由 MD5 和 userId 拼接。对象 key 每任务唯一且默认私有。
- [ ] 任务落库后初始化 Multipart 并保存 provider uploadId；初始化失败可按状态重试。Redis 只缓存进度。
- [ ] 工作区已将 uploadId/partNumber 纳入签名并为同 owner 续传重签 URL；需真实对象流验证，其余反射 API 是否替换以验证结果决定。
- [ ] 工作区只有“已完成则返回”的基础幂等；仍需存储端 ListParts 校验、条件抢占和 Complete 后 DB 失败的 HeadObject 恢复。
- [ ] 工作区只阻止跨 uploader 秒传；仍需对象复用与业务引用分离，下载根据 club/member 引用授权后签发短期 URL。
- [ ] 为旧 `file_meta` 提供映射迁移和行数/摘要对账；未能确认业务归属的旧文件不自动公开。

验收证据：真实 MinIO `Create→PUT→ListParts→Complete→GET` 后摘要一致；刷新页面或清空 Redis 只补缺片；跨账号不能上报/合并任务；撤销社团成员后下载失败；重复 complete 同结果；模拟 Complete 后 DB 失败可恢复。

估算：4–7 人日。假设使用单个 MinIO/S3 兼容存储；病毒扫描、配额计费和跨区域复制不在本轮。

回滚：先停止新上传，等待/终止在途任务；应用回滚后保留新表和对象，不做自动删除。旧 `file_meta` 只在迁移验收后停止读取，物理清理另开任务。

## P5：最小可演示业务闭环

**范围：** D-FLOW-01～04。依赖 P1–P4 的契约稳定；这是产品能力工作包，不与底层正确性修复并行发布。

- [ ] 补社团创建页和管理员待审核列表；审核只能由后端管理员角色执行。
- [ ] 增加社团管理者的活动创建、发布、取消 API 和最小页面；不做复杂运营看板。
- [ ] 为每个社团建立一个主会话；成员审批通过后加入，移除/退出后撤权。跨服务失败必须有可重试事件或补偿任务，不用同步 Feign 假装分布式事务。
- [ ] 增加最小聊天页：会话列表、文本发送、ACK 状态、按 seq 补拉。高级搜索、表情、音视频不在本轮。
- [ ] 增加社团文件入口：选择文件、分片进度、刷新续传、授权下载。只实现一种业务引用（社团资料）。
- [ ] 编写两个普通账号加一个管理员的浏览器/API E2E，数据准备不依赖手工改库。

验收流程：用户 A 创建社团 → 管理员审核 → 用户 B 申请 → A 审批 → B 加入主会话 → A 创建并发布活动 → B 报名 → A/B 发送并补拉消息 → A 上传社团文件 → B 下载 → A 移除 B → B 的会话发送、补拉和文件下载均被拒绝。

估算：5–9 人日。假设复用现有 Next.js 组件，只做最小页面与文本消息；若 P1–P4 契约仍在变更则估算失效。

回滚：各入口使用独立 feature flag；先关闭页面/写接口，再停止事件消费者。成员与会话/文件授权保留可重放事件和对账脚本。

## P6：总体验收与文档收口

| 验收项 | 必需证据 | 状态 |
|---|---|---|
| 差异闭合 | D-* 每项有实现位置、迁移、调用方和运行结果；已修复现状不再留在待办 | 待执行 |
| 构建回归 | 后端、前端、AI（若受影响）的版本、命令、通过/失败/跳过数 | 待执行 |
| 数据升级 | 干净建库、旧测试数据升级、行数/摘要对账及应用回滚演练 | 待执行 |
| 核心故障 | P2 事务失败、P3 ACK 重试、P4 Redis 丢失/Complete 中断 | 待执行 |
| 业务闭环 | 三账号 E2E 的请求、页面、数据库/对象存储终态和撤权结果 | 待执行 |
| 文档一致性 | README、API、架构、白皮书、模块说明与真实响应一致 | 待执行 |

P6 通过前不能启动性能优化或多实例扩容。环境阻塞不等于通过，应记录阻塞、可复测命令和所需外部条件。

## 后续方案门槛（本轮不实施）

| 候选方案 | 启动条件 | 本轮只保留的准备 |
|---|---|---|
| Improvement Plan 2：容量与性能 | P6 通过；固定数据、并发和硬件下出现可重复瓶颈 | traceId、核心计时、可重复数据准备 |
| Improvement Plan 3：多实例与高可用 | P6 通过；有双实例部署需求、SLO/RTO/RPO 和维护预算 | 显式 nodeId、sessionId 租约兼容、幂等键 |
| Improvement Plan 4：AI 功能 | 权限模型稳定；有固定问题集、成本预算和产品入口 | 仅保留现有 AI 不回归，不扩功能 |

## 执行与续接记录

| 日期 | 阶段 / 差异 ID | 修改内容与文件 | 验证命令 / 环境 / 结果 | 定位 | 未解决事项与下一步 |
|---|---|---|---|---|---|
| 2026-09-05 | P0 静态审计 | 建立 D-EDGE/ID/WS/BOOK/IM/FILE/FLOW 差异和六个垂直包 | 静态阅读；运行结果待环境 Agent 回填 | `main@ffe5f0b`，当前工作区 | 先回填 P0.1，再按 P1–P4 分工；修复 Agent 结束后更新差异状态 |
| 2026-09-05 | P1–P4 并行修复 | 工作区已有 Snowflake/WS、同步报名、IM 保存后 ACK、Multipart 签名与续传局部修复；准确边界见 P0.2 | JDK 21.0.6 / Maven 3.9.16，`mvn -B -ntp test`：152 tests，0 failures/errors/skipped；Docker 集成未运行 | 未提交工作区；Surefire 17 份报告 | 不重复实现已有代码；先补各差异行所列剩余项和真实依赖证据 |

每次交接必须写明：实际修改文件、尚未运行的测试、数据迁移状态、可安全继续的下一条动作。未验证项保持“待验证”，历史成功报告不得替代当前运行证据。
