# Changelog

本文件记录 Xidao Poker 的重要变更。

格式参考 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，项目版本将遵循 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)。

## [Unreleased]

### Added

- 初始化 Java 21、Spring Boot 3 和 Maven 后端工程。
- 添加 Spring Boot 应用入口，使 `mvn verify` 能够生成可执行 Jar。
- 实现标准 52 张牌组，并支持注入随机种子以复现测试牌局。
- 实现五张牌牌型判断与七选五最优组合选择。
- 支持 High Card、Pair、Two Pair、Three of a Kind、Straight、Flush、Full House、Four of a Kind 和 Straight Flush。
- 实现 `A-2-3-4-5` Wheel 顺子和完整 kicker 比较。
- 引入统一玩家生命周期 `PlayerStatus`。
- 实现 Check、Call、Bet、Raise、Fold 和 All-in 的基础下注轮转。
- 实现最小加注与 Short All-in 不重新开放加注权的规则。
- 新增统一 `ActionValidator`、稳定错误码和非法行动无副作用约束。
- 实现 Main Pot、Side Pot、Split Pot 和奇数筹码顺时针分配。
- 实现 `GameSession` 与 `Hand` 显式状态机，覆盖盲注、发牌、Preflop / Flop / Turn / River、Showdown、结算和下一手重置。
- 实现最多 10 人行动轮转、Heads-up 特殊盲注顺序和短大盲完整 bring-in。
- 添加查看者过滤的 `GameSnapshot`、不可变 `GameEvent` 和会话级单调递增事件序号。
- 添加房主掉线转移、玩家重连、破产淘汰和中途加入观察者流程。
- 添加 `RoomRuntime`、`RoomRegistry`、`RoomService` 与 `GameApplicationService`，隔离网络层和纯 Java 引擎。
- 添加房间级公平锁和共享 Executor 驱动的单房间有序发送队列。
- 添加 `commandId` 幂等缓存、连接 ID / epoch 校验、`handId` / `turnId` 防延迟行动与旧手牌开始命令保护。
- 添加默认保留 512 个事件的有限回放窗口、1,024 条命令缓存和 1,024 条出站消息上限。
- 添加服务端 `ActionOptions`，提供跟注额、最小 Bet / Raise-to 与最大下注范围。
- 添加重连时主动生成的查看者专属 `GameSnapshot`、精确连接 ID / epoch 绑定，以及广播失败后的完整快照恢复。
- 添加 `GET/POST/DELETE /api/rooms` Spring HTTP 大厅 API、参数校验和稳定错误响应。
- 添加 `/ws/poker` 原生 WebSocket 适配器以及统一客户端/服务端消息信封。
- 添加 WebSocket 握手身份绑定、32 字节随机重连令牌、成功重连令牌轮换和旧 Socket 关闭。
- 添加默认 30 秒断线宽限调度；过期任务通过连接 epoch 防止删除新连接。
- 添加同源 WebSocket 默认策略、可配置可信 Origin、16 KiB 消息上限与并发发送保护。
- 添加仅供服务端历史归档使用的 `CompletedHandSnapshot`，保存所有参与者私有牌、起止筹码、投入、奖金、底池和摊牌结果，不复用网络查看者 Snapshot。
- 添加应用层 `HandHistoryPublisher` / `HandHistoryRepository` 端口、默认 256 容量的有界异步队列、重试退避和优雅关闭。
- 添加 PostgreSQL 历史适配器和 Flyway V1 迁移，覆盖 `game_record`、`poker_user`、`hand_history`、`hand_player`、`game_action` 与 `player_statistic`。
- 添加 `(game_id, hand_id)` 幂等保存、整手事务、JSONB 公共牌 / 底池 / 奖金 / 私有牌以及玩家统计更新。
- 添加 `postgres` Profile 与环境变量数据库配置；默认关闭历史持久化，启用后数据库离线也不会阻止实时 Web 服务启动。
- 添加 Testcontainers PostgreSQL 16 合约测试，在 Docker 可用时验证迁移、JSONB、重复写入和子记录失败后的事务回滚。
- 添加 SLF4J 结构化应用命令日志和发送失败日志。
- 正式建房与连续手牌使用 `SecureRandom` 洗牌，同时保留仅供测试的确定性 seed 入口。
- 添加 114 项引擎、房间应用层、HTTP/WebSocket、历史持久化与故障恢复测试，包括真实 Tomcat WebSocket 升级、PostgreSQL 合约测试和 200 组确定性随机边池守恒场景。
- 添加项目 README，记录架构决策、工程取舍、调试策略、测试策略与 Git 工作流。
- 添加 MIT License。
- 添加 GitHub Actions CI，自动运行后端 Maven Test，并预留默认跳过的前端 Job。
- 添加 React 19、TypeScript、Vite、Zustand 和 React Router 前端工程及 npm 锁文件。
- 添加局域网大厅、创建房间、房间轮询、等待准备、房主开局和最多十席牌桌界面。
- 添加查看者安全的手牌 / 公共牌显示、服务端驱动行动栏、下注范围输入和观战状态提示。
- 添加原生 WebSocket 客户端，支持 token / epoch 会话级保存、自动重连、心跳、事件序号检测、有限回放和 Snapshot 恢复。
- 添加前端 Snapshot 整体替换、增量投影、序号缺口及观察者防卡局测试。
- 添加适用于 Java、Maven、Node、Vite、IDE、日志和本地密钥的 `.gitignore`。
- 添加 `.gitattributes`，统一跨平台文本行尾并标记常见二进制资源。

### Changed

- 将玩家的 Folded、All-in、Disconnected、Spectator 和 Busted 状态统一到单一枚举，减少冲突状态。
- 将底池逻辑移动到独立的 `engine.pot` 模块。
- 将下注合法性判断从轮转逻辑中抽离到统一验证层。
- 将观察者限制在 `GameSession` 层，当前 `Hand` 只接收经过资格筛选的参与者。
- 掉线玩家保留 `DISCONNECTED` 生命周期；当前手资格独立处理，不再伪装成观察者。
- 移除 `GameSession` 与 `Hand` 中无上限增长的事件历史；事件改为按命令返回，短期补发由 `RoomRuntime` 有界缓存负责。
- 命令结果只返回发起者自己的私有快照；其他玩家快照只能通过定向 delivery 发送。
- 房间关闭与加入共享原子生命周期边界，目录只删除已经没有成员、待移除玩家、outbox 或发送任务的房间。
- 手牌、牌组、底池和房间命令增加空值、重复牌、座位、溢出与归属校验。
- 房间摘要增加创建时间、盲注、买入和人数上限，供大厅直接渲染。
- 保留通用 DataSource 自动配置排除项，由条件历史配置显式创建连接池，使默认模式和 PostgreSQL 离线模式都能启动实时服务。
- 将历史表迁移延迟到首次保存手牌时执行；Flyway 或 PostgreSQL 故障通过异步重试隔离，不进入房间锁和实时命令事务。
- 单手接受行动历史设置 8,192 条硬上限，超限时保留牌局运行并写入 `action_history_complete=false`，避免历史功能重新引入无上限内存。
- 玩家行动事件增加 `status` 和 `canAct` 投影字段，使非行动者客户端无需为每次下注请求完整快照。
- 前端 CI 调整为仓库变量 `ENABLE_FRONTEND_CI=true` 时才启用，当前阶段默认保持跳过。

### Fixed

- 修复所有成员离开后空房间永久残留在大厅的问题；房间成员数变为 0 后启动默认 20 秒空置 TTL，周期清理所有到期空房，并通过房间锁、重新加入时间重置和出站队列检查避免误删活跃或仍在发送事件的房间。
- 修复已有客户端收到 `PLAYER_JOINED` 广播后只推进事件序号、却未把新成员投影到玩家列表，导致房主必须刷新页面才能看到后加入玩家的问题；加入事件现在携带完整公开玩家状态并由 Zustand 实时 upsert。
- 修复前端把服务端展示用的跟注额作为 `CALL.amount` 回传、触发后端协议校验并导致无法跟注的问题；`CALL` 现在只提交无金额行动意图，由服务端按当前牌局状态计算实际支付额。
- 为大厅与下注栏的输入控件补充稳定的 `id` / `name`，消除浏览器表单字段诊断警告。
- 修复通过局域网 IP 的普通 HTTP 地址访问时，浏览器缺少 `crypto.randomUUID()` 导致牌桌渲染为空白的问题；客户端 ID 改用兼容非安全上下文的随机字节实现，并增加全局错误恢复界面。
- 修复 Vite 将 WebSocket Origin 错误重写为 `ws://localhost:8080`、导致 Spring 握手返回 403 且客户端无法进入牌桌的问题。
- 修复永久性握手失败时 WebSocket 客户端无限重连并持续刷屏的问题；约 30 秒重试窗口耗尽后停止并显示可恢复错误。
- 明确 Check 与 Call 的互斥条件，不使用强制最小跟注逻辑。
- 弃牌玩家的投入继续保留在底池中，但不再具备获奖资格。
- 修复观察者、破产、All-in 或离线玩家仍可能成为当前行动者并卡住牌局的问题。
- 修复所有玩家均无法行动时状态机不能自动发完公共牌并结算的问题。
- 修复 Short All-in 可以通过 `RAISE` / `ALL_IN` 绕过未重新开放加注权的问题。
- 修复多个 Short All-in 累计达到完整加注额后仍不能重新加注的问题。
- 修复合法行动列表会展示服务端随后拒绝的短额 Bet / Raise。
- 修复非当前行动者掉线弃牌后，存在无人有资格的底池层而导致结算失败的问题。
- 修复大盲短码 All-in 时错误降低 Preflop 跟注基准的问题。
- 修复掉线后已经错过新手牌的玩家重连时被错误恢复为当前手 ACTIVE 的问题。
- 修复旧 Socket 的关闭事件、过期断线任务和重复命令可能影响替换后连接的问题。
- 修复重连只改变在线状态但未主动排入完整私有快照的问题。
- 修复过期重连命令或排队中的旧连接快照可能覆盖新连接状态的问题。
- 修复目录删除与并发加入交错时可能产生不可再发现的孤儿房间问题。
- 修复发送端积压或增量广播失败后客户端可能长期停留在不一致状态的问题。
- 修复外部非法底池数据可能造成部分结算、筹码不守恒或把奖金分给弃牌玩家的问题。
- 修复应用命令已经提交后，发送器调度失败却被错误返回为命令失败的问题。
- 修复旧重连令牌、旧连接快照或部分重连参数可能接管当前 Socket 的问题。
- 修复历史发布、数据库写入或迁移失败可能把已经结算的手牌错误报告为行动失败的问题。
- 修复启用历史持久化时 MyBatis 扫描非 Mapper 接口、事务代理无法代理 final Repository，以及 Flyway 在启动线程提前连接数据库的问题。

## Release process

发布新版本时：

1. 将 `[Unreleased]` 中的变更移动到带日期的版本标题，例如 `## [0.1.0] - 2026-09-01`。
2. 为新的 `[Unreleased]` 创建空的 Added、Changed、Deprecated、Removed、Fixed 和 Security 小节。
3. 确认 CI 全部通过，并更新 README 中的当前状态与测试基线。
4. 创建 Git 标签，例如 `v0.1.0`，再发布对应 GitHub Release。
