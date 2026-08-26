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
- 添加 54 个游戏引擎单元与流程测试。
- 添加项目 README，记录架构决策、工程取舍、调试策略、测试策略与 Git 工作流。
- 添加 MIT License。
- 添加 GitHub Actions CI，自动运行后端 Maven Test；前端 Job 在模块不存在时自动跳过。
- 添加适用于 Java、Maven、Node、Vite、IDE、日志和本地密钥的 `.gitignore`。
- 添加 `.gitattributes`，统一跨平台文本行尾并标记常见二进制资源。

### Changed

- 将玩家的 Folded、All-in、Disconnected、Spectator 和 Busted 状态统一到单一枚举，减少冲突状态。
- 将底池逻辑移动到独立的 `engine.pot` 模块。
- 将下注合法性判断从轮转逻辑中抽离到统一验证层。
- 将观察者限制在 `GameSession` 层，当前 `Hand` 只接收经过资格筛选的参与者。
- 掉线玩家保留 `DISCONNECTED` 生命周期；当前手资格独立处理，不再伪装成观察者。

### Fixed

- 明确 Check 与 Call 的互斥条件，不使用强制最小跟注逻辑。
- 弃牌玩家的投入继续保留在底池中，但不再具备获奖资格。
- 修复观察者、破产、All-in 或离线玩家仍可能成为当前行动者并卡住牌局的问题。
- 修复所有玩家均无法行动时状态机不能自动发完公共牌并结算的问题。
- 修复 Short All-in 可以通过 `RAISE` / `ALL_IN` 绕过未重新开放加注权的问题。
- 修复多个 Short All-in 累计达到完整加注额后仍不能重新加注的问题。
- 修复合法行动列表会展示服务端随后拒绝的短额 Bet / Raise。
- 修复非当前行动者掉线弃牌后，存在无人有资格的底池层而导致结算失败的问题。
- 修复大盲短码 All-in 时错误降低 Preflop 跟注基准的问题。

## Release process

发布新版本时：

1. 将 `[Unreleased]` 中的变更移动到带日期的版本标题，例如 `## [0.1.0] - 2026-09-01`。
2. 为新的 `[Unreleased]` 创建空的 Added、Changed、Deprecated、Removed、Fixed 和 Security 小节。
3. 确认 CI 全部通过，并更新 README 中的当前状态与测试基线。
4. 创建 Git 标签，例如 `v0.1.0`，再发布对应 GitHub Release。
