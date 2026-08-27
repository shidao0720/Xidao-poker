# Xidao Poker

一个面向局域网多人联机的无限注德州扑克（No-Limit Texas Hold'em）项目，目标支持最多 10 名玩家同时在线。

项目采用服务端权威（Server Authoritative）架构：客户端只提交玩家意图，所有发牌、行动校验、下注轮转、牌型判断、底池分配和筹码结算均由服务端游戏引擎裁决。

> 当前状态：后端核心流程阶段。游戏引擎、`RoomRuntime` 应用层、Spring HTTP / WebSocket 适配器、30 秒安全重连和 PostgreSQL 手牌历史持久化均已落地。当前后端共有 112 项常规测试通过，另有 2 项 Testcontainers PostgreSQL 合约测试在 Docker 可用时执行；React 前端和真实多浏览器联调仍在开发中。

## 目标游戏流程

```text
登录 / 游客身份
    → 大厅创建或加入房间
    → 所有玩家准备
    → 房主开始游戏
    → 发放手牌并收取盲注
    → Preflop / Flop / Turn / River 轮流行动
    → Showdown 或只剩一人时提前结束
    → 主池与边池结算
    → 无筹码玩家进入观战
    → 开始下一手
```

计划支持的核心能力：

- 最多 10 人同桌，局域网浏览器访问
- 创建房间、加入房间、准备和房主开始
- 无限注行动：Check、Call、Bet、Raise、Fold、All-in
- Dealer Button、Small Blind、Big Blind 与两人桌特殊规则
- 五张牌判定、七选五最优牌型与完整 kicker 比较
- Main Pot、Side Pot、Split Pot 和奇数筹码分配
- 玩家破产后观战
- Snapshot + Event 状态同步
- 断线保留席位、限时重连与房主转移
- 历史牌局、行动记录和玩家统计

## 技术栈

### Backend

- Java 21
- Spring Boot 3
- Spring Web
- Spring WebSocket
- MyBatis Plus
- PostgreSQL
- JUnit 5、AssertJ
- Maven

### Frontend（计划）

- React 19、TypeScript、Vite
- Tailwind CSS
- Zustand
- React Router
- Axios
- WebSocket Client
- Framer Motion

Redis 被视为后期优化项，不是第一版核心依赖。

## 系统架构

```text
React Client
    │
    ├── HTTP：房间列表、创建与空房删除
    └── WebSocket：加入/重连、玩家意图、实时事件、状态快照
             │
             ▼
Spring Controller / WebSocket Handler
             │
             ▼
RoomService / GameApplicationService
  用例入口、连接身份、命令日志、错误边界
             │
             ▼
RoomRuntime / RoomEventDispatcher
  房间级原子锁、连接代次、有限缓存、有序异步发送
             │
             ▼
Game Engine（纯 Java）
  GameSession → Hand → BettingRound / PotManager / HandEvaluator

Application Service ─────→ CompletedHandArchive
                           │
                           ▼
                    有界异步历史队列
                           │
                           ▼
                    Repository / PostgreSQL
RoomRuntime ──────────────→ Memory：实时牌局状态
```

后端遵循以下依赖方向：

```text
Controller / WsHandler → Application Service → RoomRuntime → Game Engine
                                      └──────→ Repository abstraction
```

游戏引擎不依赖 Spring、WebSocket、数据库或前端协议，可以直接通过 JUnit 驱动。

## 核心工程决策

### 1. 服务端权威，而不是客户端协商

客户端只能发送类似 `CHECK`、`CALL`、`RAISE_TO 200` 的意图。服务端负责验证：

- 是否轮到该玩家
- 当前阶段是否允许该行动
- Check 与 Call 是否正确区分
- 筹码是否足够
- Bet / Raise 金额是否满足最小加注规则
- Short All-in 是否重新开放加注权

选择该方案是为了确保多人环境中的状态一致性，并避免客户端篡改筹码、手牌或行动顺序。代价是服务端承担更多逻辑，所有交互都需要等待服务端确认；局域网低延迟使这个取舍可以接受。

### 2. 实时状态保存在内存，历史记录写入 PostgreSQL

下注等高频状态变化不会逐次写入数据库。每个房间在内存中持有唯一的实时游戏对象；`HAND_ENDED` 提交后生成与网络 Snapshot 隔离的 `CompletedHandArchive`，离开房间锁后再异步保存整手历史。

优点：

- 行动处理路径短，延迟低
- 避免频繁数据库更新影响牌局流畅度
- 游戏规则不受数据库可用性直接影响

代价：

- 服务进程异常退出时，进行中的一手可能无法恢复
- 多实例部署需要额外的房间归属和状态迁移机制

第一版优先保证单服务实例下的正确性。Redis 后期可用于房间目录、在线状态和临时会话缓存，但不会成为游戏引擎的真实状态源。

历史写入使用固定线程数和有界队列，默认 2 个写线程、256 个待处理任务、最多 3 次尝试。PostgreSQL 离线、迁移失败或 Repository 抛错只会产生结构化日志，不会回滚已经提交的牌局命令；数据库恢复后，后续写入会再次尝试按需执行 Flyway 迁移。队列满时新归档会被拒绝并记录 `HAND_HISTORY_QUEUE_FULL`，而不是阻塞房间线程或无限占用内存。这个取舍优先保证实时牌局可用性；需要审计级零丢失时，应在后续版本加入磁盘型 durable outbox。

单手只记录引擎已接受的玩家行动，最多 8,192 条。达到上限后牌局继续运行，数据库中的 `action_history_complete` 会标记为 `false`，避免用无上限集合换取表面上的完整性。

### 3. 房间级原子锁 + 共享发送执行器

每个 `RoomRuntime` 通过独立公平锁串行提交同一房间的命令，避免两个 WebSocket 行动同时改变一副牌。实际网络发送离开房间锁后执行，并由共享 Executor 为每个房间维持单一 drain loop，确保消息顺序。

这个设计保留了 Actor 模型最重要的“单房间顺序”，但不为每个房间永久占用线程。代价是需要显式处理 outbox 积压、发送失败和锁外 I/O；当前通过有界 outbox 与完整快照降级处理。只有在压力测试证明锁竞争成为瓶颈后，才考虑专用 Actor 框架。

房间关闭与加入共用同一把生命周期锁。目录只会在成员、待移除玩家、outbox 和发送 drain 都清空后删除房间；一旦关闭标记提交，后续加入会被拒绝，从而避免“目录已删除但玩家又加入旧实例”的孤儿房间竞态。

### 4. 命令级事件 + 有界回放，而不是无限 Event Log

游戏引擎以命令式方式更新当前状态，每个命令只返回本次产生的不可变事件。`GameSession` 与 `Hand` 不保存不断增长的事件历史，避免长时间运行导致内存持续上涨。

应用层默认只保留最近 512 个事件用于短暂补发，客户端序号早于缓存窗口时必须请求完整 Snapshot。命令去重缓存默认 1,024 条，发送 outbox 默认 1,024 条；达到上限时丢弃旧增量并为每位在线玩家生成当前快照。第一版不会仅靠事件回放重建全部牌局；已结束手牌的玩家、行动、底池和结算结果通过独立归档模型异步持久化。

### 5. Game Session 与 Hand 生命周期分离

`GameSession` 表示一场持续多手牌的游戏；`Hand` 只表示其中一手。每次开始新手牌，都必须重新初始化：

- hole cards
- community cards
- deck
- pot / side pots
- betting state
- current actor

这个边界可以避免上一手的下注额、手牌或行动标记污染下一手。

### 6. 单一玩家生命周期状态

玩家使用 `PlayerStatus` 表示生命周期，而不是组合多个可能互相冲突的布尔值：

```text
ACTIVE / FOLDED / ALL_IN / DISCONNECTED / SPECTATOR / BUSTED
```

所有行动轮转统一依赖 `player.canAct()`。Folded、All-in、Disconnected、Spectator 和 Busted 玩家不会进入行动队列。

掉线不会被伪装成 `SPECTATOR`：

- 尚可行动的玩家掉线后，保留 `DISCONNECTED` 生命周期，并在当前手按 Folded 处理
- 已经 All-in 的玩家掉线后仍保留摊牌和获奖资格
- 当前手因掉线弃牌后，即使马上重连，也只恢复观看，不重新进入该手行动队列
- 席位和筹码继续保留，下一手是否参与由连接、筹码和准备状态重新筛选

第一版引擎采用立即让掉线的可行动玩家退出当前手、但应用层继续保留座位的确定性语义。Spring WebSocket 层已实现默认 30 秒的共享断线定时器；旧连接的关闭事件和旧 epoch 定时任务都会被忽略。无论宽限期是否结束，`currentActor` 都不会停留在离线玩家身上。

### 7. 行动指针采用防卡局硬约束

底层状态机始终维护以下不变量：

```text
currentActor != null  →  currentActor.canAct() == true
下注街尚未结束       →  必须存在一个 canAct() 的 currentActor
不存在可行动玩家     →  自动推进公共牌、摊牌或结算
```

`SPECTATOR` 和 `BUSTED` 玩家只存在于 `GameSession`，不会被放入当前 `Hand` 的参与者集合。Fold、All-in、掉线等资格变化发生后，`BettingRound` 会立即重新计算行动者；任何 `TURN_CHANGED` 事件在发出前还会再次验证目标可以行动。这些约束专门防止“轮到观察者或离线玩家后无人可操作”的卡局。

### 8. 手牌比较使用可排序数值键

五张牌被编码为一个可直接比较大小的 `long`：高位保存牌型等级，低位保存用于平局比较的 rank/kicker。七张牌遍历 `C(7,5) = 21` 种组合，选择最大值。

相比一开始就实现高度优化的查表算法，21 次五张牌评估更容易验证，且对最多 10 人的人工牌局完全足够。这里优先选择正确性和可测试性。

### 9. Snapshot + Event 同步

客户端通过两种数据保持同步：

- `ROOM_SNAPSHOT`：加入房间、刷新页面、断线重连时替换全部本地牌局状态
- Event：正常游戏过程中应用实时增量事件

每个事件将带有单调递增序号。客户端发现序号不连续时应请求新快照，而不是猜测缺失状态。收到快照时必须 replace state，不能与旧状态盲目 merge。

重连在同一个房间原子操作中完成三件事：校验客户端持有的旧连接 epoch、替换连接 ID、递增 epoch，并生成该玩家专属快照加入定向 outbox。每个 Snapshot delivery 都绑定目标 `playerId + connectionId + connectionEpoch`；发送适配器只能投递到完全匹配的当前连接，排队期间已经失效的旧连接快照必须丢弃。命令确认只允许携带发起者自己的快照，绝不会包含“所有玩家各自的私有快照”。如果增量广播失败，发送器会自动为所有在线玩家排入隐私过滤后的恢复快照。

### 10. 网络命令的幂等与防重放

客户端命令信封必须携带：

```text
commandId + playerId（由连接绑定，不信任消息体）
connectionId + connectionEpoch
handId + turnId（行动命令）
```

- `commandId` 防止同一请求因重试重复扣筹码。
- `connectionId + epoch` 拒绝被刷新页面替换的旧 Socket。
- `handId + turnId` 即使在命令缓存淘汰后，也能拒绝延迟到下一回合或下一手的行动。
- 开始游戏命令绑定客户端看到的上一手 ID，防止延迟的 `START_GAME` 意外开启后续牌局。

服务端 Snapshot 直接给当前行动者提供 `toCall`、实际 `callAmount`、最小 Bet/Raise-to 和最大可下注额；前端不得自行推导这些规则。

## 规则实现中的重要取舍

- Royal Flush 不作为独立牌型等级；它是 Ace-high Straight Flush。
- `A-2-3-4-5` 的顺子 high card 为 5。
- Bet 和 Raise 的金额使用 `raiseTo` 语义，即“本街总下注达到多少”，避免客户端和服务端对增量金额理解不一致。
- 单次 Short All-in 可以提高 `currentBet`，但不足一次完整加注时不会重新开放已行动玩家的加注权；多个 Short All-in 累计达到完整加注额时会重新开放。该行为遵循 [Poker TDA Rule 47](https://www.pokertda.com/view-poker-tda-rules/)。
- 大盲短码 All-in 时，Preflop 的 bring-in 仍按完整大盲计算。
- Side Pot 根据整手牌累计投入分层构造；弃牌玩家的筹码保留在底池中，但不具备获奖资格。
- 强制掉线弃牌导致某个边池没有常规获奖资格者时，该层作为 dead money 由仍留在手牌中的玩家争夺，保证筹码守恒且结算不会中断。
- 平分底池产生的奇数筹码，从 Button 左侧第一个获胜玩家开始顺时针分配，以保证结果确定。
- 观察者不能查看未公开手牌；只显示查看者自己的手牌和 Showdown 后实际摊牌玩家的手牌。

## HTTP 与 WebSocket 协议

大厅 HTTP API：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/rooms` | 获取房间列表和盲注、买入、人数状态 |
| `POST` | `/api/rooms` | 创建房间；服务端生成 `roomId` |
| `DELETE` | `/api/rooms/{roomId}` | 删除没有成员、outbox 或发送任务的空房间 |

创建房间请求示例：

```json
{
  "roomName": "Friday LAN",
  "smallBlind": 5,
  "bigBlind": 10,
  "buyIn": 1000,
  "maxPlayers": 10
}
```

WebSocket 入口为 `/ws/poker`。首次加入使用：

```text
ws://localhost:8080/ws/poker?roomId=<roomId>&playerId=<playerId>&playerName=<url-encoded-name>
```

服务端会定向返回 `CONNECTION_READY` 和 `ROOM_SNAPSHOT`；客户端保存其中的 `connectionEpoch` 与 `resumeToken`。重连时使用：

```text
ws://localhost:8080/ws/poker?roomId=<roomId>&playerId=<playerId>&connectionEpoch=<epoch>&resumeToken=<token>
```

成功重连会递增 epoch、轮换 token、关闭旧 Socket，并主动推送新的查看者专属 Snapshot。token 只存在于网络适配层，不进入 Engine 或日志；旧 token、旧 epoch、旧连接发送的消息都会被拒绝。

该 token 只是局域网版本的“重连持有证明”，不是完整账号认证。若未来开放到互联网，必须在它之前增加登录鉴权、TLS、速率限制和更严格的 Origin 白名单。

客户端消息统一使用：

```json
{
  "type": "PLAYER_ACTION",
  "commandId": "client-generated-uuid",
  "payload": {
    "handId": 12,
    "turnId": 38,
    "action": "RAISE",
    "amount": 200
  }
}
```

已实现的客户端消息为 `READY`、`START_GAME`、`PLAYER_ACTION`、`REQUEST_SNAPSHOT`、`REPLAY_EVENTS`、`LEAVE` 和 `PING`。`roomId`、`playerId`、`connectionId` 与 epoch 全部取自握手绑定，消息体不能覆盖身份。

服务端直接使用稳定的 `GameEventType` 作为事件名称，并额外提供 `CONNECTION_READY`、`COMMAND_RESULT`、`ROOM_SNAPSHOT`、`EVENT_REPLAY`、`ERROR` 和 `PONG`。当前行动者的合法操作与金额边界通过 Snapshot 内的 `actionOptions` 提供，不存在由前端自行计算的 `ACTION_REQUEST` 状态源。

非法行动不会造成连接异常或部分状态修改。错误消息使用稳定 code，展示文案则允许后续调整：

```json
{
  "type": "ERROR",
  "roomId": "room-id",
  "commandId": "client-generated-uuid",
  "payload": {
    "code": "INVALID_AMOUNT",
    "message": "raise amount is below the minimum"
  }
}
```

默认只接受同源 WebSocket。Vite 或 LAN 跨源开发必须在 `poker.network.allowed-origin-patterns` 中显式列出可信 Origin，不建议配置为 `*`。

## 调试策略

### 结构化日志

项目使用 SLF4J + Logback，禁止以 `System.out.println` 作为调试方案。关键日志应包含：

```text
timestamp level module roomId gameId handId playerId event
```

需要记录的主要事件：

- WebSocket：`CONNECT`、`DISCONNECT`、`MESSAGE_RECEIVED`、`MESSAGE_SENT`
- Game Engine：`ACTION_RECEIVED`、`ACTION_ACCEPTED`、`ACTION_REJECTED`
- State Machine：`STATE_CHANGED`、`HAND_STARTED`、`HAND_SETTLED`
- Validation：`NOT_YOUR_TURN`、`INVALID_AMOUNT`、`INSUFFICIENT_CHIPS`

`GameApplicationService` 统一记录 `ROOM_COMMAND_RECEIVED`、`ROOM_COMMAND_COMMITTED`、`ROOM_COMMAND_REJECTED` 及最终事件序号；WebSocket 层记录 `WS_CONNECT`、`WS_DISCONNECT`、`WS_MESSAGE_RECEIVED`、`WS_MESSAGE_SENT` 和拒绝原因。`RoomEventDispatcher` 会记录发送失败并触发 Snapshot 恢复。

日志不能包含其他玩家尚未公开的手牌。服务端内部如需调试手牌，只能在开发环境受控输出。

### Debug Snapshot

游戏引擎提供 `snapshot(viewerId)`：

- phase
- player status 与 stack
- pot / side pots
- community cards
- current actor
- 当前查看者自己的 hole cards

快照必须按查看者过滤隐私信息。生产日志不得直接打印未经脱敏的完整内部状态。

### 确定性复现

牌组支持注入随机种子。记录 seed、初始玩家状态和行动日志后，可以在测试环境重放问题牌局。显式 seed 入口只保留给测试和问题复现；正式建房入口已使用 `SecureRandom`，且不会向 Controller 暴露 seed 参数。

## 测试策略

测试优先级为：

```text
Correctness → State Consistency → Debuggability → Features
```

### 单元测试

纯 Java 游戏引擎使用 JUnit 5 + AssertJ，重点覆盖：

- Hand Evaluation：所有牌型、kicker、平局、Wheel、七选五
- Betting：Check、Call、Bet、Raise、Fold、Full Raise、Short All-in
- Pot：Main Pot、多个 Side Pot、Folded contribution、Split Pot、奇数筹码
- State Machine：合法转换与非法阶段操作
- Player Lifecycle：Disconnect、Reconnect、Busted、Spectator
- Invariants：观察者、破产、弃牌、All-in 和离线玩家永远不会成为当前行动者
- Application：连接 ID / epoch、防重复命令、过期 hand/turn、有限回放缓存、outbox 降级与定向快照隐私
- History：完成手牌敏感投影、起止筹码核对、单手行动上限、有界异步队列、重试与关闭语义
- Persistence：重复手牌幂等、玩家统计只更新一次、JSON 序列化和 Repository 调用边界

### 流程测试

通过固定 seed 和行动序列覆盖：

- 第一手完整牌局
- 下一手重新初始化
- Heads-up 盲注与行动顺序
- 所有剩余玩家 All-in 后自动发完公共牌
- 玩家破产并进入观战
- 观战者中途加入
- 房主断线后的所有权转移
- 10 人完整行动与筹码守恒
- 短大盲、累计 Short All-in 和掉线后的 dead money 结算

### 故障测试

- 非当前玩家发送行动
- 重复或过期消息
- 非法 Raise 金额
- 筹码不足
- WebSocket 短暂断线与 30 秒内重连
- 客户端事件序号缺失后重新请求 Snapshot
- 事件广播失败后自动回落为 viewer-specific Snapshot
- PostgreSQL 暂时不可用时，进行中的牌局不被中断
- 历史发布器异常或队列满载时，已经提交的 `HAND_ENDED` 不被回滚
- Flyway 首次迁移失败后可重试，启用持久化但数据库离线时完整 Web 服务仍可启动
- 有 Docker 时使用 Testcontainers 验证 PostgreSQL 16 的迁移、JSONB、幂等和事务回滚

### 运行测试

```bash
cd backend
mvn test
```

当前本地基线：114 项后端测试被发现，其中 112 项通过、0 failures / 0 errors；本机没有 Docker 时，2 项真实 PostgreSQL 合约测试自动跳过。Docker 可用的 CI / 开发环境会执行全部 114 项。该数字会随开发持续增长，以 CI 的实际结果为准。

## Git 工作流

### 分支策略

- `main`：始终保持可构建、测试通过，可用于演示或发布
- `develop`：可选的集成分支；项目规模较小时可直接使用短生命周期功能分支合并到 `main`
- `feature/<scope>-<name>`：新功能，例如 `feature/engine-hand-state-machine`
- `fix/<scope>-<name>`：缺陷修复，例如 `fix/betting-short-all-in`
- `docs/<name>`：文档变更
- `refactor/<scope>-<name>`：无行为变化的重构

功能分支应保持短生命周期，并在合并前同步目标分支。

### Commit 规范

建议采用 Conventional Commits：

```text
feat(engine): add heads-up blind rotation
fix(betting): keep raise closed after short all-in
test(pot): cover odd-chip split rule
docs(readme): document architecture trade-offs
refactor(player): replace lifecycle booleans with status enum
```

一次提交只处理一个清晰主题。规则修复必须同时提交能够复现该问题的测试。

### Pull Request 要求

每个 PR 至少说明：

- 解决什么问题
- 为什么采用当前方案
- 是否改变协议、数据库或游戏规则
- 如何测试
- 是否存在迁移或兼容风险

合并前必须满足：

- `mvn test` 通过
- 前端 lint、typecheck 和测试通过（前端建立后）
- 没有提交密码、数据库凭据、私人 IP 或未脱敏日志
- 协议变更同步更新共享类型和 README
- 规则变更包含对应测试

推荐使用 Squash Merge，使 `main` 历史保持简洁。发布版本使用语义化版本标签，例如 `v0.1.0`。

### 不应提交的文件

```text
backend/target/
frontend/node_modules/
frontend/dist/
.env
.env.*
!.env.example
*.log
IDE user settings
```

后续将通过 `.gitignore` 固化这些规则。

仓库根目录已经提供 `.gitignore`。如果新增构建工具、运行目录或本地密钥文件，应在首次提交这些文件之前同步更新忽略规则。

### 持续集成

GitHub Actions 配置位于 `.github/workflows/ci.yml`：

- Push 或 Pull Request 到 `main`、`develop` 时运行
- 使用 Temurin Java 21 执行后端 `mvn test`
- 测试失败时上传 Surefire 报告，保留 7 天
- 检测不到 `frontend/package.json` 时，前端 Job 自动显示为 Skipped
- 前端模块建立后，预留 Job 自动启用 Node.js 22 的安装、lint、typecheck、test 和 build
- 同一分支的新提交会取消仍在运行的旧 CI，避免浪费资源

在推送到 GitHub 前，应先在本地运行与 CI 相同的关键检查。

### Changelog

所有面向使用者或开发者的重要变化都记录在 `CHANGELOG.md` 的 `[Unreleased]` 小节。普通格式化、注释修正和没有行为变化的内部整理可以不记录。

发布时将 `[Unreleased]` 内容移动到带日期的语义化版本中，并创建对应的 Git 标签和 GitHub Release。

## 本地开发环境

当前后端要求：

- JDK 21
- Maven 3.9+

验证环境：

```bash
java -version
mvn -version
```

运行现阶段测试：

```bash
cd backend
mvn test
```

启动当前后端：

```bash
cd backend
mvn spring-boot:run
```

默认 HTTP 地址为 `http://localhost:8080/api/rooms`，WebSocket 地址为 `ws://localhost:8080/ws/poker`。历史持久化默认关闭，启动服务不要求本机安装 PostgreSQL。

### 启用 PostgreSQL 历史持久化

先创建空数据库，再通过环境变量提供连接信息。不要把真实凭据写入仓库：

```bash
export POKER_DB_URL='jdbc:postgresql://localhost:5432/xidao_poker'
export POKER_DB_USERNAME='<username>'
export POKER_DB_PASSWORD='<password>'

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

`postgres` Profile 只负责打开 `poker.persistence.enabled` 并读取环境变量。Flyway 不在启动线程中连接数据库，而是在首次保存已结束手牌时按需迁移；迁移或写入失败由有界异步写入器重试，实时 HTTP / WebSocket 服务继续运行。

首版迁移创建以下表：

- `game_record`：房间级游戏配置、时间范围和已保存手牌数
- `poker_user`：局域网玩家 ID 与最近显示名
- `hand_history`：公共牌、底池、奖金和行动完整性标记
- `hand_player`：座位、私有牌、起止筹码、投入、奖金与摊牌结果
- `game_action`：引擎接受的行动及行动后筹码状态
- `player_statistic`：参局数、获胜手数、累计投入和累计奖金

同一手使用 `(game_id, hand_id)` 唯一键。重复异步提交返回 `ALREADY_EXISTS`，不会重复插入行动或累加玩家统计；一手牌的主记录、玩家、行动与统计在同一事务中提交。

## 目录结构（当前与规划）

```text
Xidao-poker/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/xidao/poker/
│       │   ├── application/
│       │   │   ├── command/
│       │   │   ├── history/
│       │   │   └── room/
│       │   ├── config/
│       │   ├── engine/
│       │   │   ├── action/
│       │   │   ├── card/
│       │   │   ├── deck/
│       │   │   ├── eval/
│       │   │   ├── event/
│       │   │   ├── game/
│       │   │   ├── history/
│       │   │   ├── player/
│       │   │   ├── pot/
│       │   │   ├── snapshot/
│       │   │   └── table/
│       │   ├── persistence/history/
│       │   └── web/
│       │       ├── api/
│       │       ├── protocol/
│       │       └── ws/
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-postgres.yml
│       │   └── db/migration/
│       └── test/java/com/xidao/poker/
│           ├── application/history/
│           ├── application/room/
│           ├── engine/
│           ├── persistence/history/
│           └── web/
├── frontend/                  # 计划
└── README.md
```

## Roadmap

- [x] Maven / Java 21 后端基础工程
- [x] 牌组与可复现洗牌
- [x] 五张牌、七选五牌型判断
- [x] 玩家生命周期基础模型
- [x] 无限注下注轮基础规则
- [x] Main Pot / Side Pot / Split Pot
- [x] `GameSession` 与 `Hand` 核心状态机
- [x] 查看者过滤 Snapshot、单调递增 Event 与房主转移底层逻辑
- [x] 观察者 / 破产 / 掉线玩家防卡局约束
- [x] `RoomRuntime`、房间目录与大厅应用服务基础
- [x] 有界事件回放、命令幂等、连接代次与异步定向 outbox
- [x] Spring HTTP 大厅 API
- [x] WebSocket 协议、身份绑定与实时广播
- [x] PostgreSQL 手牌历史、行动记录与玩家统计持久化
- [ ] React 大厅、房间和牌桌界面
- [x] 引擎 / 应用层断线重连、旧连接隔离和观战等待下一手
- [x] WebSocket 30 秒宽限调度与 token / epoch 安全重连
- [ ] 多浏览器重连联调
- [ ] 多浏览器 10 人局域网联调
- [x] Backend Maven Test CI（前端模块不存在时自动跳过）
- [ ] 容器化与首个 GitHub Release

## 安全与公平性说明

本项目当前定位为局域网娱乐和工程实践，不涉及真钱、充值或提现。任何涉及真实资金的部署都会引入额外的法律、合规、安全与审计要求，不在当前范围内。

请勿在 Issue、日志或提交中公开密码、数据库连接串、令牌或未公开玩家手牌。

## Contributing

项目仍处于架构和核心规则建设阶段。提交功能前请先创建 Issue 或 Discussion 描述场景，特别是涉及德州扑克规则、WebSocket 协议、持久化模型或状态机变更时。

规则正确性优先于功能数量。任何规则修复都应附带回归测试。

## License

本项目使用 MIT License。详见仓库根目录的 `LICENSE` 文件。
