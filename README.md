# Xidao LAN Game Hub｜局域网德州扑克与多人联机游戏

## 项目概览

一个适合朋友聚会的开源局域网多人游戏平台。内置最多支持 10 人的无限注德州扑克（多人联机纸牌游戏），以及 2D 载具对战游戏。主机启动服务后，同一网络中的玩家通过浏览器即可加入，无需安装游戏客户端。支持 Docker 一键部署，所有筹码均为无现金价值的娱乐点数。

A self-hosted LAN multiplayer browser game hub featuring No-Limit Texas Hold'em poker and 2D vehicle battles. Supports up to 10 players per room, real-time WebSocket communication, and Docker deployment.

项目采用服务端权威（Server Authoritative）架构：客户端只提交玩家意图，所有发牌、行动校验、下注轮转、牌型判断、底池分配和筹码结算均由服务端游戏引擎裁决。
[English technical note: Poker State Machine — Design and Source Code](docs/poker-state-machine.en.md)
> 当前状态：LAN Alpha，可用于局域网试玩。德州扑克、载具竞技、账户与商城、好友在线状态及运营后台均已实现；公网部署仍待完成。每个房间最多 10 人不代表已验证任意数量房间的并发承载能力。

## 快速开始

完整账户模式推荐使用 Docker Desktop（Linux 容器模式）。在项目根目录的 PowerShell 中运行；已有 `.env.lan` 时保留原文件和密码：

```powershell
# 仅首次创建配置
if (-not (Test-Path .env.lan)) { Copy-Item .env.lan.example .env.lan }
# 编辑 .env.lan，设置自己的 POKER_DB_PASSWORD
.\lan-docker-start.bat
```

先确认 Docker Desktop 的引擎已经运行。构建成功后，主机打开 `http://localhost:8080`，
其他玩家打开启动脚本列出的 `http://<主机局域网IPv4>:8080`。选择实际 Wi-Fi / 网线地址，
不要使用 VPN 或 Docker 虚拟网卡地址；主机和 Docker 服务需要保持运行。
如果设置了 `POKER_HTTP_PORT`，请使用对应端口。首次构建需要联网下载依赖。

Docker 构建已包含 Java、Maven 和 Node 环境，主机无需另外安装这些工具；本地源码开发要求见
[本地开发环境](#本地开发环境)。不使用 PostgreSQL 的游客模式请参阅同节中的 `lan-start.bat`。

## 已实现的页面与功能

| 页面 | 路径 | 内容 |
| --- | --- | --- |
| PLAY | `/play` | 扑克大厅、创建与加入房间、离桌盈亏 |
| ARENA | `/arena` | 载具竞技大厅、随机地图与连续回合 |
| FRIENDS | `/friends` | 申请、接受、拒绝、删除好友与在线状态 |
| LEADERBOARD | `/leaderboard` | 胜利手数、累计奖金、单手净收益三个 Top 3 |
| STORE | `/store` | 英魂结晶购买外观与装备；主页风格分类暂为空 |
| 个人主页 | `/profile` | 头像、装扮、钱包兑换与兑换码 |
| 站内邮件 | `/mail` | 公告、通知与奖励附件领取 |
| ADMIN | `/admin` | 兑换码、群发邮件、账号余额、密码重置及好友关系管理 |

账户、好友、商城、邮件、排行榜和运营后台需要 PostgreSQL；ADMIN 还要求管理员权限。
未登录时会显示登录/注册页面，根路径 `/` 登录后进入 PLAY。
好友关系归属于账户，前端每 20 秒发送登录会话心跳，服务端以最近 45 秒内的有效会话判定在线，
因此关闭页面后的离线标记有延迟；当前尚未实现好友邀请入房或聊天。

### 管理员初始化

当前 LAN 版本在数据库没有管理员时，会尝试将新注册账户设为管理员；V9 迁移对已有数据库
会选取最早创建的账户作为初始管理员。新部署请由部署者先完成注册，再邀请朋友加入。
这不是固定用户名或内置密码，也不是已经完成公网加固的管理员初始化方案。

管理员可查看账号资料和货币、调整余额、编辑好友关系并重置密码；接口不提供明文密码或
密码哈希。重置密码会撤销该账户已有登录会话。货币调整与好友管理等操作会记录审计信息。

V8 迁移包含公开测试兑换码 `FATE-STAY-POKER`，每个账户可领取一次 100 英魂结晶。
它不是私人运营凭据；如不需要该测试奖励，可在 ADMIN 中停用。

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

已实现的扑克核心能力：

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

### Frontend

- React 19、TypeScript、Vite
- Zustand
- React Router
- 原生 Fetch / WebSocket
- 响应式 CSS

Redis 被视为后期优化项，不是第一版核心依赖。

## 系统架构

```text
React Client
    │
    ├── HTTP：房间目录、账户、好友、钱包、商城与运营接口
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

上图展开的是扑克模式。载具模式使用独立的
`ArenaApplicationService → ArenaRoomRuntime → ArenaSession`，以约 30 tick/s
推进物理状态；两种游戏共享账户基础设施，各自维护房间状态与 WebSocket 协议。

前端遵循单向数据流：

```text
HTTP / WebSocket
       │
       ▼
API Client / PokerSocket
       │
       ▼
Zustand（唯一牌局状态源）
       │
       ▼
Lobby / Table / Action components
```

组件不复制 `players`、`phase` 或当前行动者状态。`ROOM_SNAPSHOT` 会整体替换 Zustand 中的旧快照；增量事件只投影服务端明确给出的字段。行动栏直接渲染 Snapshot 中的 `actionOptions`，不会在浏览器重新实现下注规则。重连凭据仅保存在当前浏览器标签页的 `sessionStorage`，刷新时用于携带旧 epoch/token，成功后由服务端轮换。

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

账户、钱包和牌桌买入属于低频关键事务，启用 PostgreSQL 后同步提交，不走手牌历史异步队列。首次入座会把买入从账户钱包转入唯一的 `table_buy_in` 托管记录；重连复用该记录，不会重复扣款。只有服务端产生带最终筹码的 `PLAYER_LEFT` 后才返还余额，因此主动离开、断线超时和 All-in 后延迟离桌会汇入同一结算路径。数据库瞬时失败时结算监听器以幂等请求号重试。

离桌后的盈亏展示同样以 `table_buy_in` 为准：前端回到大厅后查询托管状态，`ACTIVE` 时只显示“结算处理中”，`SETTLED` 后以 `returned_chips - buy_in` 展示净输赢并刷新钱包。兑换码不保存明文，只保存标准化代码的 SHA-256 摘要；核销记录、全局次数、钱包余额与账本流水在同一个数据库事务中更新。

### 3. 双货币是单向消耗模型

- 筹码用于牌桌买入、签到奖励和排行榜统计。
- `10 筹码 = 1 英魂结晶`，兑换必须由玩家明确确认且只能单向进行。
- 商城只能使用英魂结晶；首批 14 件商品覆盖头像框、牌背、称号、局内按钮效果、结算演出和组合珍藏，“主页风格”分类暂时保留为空。
- 商品目录、价格、购买资格和装备槽位全部由服务端裁决。购买命令携带 `requestId`，网络重试不会重复扣款；客户端只能装备数据库中已经拥有且类别匹配的物品。
- 已装备外观在 WebSocket 身份握手时由账户档案注入房间状态，客户端不能通过查询参数伪造皮肤。头像框、牌背、称号、局内按钮效果和胜利结算演出会进入牌桌查看者快照；主页风格不进入牌局。
- 同一真实身份可拥有多个游戏 ID，但一个游戏 ID 只能归属一个真实身份；同一真实身份同时只能占用一个有资金的牌桌席位。
- 两种货币都只属于 play-money，不可充值、提现、换现或在玩家间转移。

这个设计让牌局收益与外观消费形成长期循环，同时避免可逆兑换和玩家转账引入套利、洗分与真实资金风险。代价是兑换不可撤销，前端必须明确展示兑换比例与确认提示。

### 4. 房间级原子锁 + 共享发送执行器

每个 `RoomRuntime` 通过独立公平锁串行提交同一房间的命令，避免两个 WebSocket 行动同时改变一副牌。实际网络发送离开房间锁后执行，并由共享 Executor 为每个房间维持单一 drain loop，确保消息顺序。

这个设计保留了 Actor 模型最重要的“单房间顺序”，但不为每个房间永久占用线程。代价是需要显式处理 outbox 积压、发送失败和锁外 I/O；当前通过有界 outbox 与完整快照降级处理。只有在压力测试证明锁竞争成为瓶颈后，才考虑专用 Actor 框架。

房间关闭与加入共用同一把生命周期锁。目录只会在成员、待移除玩家、outbox 和发送 drain 都清空后删除房间；一旦关闭标记提交，后续加入会被拒绝，从而避免“目录已删除但玩家又加入旧实例”的孤儿房间竞态。

### 5. 命令级事件 + 有界回放，而不是无限 Event Log

游戏引擎以命令式方式更新当前状态，每个命令只返回本次产生的不可变事件。`GameSession` 与 `Hand` 不保存不断增长的事件历史，避免长时间运行导致内存持续上涨。

应用层默认只保留最近 512 个事件用于短暂补发，客户端序号早于缓存窗口时必须请求完整 Snapshot。命令去重缓存默认 1,024 条，发送 outbox 默认 1,024 条；达到上限时丢弃旧增量并为每位在线玩家生成当前快照。第一版不会仅靠事件回放重建全部牌局；已结束手牌的玩家、行动、底池和结算结果通过独立归档模型异步持久化。

### 6. Game Session 与 Hand 生命周期分离

`GameSession` 表示一场持续多手牌的游戏；`Hand` 只表示其中一手。每次开始新手牌，都必须重新初始化：

- hole cards
- community cards
- deck
- pot / side pots
- betting state
- current actor

这个边界可以避免上一手的下注额、手牌或行动标记污染下一手。

### 7. 玩家状态采用三个正交维度

连接状态、座位状态和当前手状态分别建模，不能压缩成互斥的单一枚举：

```text
ConnectionStatus: CONNECTED / DISCONNECTED
SeatStatus:       SEATED / SPECTATOR / BUSTED
HandStatus:       NOT_IN_HAND / ACTIVE / FOLDED / ALL_IN
```

`PlayerStatus` 只作为旧 UI 的兼容投影，不参与底层规则裁决。所有行动轮转统一依赖 `player.canAct()`，其条件为“已连接 + 有效座位 + 当前手 ACTIVE”；底池资格则只依赖当前手状态。因此 `DISCONNECTED + ALL_IN`、`SPECTATOR + ALL_IN` 等组合可以被正确表达，玩家离开连接或席位后仍不会丢失本手已经取得的底池资格。

掉线不会被伪装成 `SPECTATOR`：

- 尚可行动的玩家掉线后，保留 `DISCONNECTED` 生命周期，并在当前手按 Folded 处理
- 已经 All-in 的玩家掉线后仍保留摊牌和获奖资格
- 当前手因掉线弃牌后，即使马上重连，也只恢复观看，不重新进入该手行动队列
- 席位和筹码继续保留，下一手是否参与由连接、筹码和准备状态重新筛选

第一版引擎采用立即让掉线的可行动玩家退出当前手、但应用层继续保留座位的确定性语义。Spring WebSocket 层已实现默认 30 秒的共享断线定时器；旧连接的关闭事件和旧 epoch 定时任务都会被忽略。无论宽限期是否结束，`currentActor` 都不会停留在离线玩家身上。

WebSocket 命令、网络断线、断线宽限到期、回合超时和空房清理全部进入同一个 `RoomRuntime` 公平锁串行执行路径。计时任务只携带不可变的 `roomId + handId + turnId` 作用域；执行时重新校验当前房间版本，过期任务直接忽略。回合超时也只向应用层提交意图，最终由服务端 `legalActions()` 决定自动 Check 或 Fold，客户端不推断规则。默认回合时限可通过 `poker.network.turn-timeout` 调整。

房间的真实成员数变为 0 后会记录空置起点，默认满 45 秒后由共享清理任务从大厅目录删除。重新加入会重置该期限；清理时仍会在房间锁内复查成员、待移除玩家、outbox 和发送任务，避免与并发加入或尚未完成的离开广播竞态。普通断线仍先执行 30 秒重连保护，等待重连的成员不算空房；启动时还会强制校验空房 TTL 必须严格长于重连宽限。期限与扫描间隔可通过 `poker.room.empty-ttl` 和 `poker.room.cleanup-interval` 配置。

### 8. 行动指针采用防卡局硬约束

底层状态机始终维护以下不变量：

```text
currentActor != null  →  currentActor.canAct() == true
下注街尚未结束       →  必须存在一个 canAct() 的 currentActor
不存在可行动玩家     →  自动推进公共牌、摊牌或结算
```

`SPECTATOR` 和 `BUSTED` 玩家只存在于 `GameSession`，不会被放入当前 `Hand` 的参与者集合。Fold、All-in、掉线等资格变化发生后，`BettingRound` 会立即重新计算行动者；任何 `TURN_CHANGED` 事件在发出前还会再次验证目标可以行动。这些约束专门防止“轮到观察者或离线玩家后无人可操作”的卡局。

### 9. 手牌比较使用可排序数值键

五张牌被编码为一个可直接比较大小的 `long`：高位保存牌型等级，低位保存用于平局比较的 rank/kicker。七张牌遍历 `C(7,5) = 21` 种组合，选择最大值。

相比一开始就实现高度优化的查表算法，21 次五张牌评估更容易验证，且对最多 10 人的人工牌局完全足够。这里优先选择正确性和可测试性。

### 10. Snapshot + Event 同步

客户端通过两种数据保持同步：

- `ROOM_SNAPSHOT`：加入房间、刷新页面、断线重连时替换全部本地牌局状态
- Event：正常游戏过程中应用实时增量事件

每个事件带有单调递增序号。客户端发现序号不连续时请求回放或新快照，不猜测缺失状态。收到快照时必须 replace state，不能与旧状态盲目 merge。

重连在同一个房间原子操作中完成三件事：校验客户端持有的旧连接 epoch、替换连接 ID、递增 epoch，并生成该玩家专属快照加入定向 outbox。每个 Snapshot delivery 都绑定目标 `playerId + connectionId + connectionEpoch`；发送适配器只能投递到完全匹配的当前连接，排队期间已经失效的旧连接快照必须丢弃。命令确认只允许携带发起者自己的快照，绝不会包含“所有玩家各自的私有快照”。如果增量广播失败，发送器会自动为所有在线玩家排入隐私过滤后的恢复快照。

前端收到不连续序号时先请求 `REPLAY_EVENTS`。事件仍在 512 条窗口内时按序补齐；窗口之外或回放仍不连续时改为 `REQUEST_SNAPSHOT`。观察者、掉线和破产玩家即使留在玩家列表中，其界面也不会生成行动按钮，因为可操作性只来自当前查看者 Snapshot 的 `actionOptions`。

### 11. 网络命令的幂等与防重放

客户端命令信封必须携带：

```text
requestId + playerId（由连接绑定，不信任消息体）
connectionId + connectionEpoch
handId + turnId（行动命令）
```

- `requestId` 防止同一请求因重试重复扣筹码；服务端内部将其作为命令幂等键。
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
| `POST` | `/api/rooms` | 创建房间；服务端生成 `roomId`，账户模式要求登录 |
| `DELETE` | `/api/rooms/{roomId}` | 删除没有成员、outbox 或发送任务的空房间，账户模式要求登录 |
| `POST` | `/api/auth/register` | 创建真实身份、首个游戏 ID，或为同一身份添加游戏 ID |
| `POST` | `/api/auth/login` | 用真实姓名、游戏 ID 和密码登录 |
| `POST` | `/api/auth/logout` | 注销 HttpOnly 会话 Cookie |
| `GET` | `/api/account/me` | 获取自己的游戏 ID 列表和双货币余额 |
| `POST` | `/api/account/check-in` | 每个北京时间自然日签到一次并领取 500 筹码 |
| `GET` | `/api/account/mail` | 获取自己的站内收件箱与未读数 |
| `PUT` | `/api/account/mail/{mailId}/read` | 将本人邮件标记为已读 |
| `POST` | `/api/account/mail/{mailId}/claim` | 幂等领取邮件中的筹码、结晶或皮肤附件 |
| `POST` | `/api/admin/mail/broadcast` | 仅管理员可向所有现有账户群发公告、通知或奖励 |
| `GET` | `/api/admin/overview` | 管理员运营数据概览 |
| `GET` | `/api/admin/redemption-codes` | 管理员查看兑换码摘要、限额和状态（不返回明文） |
| `POST` | `/api/admin/redemption-codes` | 管理员幂等创建兑换码，明文仅在创建响应中返回一次 |
| `PUT` | `/api/admin/redemption-codes/{hash}/enabled` | 管理员幂等启用或停用兑换码 |
| `GET` | `/api/friends` | 查看好友、收到与发出的好友申请以及好友在线状态 |
| `POST` | `/api/friends/heartbeat` | 更新当前登录会话的服务端在线心跳 |
| `POST` | `/api/friends/requests` | 按游戏 ID 发送好友申请 |
| `POST` | `/api/friends/{id}/accept` | 接受本人收到的好友申请 |
| `POST` | `/api/friends/{id}/reject` | 拒绝本人收到的好友申请 |
| `POST` | `/api/friends/{id}/remove` | 删除本人的已接受好友关系 |
| `GET` | `/api/admin/accounts` | 管理员查看账户身份、游戏 ID、在线状态与货币余额 |
| `POST` | `/api/admin/accounts/{id}/wallet-adjustments` | 管理员按增减值和原因调整娱乐货币并写入流水与审计 |
| `POST` | `/api/admin/accounts/{id}/password-reset` | 管理员重置密码并注销目标账户全部旧会话 |
| `GET` | `/api/admin/friendships` | 管理员查看好友关系图 |
| `POST` | `/api/admin/friendships` | 管理员建立好友关系或批准已有申请 |
| `POST` | `/api/admin/friendships/{id}/remove` | 管理员解除好友关系 |
| `POST` | `/api/account/redeem` | 幂等核销兑换码并把奖励写入钱包账本 |
| `GET` | `/api/account/table-result?roomId=...` | 查询本人指定牌桌的托管结算与净输赢 |
| `POST` | `/api/wallet/exchange` | 按 10:1 将筹码单向兑换为英魂结晶 |
| `GET` | `/api/leaderboards` | 获取胜利手数、累计奖金和单手净收益三个 Top 3 |
| `GET` | `/api/store/catalog` | 获取服务端权威商城目录和价格 |
| `POST` | `/api/store/purchase` | 用英魂结晶幂等购买商品或组合珍藏 |
| `PUT` | `/api/account/loadout/{slot}` | 装备或卸下已拥有的对应槽位外观 |

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

WebSocket 入口为同源 `/ws/poker`。账户模式下浏览器会自动发送登录时取得的 HttpOnly Cookie，服务端以数据库身份覆盖前端提交的玩家 ID；游客模式继续使用浏览器生成的局域网身份。首次加入握手包含协议与构建版本：

```text
<current-origin>/ws/poker?roomId=<roomId>&playerId=<playerId>&playerName=<name>&protocolVersion=1&buildVersion=0.1.0
```

服务端会定向返回 `CONNECTION_READY` 和 `ROOM_SNAPSHOT`；客户端保存其中的 `connectionEpoch` 与 `resumeToken`。重连时使用：

```text
<current-origin>/ws/poker?roomId=<roomId>&playerId=<playerId>&connectionEpoch=<epoch>&resumeToken=<token>&protocolVersion=1&buildVersion=0.1.0
```

成功重连会递增 epoch、轮换 token、关闭旧 Socket，并主动推送新的查看者专属 Snapshot。token 只存在于网络适配层，不进入 Engine 或日志；旧 token、旧 epoch、旧连接发送的消息都会被拒绝。

`resumeToken` 只是某个牌桌连接的重连持有证明，不代替账户会话。账户模式使用另一枚只保存 SHA-256 哈希的随机会话令牌，并通过 SameSite=Strict、HttpOnly Cookie 传输；密码使用 BCrypt 保存。若未来开放到互联网，仍必须增加 TLS、速率限制、CSRF 审计和更严格的 Origin 白名单。

客户端消息统一使用：

```json
{
  "type": "PLAYER_ACTION",
  "requestId": "client-generated-uuid",
  "payload": {
    "handId": 12,
    "turnId": 38,
    "action": "RAISE",
    "amount": 200
  }
}
```

已实现的客户端消息为 `READY`、`START_GAME`、`PLAYER_ACTION`、`REQUEST_SNAPSHOT`、`REPLAY_EVENTS`、`LEAVE` 和 `PING`。`roomId`、`playerId`、`connectionId` 与 epoch 全部取自握手绑定，消息体不能覆盖身份。

服务端直接使用稳定的 `GameEventType` 作为事件名称，并额外提供 `CONNECTION_READY`、`COMMAND_RESULT`、`ROOM_SNAPSHOT`、`EVENT_REPLAY`、`ERROR` 和 `PONG`。每个服务端信封携带 `protocolVersion` 和 `buildVersion`；不兼容握手会在加入房间前被拒绝，前端收到不同发布构建时会停止重连并提示刷新。当前行动者的合法操作与金额边界通过 Snapshot 内的 `actionOptions` 提供，不存在由前端自行计算的 `ACTION_REQUEST` 状态源。

非法行动不会造成连接异常或部分状态修改。错误消息使用稳定 code，展示文案则允许后续调整：

```json
{
  "type": "ERROR",
  "roomId": "room-id",
  "requestId": "client-generated-uuid",
  "payload": {
    "code": "INVALID_AMOUNT",
    "message": "raise amount is below the minimum"
  }
}
```

默认只接受同源 WebSocket。仓库自带的 Vite `/ws` 代理会在升级请求中把 Origin 重写为后端的 HTTP Origin，因此通过 Vite 进行 localhost / LAN 开发无需放宽白名单；只有浏览器绕过 Vite、直接跨源连接后端时，才必须在 `poker.network.allowed-origin-patterns` 中显式列出可信 Origin，不建议配置为 `*`。

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

日志不得包含密码、会话或重连令牌、任何尚未公开的手牌。手牌问题通过测试用固定种子和合成牌局复现，不将真实玩家私有状态打印到日志中。

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

### 前端同步排错

前端问题先按 `connection state → ROOM_SNAPSHOT.lastSequence → 后续 Event.sequence → 当前 viewer actionOptions` 的顺序定位。浏览器 Network 面板可检查 WebSocket 信封，但截图或 Issue 必须删除 `resumeToken` 和未公开手牌。若界面序号落后，先发送 `REPLAY_EVENTS`；无法补齐时以新 Snapshot 整体替换，不通过手工修改 Zustand 或组件局部状态“修好”界面。重复连接错误应检查是否同时打开了持有同一玩家身份的旧标签页。

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
- Player State：Connection / Seat / Hand 三维正交、Disconnect、Reconnect、Busted、Spectator、离线 All-in
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
- 1,000 手确定性自动对局，逐行动检查筹码守恒、非负筹码和 `currentActor.canAct()`

### 故障测试

- 非当前玩家发送行动
- 重复或过期消息
- 非法 Raise 金额
- 筹码不足
- WebSocket 短暂断线与 30 秒内重连
- 空房间满 45 秒批量清理、期限前不删除及重新加入后旧期限失效，并验证该期限严格长于 30 秒重连宽限
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

cd ../frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

前端状态同步测试重点锁定 Snapshot 整体替换、事件序号缺口、服务端行动投影，以及观察者/破产玩家不进入行动队列。测试数量会随开发持续增长，以本地验证和 CI 的实际结果为准。

最近一次后端完整测试报告为 169 项、0 失败、0 错误、0 跳过，包含好友关系、在线状态和管理员操作的 PostgreSQL 集成测试；前端最近一次完整检查为 38 项测试通过，lint、typecheck 和生产构建通过。这些是已有验证基线，不代表每次文档或素材修改都重跑了全部测试。

后端还覆盖随机迷宫双通路、弹丸寿命/弹药/自伤、载具技能周期、自动续局与 300 回合连续运行不变量。Testcontainers 需要可访问的 Docker 引擎；普通镜像构建阶段没有 Docker Socket 时，10 项数据库集成测试会跳过。应分别检查测试报告的 Tests、Failures、Errors、Skipped，不能把“169 项运行、10 项跳过”写成“169 项全部通过”。自动模拟不替代真实多设备网络与延迟测试。

## Git 工作流

公开版本使用独立的清理历史副本，原开发仓库保留完整历史。生成方式、排除范围及后续更新注意事项见 [公开发布副本流程](docs/public-release.md)。不要把原仓库或整个 `release/` 目录直接上传。

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


### Changelog

所有面向使用者或开发者的重要变化都记录在 `CHANGELOG.md` 的 `[Unreleased]` 小节。普通格式化、注释修正和没有行为变化的内部整理可以不记录。

发布时将 `[Unreleased]` 内容移动到带日期的语义化版本中，并创建对应的 Git 标签和 GitHub Release。


## 本地开发环境

### LAN 同端口发布（推荐联机方式）

正式局域网联机不运行 Vite 开发服务器。React 先构建到 `frontend/dist`，随后 Maven 将这些文件打入 Spring Boot JAR；页面、`/api` 和 `/ws` 全部由同一个 `8080` 端口提供。

Windows 源码构建并启动：

```bat
lan-build.bat
lan-start.bat
```

若还没有 JAR，`lan-start.bat` 会先调用构建脚本。启动日志会输出所有可信私有 IPv4 候选，例如：

```text
LAN_ACCESS_URL interface=... url=http://<本机当前局域网IP>:8080
```

存在 Wi-Fi、网线、VPN 或虚拟网卡时会列出多个地址，不会盲目选择第一个；主机应把与玩家处于同一网络的地址发给其他人。玩家设备只需打开该地址。Windows 防火墙只应在“专用网络”中允许 Java 或 Docker 的应用端口，不应向公共网络开放。

带 PostgreSQL 的 Docker 方式：

```bat
copy .env.lan.example .env.lan
rem 修改 .env.lan 中的数据库密码
lan-docker-start.bat
```

`compose.lan.yml` 只映射应用端口。PostgreSQL 没有宿主机 `ports` 映射，并位于内部 Docker 网络，局域网设备不能直接访问5432端口。Docker 方式启用账户模式，首次打开网页会进入登录/注册页；不带数据库的 `lan-start.bat` 保留游客局域网模式。

直接刷新已注册的页面路径（包括 `/play`、`/friends`、`/admin`、`/rooms/{roomId}` 和
`/arena/{roomId}`）会由 Spring 转发到 `index.html`；`/api`、`/ws` 和静态资源路径
不参与 SPA 回退，缺失接口或资源仍返回 404。

### 更新后仍显示旧界面

`8080` 提供的是构建时打包的前端。修改源码或 `public` 后：

- Docker 模式：从根目录重新运行 `lan-docker-start.bat`，会重新构建并替换应用容器。
- JAR 模式：先停止旧服务，运行 `lan-build.bat`，再运行 `lan-start.bat`。`lan-start.bat` 找到有效 JAR 后会直接使用，不会因源码变动自动重新打包。
- 前端开发模式：运行 `npm run dev`，打开终端给出的 Vite 地址，默认端口 `5173`；不要把它与正式的 `8080` 页面混淆。

重建后可用 `Ctrl + F5` 刷新。更新会中断正在进行的内存牌局，应在无人游戏时操作；
正常 Compose 重建保留数据库卷。不要使用 `docker compose down -v` 更新服务，该选项会删除数据卷。

Docker 日志可在项目根目录查看：

```shell
docker compose --env-file .env.lan -f compose.lan.yml ps
docker compose --env-file .env.lan -f compose.lan.yml logs --tail 100 poker
```

### 开发模式

当前开发环境要求：

- JDK 21
- Maven 3.9+
- Node.js 24 LTS（Docker 构建使用 24；使用 Node.js 22 时至少为 22.13，满足当前 Vite 与 ESLint 的要求）
- npm 10+

验证环境：

```bash
java -version
mvn -version
node -v
npm -v
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

默认 HTTP 地址为 `http://localhost:8080/api/rooms`，WebSocket 地址为 `ws://localhost:8080/ws/poker`。持久化默认关闭，启动服务不要求本机安装 PostgreSQL，此时使用游客身份且不启用账户、钱包、排行榜和商城。

另开终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

默认前端地址为 `http://localhost:5173`。Vite 会把同源 `/api` 和 `/ws` 代理到 `localhost:8080`，并重写 WebSocket Origin，因此本地开发无需把后端 Origin 白名单设置为 `*`。`npm run dev` 已监听 `0.0.0.0`。生产代码始终使用相对 `/api`、`/ws`，不支持通过环境变量写入 `localhost`、私人 IP 或跨源后端地址。

### 启用 PostgreSQL 账户与历史持久化

先创建空数据库，再通过环境变量提供连接信息。不要把真实凭据写入仓库：

```bash
export POKER_DB_URL='jdbc:postgresql://localhost:5432/xidao_poker'
export POKER_DB_USERNAME='<username>'
export POKER_DB_PASSWORD='<password>'

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

`postgres` Profile 打开 `poker.persistence.enabled` 并读取环境变量。Flyway 不在启动线程中抢先连接数据库，而是在首次账户或历史用例访问数据库时按需迁移；手牌历史迁移或写入失败由有界异步写入器重试，实时牌局不会被历史队列阻塞。账户、钱包和买入事务需要数据库可用，失败时会明确拒绝对应操作，绝不在内存中伪造余额。

当前 V1–V12 迁移涵盖以下表：

- `game_record`：房间级游戏配置、时间范围和已保存手牌数
- `poker_user`：局域网玩家 ID 与最近显示名
- `hand_history`：公共牌、底池、奖金和行动完整性标记
- `hand_player`：座位、私有牌、起止筹码、投入、奖金与摊牌结果
- `game_action`：引擎接受的行动及行动后筹码状态
- `player_statistic`：参局数、获胜手数、累计投入和累计奖金
- `identity_account` / `account_session`：真实身份归属、BCrypt 密码哈希与仅存哈希的会话
- `account_wallet` / `wallet_ledger`：筹码、英魂结晶与不可变余额流水
- `daily_check_in`：按北京时间自然日去重的签到奖励
- `table_buy_in`：入桌买入托管、最终筹码和幂等离桌结算
- `redemption_code` / `redemption_claim`：兑换码摘要、有效期、全局限额与每账户核销记录
- `mail_message` / `account_mail`：管理员群发内容、玩家未读状态和附件领取状态
- `account_cosmetic`：通过奖励邮件或商城购买取得的装扮库存
- `cosmetic_purchase`：商城购买请求、价格快照与幂等结果
- `account_cosmetic_loadout`：每个账户各装扮槽位当前装备项
- `friendship` / `friend_operation_request`：好友申请、双向好友关系与命令去重
- `admin_operation_request` / `admin_audit_log`：管理员操作去重和审计

同一手使用 `(game_id, hand_id)` 唯一键。重复异步提交返回 `ALREADY_EXISTS`，不会重复插入行动或累加玩家统计；一手牌的主记录、玩家、行动与统计在同一事务中提交。

## 目录结构（当前与规划）

```text
Xidao-poker/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/xidao/poker/
│       │   ├── application/
│       │   │   ├── account/
│       │   │   ├── arena/
│       │   │   ├── command/
│       │   │   ├── history/
│       │   │   └── room/
│       │   ├── config/
│       │   ├── engine/
│       │   │   ├── action/
│       │   │   ├── arena/
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
│       │   ├── persistence/
│       │   │   ├── account/
│       │   │   └── history/
│       │   └── web/
│       │       ├── api/
│       │       ├── arena/
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
├── frontend/
│   ├── src/
│   │   ├── api/              # 游戏、账户与运营 HTTP 客户端
│   │   ├── arena/            # 载具模式协议类型
│   │   ├── components/       # 手牌、座位、行动栏与连接状态
│   │   ├── pages/            # 游戏大厅、对局、账户、好友、商城与后台
│   │   ├── store/            # Snapshot replace + Event 投影
│   │   ├── types/            # 与后端协议对齐的类型
│   │   └── ws/               # 重连、回放与心跳
│   ├── public/              # 可公开发布的 SVG 与原创合成音乐
│   ├── package.json
│   └── vite.config.ts
├── scripts/                 # 构建、启动与公开音乐生成
├── compose.lan.yml
├── ASSET_LICENSES.md
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
- [x] 真实身份、多游戏 ID、HttpOnly 会话与 BCrypt 密码
- [x] 双货币钱包、每日签到与三个 Top 3
- [x] 服务端权威商城、幂等购买、装扮库存与牌桌装备效果
- [x] 幂等牌桌买入托管、重连防重复扣款和离桌返还
- [x] 站内邮件、管理员群发奖励、兑换码核销和离桌净输赢提示
- [x] 好友申请、接受/拒绝/删除与会话心跳在线状态
- [x] 运营后台：账号、余额调整、密码重置及好友关系管理
- [x] Arena 十人房间、随机地图、技能、淘汰播报与自动续局
- [x] 公开版 SVG Logo、头像和合成 BGM；本地私人素材隔离
- [x] React 大厅、等待房间和牌桌 MVP
- [x] 引擎 / 应用层断线重连、旧连接隔离和观战等待下一手
- [x] WebSocket 30 秒宽限调度与 token / epoch 安全重连
- [x] React / Spring Boot 同端口 LAN 发布、SPA 路由回退与多网卡地址提示
- [x] Docker Compose 应用与内部 PostgreSQL 网络（数据库端口不向 LAN 映射）
- [x] 协议版本与构建版本握手检测
- [x] Backend Maven Test CI（前端 Job 预留且默认跳过）
- [x] LAN 容器化构建

## 安全与公平性说明

本项目当前定位为局域网娱乐和工程实践。持久点数只能作为 play-money：不提供充值、提现、现金价值、实物兑换或玩家间转账。任何涉及真实资金的部署都会引入额外的法律、合规、安全与审计要求，不在当前范围内。

请勿在 Issue、日志或提交中公开密码、数据库连接串、令牌或未公开玩家手牌。

## Contributing

项目处于 LAN Alpha 迭代阶段。提交功能前请先创建 Issue 或 Discussion 描述场景，特别是涉及游戏规则、WebSocket 协议、持久化模型或状态机变更时。

规则正确性优先于功能数量。任何规则修复都应附带回归测试。

## License

本项目代码使用 [MIT License](LICENSE)。公开素材的来源与许可边界见 [ASSET_LICENSES.md](ASSET_LICENSES.md)；本地备份素材不因放在项目目录中自动获得该许可。



## 载具竞技模式（Prototype 01）

`/arena` 提供一套原创的简易 2D 反射迷宫载具玩法，用于验证未来多游戏 IP 的实时联机底座：

```text
竞技大厅创建/加入房间
    → 最多 10 名驾驶员加入并准备
    → 房主开始回合
    → WASD / 方向键驾驶，Space 发射反弹弹丸
    → 单次命中淘汰，最后幸存者胜场立即 +1
    → 晶体消散与淘汰播报后，在线人数不少于 2 时自动生成地图并开始下一轮
```

- 移动、载具互撞、墙体碰撞、弹丸反射、命中和胜负全部由 Java 服务端以约 30 tick/s 裁决。
- 每轮由服务端生成一张 1800×1080 的 12×8 碎片化随机迷宫；通道按载具碰撞半径预留安全净空，拓扑不存在单一咽喉点，保证任意出生区域之间至少有两条内部独立路线。
- 每名玩家最多同时保有 5 发弹丸；弹丸命中目标或固定存在 5 秒后归还弹药。墙面反射不消耗弹药，首次反射后弹丸可伤害发射者。
- Canvas 客户端使用跟随摄像机和平滑绘制，并提供全图小地图；触屏设备提供方向和开火按钮。
- 回合开始后每隔 20 秒在安全可通行位置生成特殊技能：超载推进、快速射击或可抵挡一次命中的灵子护盾。
- 中途加入者先观战，下一轮才参战；断线有 20 秒重连窗口，过期移除后不会让回合卡死。
- 回合不显示 Victory 结算遮罩；3 秒清晰淘汰播报与转场后自动续局，在线人数不足 2 人时才返回准备阶段。
- 每个房间通过独立公平锁串行处理加入、输入、Tick、断线、重连和离开；客户端命令带 `requestId`，输入另带单调序号。
