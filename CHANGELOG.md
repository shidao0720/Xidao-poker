# Changelog

本文件记录 Xidao Poker 的重要变更。

格式参考 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，项目版本将遵循 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)。

## [Unreleased]

### Added

- 添加独立公开发布副本生成脚本、精确素材历史排除清单和发布流程文档；保留原始开发历史及私有 Bundle，校验公开副本路径、对象库和源码树，不自动推送。
- 添加公开发布素材清单 `ASSET_LICENSES.md`、原创 Xidao Poker SVG Logo、12 个替代摄影头像的原创抽象 SVG，以及由确定性振荡器生成、不含第三方采样的大厅循环音乐。
- 添加可重复生成公开版背景音乐的 `scripts/generate-public-bgm.mjs`。
- Added persistent friend requests, accept/reject/remove flows, and friend-only online presence backed by per-session heartbeats. Multi-device sessions are evaluated independently and presence expires after missed heartbeats.
- Extended Operations with administrator account lookup, audited chip/crystal adjustments, BCrypt password reset with old-session revocation, and friendship graph creation/removal. Password plaintext and password hashes are never exposed to the UI or logs.
- Added an administrator-only Operations page with live account/mail/cosmetic metrics, redemption-code creation and enable/disable controls, and catalog-backed global reward mail composition. Administrator mutations carry idempotent `requestId` values; redemption plaintext is returned once and only its SHA-256 digest is stored.

- 添加第二个可游玩模式“逆相载具竞技”：原创 2D 反射迷宫、最多 10 人房间、准备/房主开局、载具驾驶、墙体与载具碰撞、反弹弹丸、单次命中淘汰和回合胜负结算。
- 添加 1800×1080 双通路随机迷宫生成器，每轮重新生成 12×8 碎片化地图；生成算法拒绝单一咽喉点，保证所有出生区域之间至少存在两条内部独立路线，并按载具碰撞半径留出稳定通行净空。
- 添加每 20 秒服务端随机技能投放：超载推进、快速射击和一次性灵子护盾；技能位置、拾取、持续时间和护盾命中均由服务端裁决。
- 添加跟随玩家的战场摄像机、全图小地图、技能投放倒计时、地图技能标记和玩家生效状态反馈。
- 添加纯 Java `ArenaSession`、串行 `ArenaRoomRuntime`、30 tick/s 服务端物理循环、`/api/arena/rooms` 房间目录和 `/ws/arena` 实时协议；输入只作为意图提交，命中与胜负均由服务端裁决。
- 添加载具模式断线重连、连接 epoch 防陈旧、20 秒宽限、命令 `requestId` 与输入序号双重去重，以及等待重连成员阻止空房误清理的生命周期约束。
- 添加 React 载具大厅、Canvas 战场、平滑插值、键盘/触屏控制、驾驶员计分板、观战、淘汰播报和晶体碎裂消散阵亡效果，并接入同端口 LAN 发布路径。
- 添加载具连续回合：最后存活者由服务端立即累计胜场，取消 Victory 遮罩，在短暂淘汰转场后为至少两名在线玩家自动生成地图并开始下一轮。
- 修复弹丸反射后同一物理帧仍沿用旧位移、连续重复碰墙并提前消失的问题；弹丸改为固定存在 5 秒，每名玩家最多同时发射 5 发，命中或消失后即时恢复弹药，首次墙面反射后允许自伤。
- 添加载具引擎和房间运行时自动测试，覆盖十人容量、开局资格、弹丸淘汰、载具互撞、离开结算、重复命令、旧连接、清理 TTL 和 300 回合连续运行不变量。
- 添加正式“Spirit Archive”商城：服务端提供 14 件权威商品目录，覆盖头像框、牌背、称号、局内按钮效果、结算演出和组合珍藏；“主页风格”分类暂时保留为空。
- 添加真实英魂结晶购买、`requestId` 幂等防重复扣款、组合商品按缺失内容发放、已拥有校验，以及购买流水与装扮库存同事务提交。
- 添加账户装扮栏与装备接口；头像框、牌背、称号、局内按钮效果和胜利演出会在服务端认证 WebSocket 时注入牌桌快照，客户端不能伪造未拥有外观。
- 添加商城商品详情、分类侧栏、搜索排序、已拥有/已装备状态、收藏预览和购买/装备交互；局内按钮效果拥有独立侧栏入口。
- 添加站内邮件中心与小信封未读入口；管理员可向全部现有玩家发送公告、通知，以及附带筹码、英魂结晶或皮肤的奖励邮件。
- 添加事务型兑换码系统：兑换码以 SHA-256 摘要保存、按账户防重复核销，奖励与钱包流水在同一事务内提交；内置测试码 `FATE-STAY-POKER` 奖励 100 英魂结晶。
- 添加权威离桌盈亏提示：大厅查询 `table_buy_in` 托管结算，在中途离桌尚未完成当前手时显示等待状态，结算后展示带入、带回与净输赢。
- 添加非牌桌页面连续循环背景音乐；登录、大厅、排行榜、商城、个人主页和邮件页共享播放状态，进入牌桌自动暂停且不显示播放开关。
- 添加 PostgreSQL 真实身份体系：真实姓名、游戏 ID 与密码共同验证；同一真实身份可持有多个游戏 ID，一个游戏 ID 只能归属一个真实身份。
- 添加双货币钱包：新身份初始 10,000 筹码、北京时间每日签到 500 筹码、按 `10 筹码 = 1 英魂结晶` 单向凝结，商城只接受英魂结晶。
- 添加牌桌买入托管：首次入座原子扣除买入，重连不重复扣款，同一真实身份同时只能占用一个资金席位；主动离开、断线到期和 All-in 后离桌统一按服务端最终筹码幂等返还。
- 添加胜利手数、累计赢得筹码、单手最高净收益三个 Top 3 排行榜。
- 添加 React 登录/注册门、账户钱包栏、签到、英魂凝结、排行榜和商城页面；无 PostgreSQL 模式继续保留游客流程。
- 添加 Flyway V2/V3 迁移，覆盖身份、会话、双货币钱包、流水、签到和牌桌托管，并增加 Docker PostgreSQL 事务与幂等测试。
- 添加前后端同端口 LAN 发布模式：React 生产资源随 Spring Boot JAR 打包，页面、`/api` 与 `/ws` 统一由应用端口提供。
- 添加 `lan-build.bat`、`lan-start.bat` 和 Docker 一键启动入口，第二台局域网设备可直接访问启动日志列出的地址。
- 添加受限 SPA 路由回退，仅将牌桌页面路由转发到 `index.html`，明确排除 `/api`、`/ws` 与静态资源路径。
- 添加多网卡 LAN IPv4 枚举与候选排序，启动时列出全部可用地址并降低 VPN、容器及虚拟网卡优先级。
- 添加多阶段生产镜像与 `compose.lan.yml`；PostgreSQL 不映射宿主机端口，并隔离在 Docker 内部网络。
- 添加协议版本与构建版本握手校验，客户端请求和服务端信封均携带版本元数据，避免旧缓存前端静默连接不兼容后端。
- 将 16 条 LAN Hardening Constraints 纳入开发约束，覆盖房间串行化、计时器防陈旧、状态正交、幂等、安全快照、日志与筹码守恒等要求。
- 为正式牌桌行动按钮加入预览方案中的点击爆光反馈：弃牌使用赤红斩光、过牌使用蓝色波光、跟注使用金色放射光，Bet / Raise 使用青蓝灵子光，并与后续筹码飞行动画并行播放。
- 初始化 Java 21、Spring Boot 3 和 Maven 后端工程。
- 添加 Spring Boot 应用入口，使 `mvn verify` 能够生成可执行 Jar。
- 实现标准 52 张牌组，并支持注入随机种子以复现测试牌局。
- 实现五张牌牌型判断与七选五最优组合选择。
- 支持 High Card、Pair、Two Pair、Three of a Kind、Straight、Flush、Full House、Four of a Kind 和 Straight Flush。
- 实现 `A-2-3-4-5` Wheel 顺子和完整 kicker 比较。
- 将玩家状态拆分为 `ConnectionStatus`、`SeatStatus` 与 `HandStatus` 三个正交维度；`PlayerStatus` 仅保留为旧 UI 兼容投影。
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
- 添加 `requestId` 幂等缓存、连接 ID / epoch 校验、`handId` / `turnId` 防延迟行动与旧手牌开始命令保护。
- 添加默认保留 512 个事件的有限回放窗口、1,024 条命令缓存和 1,024 条出站消息上限。
- 添加服务端 `ActionOptions`，提供跟注额、最小 Bet / Raise-to 与最大下注范围。
- 添加重连时主动生成的查看者专属 `GameSnapshot`、精确连接 ID / epoch 绑定，以及广播失败后的完整快照恢复。
- 添加 `GET/POST/DELETE /api/rooms` Spring HTTP 大厅 API、参数校验和稳定错误响应。
- 添加 `/ws/poker` 原生 WebSocket 适配器以及统一客户端/服务端消息信封。
- 添加 WebSocket 握手身份绑定、32 字节随机重连令牌、成功重连令牌轮换和旧 Socket 关闭。
- 添加默认 30 秒断线宽限调度；过期任务通过连接 epoch 防止删除新连接。
- 添加统一 `RoomTimerCommand` 模型，将断线到期、回合超时和空房清理全部送入同一个房间串行执行路径，并使用 `roomId + handId + turnId` 拒绝陈旧任务。
- 添加默认 30 秒服务端回合计时器；超时后只依据服务端 `legalActions()` 自动 Check 或 Fold。
- 添加同源 WebSocket 默认策略、可配置可信 Origin、16 KiB 消息上限与并发发送保护。
- 添加仅供服务端历史归档使用的 `CompletedHandSnapshot`，保存所有参与者私有牌、起止筹码、投入、奖金、底池和摊牌结果，不复用网络查看者 Snapshot。
- 添加应用层 `HandHistoryPublisher` / `HandHistoryRepository` 端口、默认 256 容量的有界异步队列、重试退避和优雅关闭。
- 添加 PostgreSQL 历史适配器和 Flyway V1 迁移，覆盖 `game_record`、`poker_user`、`hand_history`、`hand_player`、`game_action` 与 `player_statistic`。
- 添加 `(game_id, hand_id)` 幂等保存、整手事务、JSONB 公共牌 / 底池 / 奖金 / 私有牌以及玩家统计更新。
- 添加 `postgres` Profile 与环境变量数据库配置；默认关闭历史持久化，启用后数据库离线也不会阻止实时 Web 服务启动。
- 添加 Testcontainers PostgreSQL 16 合约测试，验证迁移、JSONB、重复写入和子记录失败后的事务回滚；升级 Testcontainers 至 1.21.4 以兼容较新的 Docker Engine API。
- 添加 SLF4J 结构化应用命令日志和发送失败日志。
- 正式建房与连续手牌使用 `SecureRandom` 洗牌，同时保留仅供测试的确定性 seed 入口。
- 添加 135 项引擎、房间应用层、HTTP/WebSocket、同端口发布、历史持久化与故障恢复测试，包括真实 Tomcat WebSocket 升级、PostgreSQL 合约测试、200 组确定性随机边池场景和 1,000 手自动对局不变量验证。
- 添加项目 README，记录架构决策、工程取舍、调试策略、测试策略与 Git 工作流。
- 添加 MIT License。
- 添加 GitHub Actions CI，自动运行后端 Maven Test，并预留默认跳过的前端 Job。
- 添加 React 19、TypeScript、Vite、Zustand 和 React Router 前端工程及 npm 锁文件。
- 添加局域网大厅、创建房间、房间轮询、等待准备、房主开局和最多十席牌桌界面。
- 添加查看者安全的手牌 / 公共牌显示、服务端驱动行动栏、下注范围输入和观战状态提示。
- 添加原生 WebSocket 客户端，支持 token / epoch 会话级保存、自动重连、心跳、事件序号检测、有限回放和 Snapshot 恢复。
- 添加前端 Snapshot 整体替换、增量投影、序号缺口及观察者防卡局测试。
- 添加与主 React 程序隔离的前端视觉实验室，提供四套蓝色主题、`Fate stay poker` 透明 Logo 以及四种可切换的大厅扑克电波背景。
- 添加适用于 Java、Maven、Node、Vite、IDE、日志和本地密钥的 `.gitignore`。
- 添加 `.gitattributes`，统一跨平台文本行尾并标记常见二进制资源。

### Changed

- 审查并更新 README：补充 Docker 首次启动、现有页面与权限、管理员初始化、数据卷与 Git 边界、旧界面重建排查；同步好友/Arena 功能、数据库迁移、依赖要求和已有测试报告口径。
- 登录页与大厅的公开版 Logo 采用 02「月蚀字徽」，移除右上角粉色小三角，保留银白交错字徽、蓝色弧线与 XIDAO / POKER 字样。
- 公开构建不再依赖设计预览、摄影头像、旧主题 Logo、牌面样图或授权未确认的音乐；这些本地素材迁移到 Git/Docker 均忽略的备份目录，主程序资源引用全部切换到公开安全替代项。
- 账户模式下 HTTP 与 WebSocket 身份由 HttpOnly、SameSite=Strict 会话 Cookie 认证；服务端使用数据库游戏 ID 覆盖握手查询中的自报身份，原始会话令牌只返回 Cookie 且日志不记录。
- `PLAYER_LEFT` 增加服务端最终筹码字段，资金结算监听器只以该权威事件返还托管筹码；临时数据库失败会使用稳定请求号重试。
- 创建/删除房间在账户模式下要求登录，游客模式保持原有局域网行为。
- 生产前端固定使用同源相对 `/api` 与 `/ws` 地址，不再允许把 localhost 或局域网 IP 编译进前端产物；Vite 的 localhost 代理仅用于开发模式。
- 空房间清理期限由 20 秒调整为 45 秒，并在启动时校验其严格长于 30 秒重连宽限，避免重连等待中的玩家被误判为空房。
- 明确持久点数仅为 play-money，不具备现金价值，不支持充值、提现、实物兑换或玩家间转账。
- Fate 胜负裁决播报最长保留 5 秒，玩家可随时点击右上角关闭并立即进入筹码分发；下注与结算筹码粒子延长单颗飞行时间，并在完整动画周期内等间隔逐颗发射，形成连续筹码流。
- 重排 Fate 结算赢家栏：左侧保留赢家身份，右侧直接展示牌型和服务端评估出的最佳五张组合，并逐张标明来自手牌或公共牌；未摊牌结算则在同一区域展示公开手牌。
- 跟注/下注/加注到总底池、总底池到赢家筹码框的粒子轨迹改为 GPU 加速的连续直线位移，移除中途弧线关键帧和数字结算缓动，使粒子与筹码数字从起点到终点持续运动。
- 将正式牌桌的跟注反馈改为金红实体筹码，将 Bet / Raise 反馈改为青蓝灵子筹码与脉冲；两套效果均按实际页面坐标飞入总底池。结算裁决播报结束后，底池筹码飞向各赢家姓名框，底池同步递减至 0、赢家筹码同步递增，动画按底池大小缩放并限制在 0.5–2 秒。
- 将结算播报切换为 Fate「Grail Verdict」样式，使用 `Victory / Defeat` 胜负标题、术式圆环与持续金红粒子；修复隐藏状态提前消耗结算和 All-in 动画、行动切换时按钮粒子被卸载的问题，分别呈现跟注的金色筹码轨迹与加注的灵子脉冲，并恢复自己手牌旁的“点击翻牌”提示。
- 将主程序大厅切换为“反向干扰”扑克电波主题，将实际牌桌切换为“圣杯裁决”主题，并接入洗牌牌堆、手牌与公共牌飞入、点击或上滑翻牌、灵子脉冲 All-in 及服务端结算展示；同时移除页面中的营销文案、操作教学和重复提示文字。
- 将牌桌座位改为以当前查看者为底部基准的相对环形布局，按实际玩家数量均匀分布最多十人；同时缩小玩家信息框并放大公共牌、手牌和自己的手牌。
- 移除容易误触的独立“全下”按钮；当服务端允许 `ALL_IN` 且下注滑块达到最大值时，前端发送无金额的 `ALL_IN` 意图，普通跟注与非最大 Bet / Raise 行为保持不变。
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

- 补充站点 favicon 配置，避免浏览器对缺失 `/favicon.ico` 的自动请求持续产生无关 404，干扰真实网络故障排查。
- 修复已登录玩家使用中文或其他非 ASCII 游戏 ID 时，载具竞技 WebSocket 在读取服务端账户身份前错误返回 400 的问题；认证模式现完全以服务端账户资料为准。
- 修复筹码粒子在直线位移中段因 `rotateY` 转到背面并被 `backface-visibility` 隐藏、看起来突然消失的问题；飞行旋转改为全程可见的平面旋转。
- 修复前端热更新后仍连接旧后端进程时，旧版快照缺少 `revealedHands` 字段导致结算组件调用 `.find()` 白屏的问题；Snapshot 接收边界现会将缺失字段归一化为空列表，重启后端前也能安全降级展示。
- 修复结算播报缺少赢家牌型、非赢家客户端偶尔收不到赢家手牌的问题；服务端现随摊牌/结算事件和查看者快照发送权威牌型、最佳五张牌及公开手牌，前端只负责展示。未发满五张公共牌便因弃牌结束时明确显示“未摊牌获胜”，不会伪造牌型。
- All-in 全屏播报除自动淡出外，现在点击屏幕任意位置也会立即淡出。
- 修复 Heads-up 翻牌前小盲补齐大盲后，大盲只显示过牌和全下、无法选择正常加注的问题；大盲现在保留 Check / Raise 选择权，最小 Raise-to 按当前下注额加一个完整大盲增量计算。
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
