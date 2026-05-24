# BrokerWallet AI Agent 工作报告：黄金票据预测市场、安全改造与联调

## 1. 项目来源与提交目标

- 原始项目：[HuangLab-SYSU/brokerwallet-academic](https://github.com/HuangLab-SYSU/brokerwallet-academic)
- 当前开发仓库：[wangdad007/brokerwallet-ai-agent](https://github.com/wangdad007/brokerwallet-ai-agent)
- 当前分支：`prediction-market`
- 上游基线：`upstream/master`，commit `09add95`
- 已完成安全改造提交：`69538b5 feat: secure gold market contract access`
- 已完成工作报告提交：`549a7c5 docs: add gold market security work report`
- 本报告更新时间：2026-05-25

本项目是在原始 BrokerWallet Android 钱包项目基础上扩展的 AI Agent 与黄金票据预测市场版本。原项目主要提供 BrokerChain 钱包、账户、转账、NFT、证明等基础能力；当前分支在不重写原钱包主体的前提下，新增了黄金行情、DeepSeek 投研助手、链上预测市场交互和 Remix 合约测试支持。

本轮改造的核心目标是：普通用户只参与官方验证市场，不在 App 内自由部署或创建合约；官方部署合约后，App 通过官方合约列表读取链上市场，并按具体 `contractAddress + gameId` 路由交易。

## 2. 相对原始项目的主要新增内容

### 2.1 黄金票据预测市场模块

新增 `app/src/main/java/com/example/brokerfi/xc/agent/gold/` 模块，核心文件包括：

- `BrokerChainClient.java`：封装 BrokerChain 网关请求，支持 `eth_call`、`eth_sendTransaction`、余额查询和地址推导。
- `GoldMarketSecurityPolicy.java`：控制官方合约地址、开发测试合约列表和 RPC 配置策略。
- `GoldMarketRepository.java`：App 与预测市场合约交互层，负责读取市场数量、读取市场详情、买入、卖出和领取奖励。
- `GoldNoteMarketActivity.java`：黄金票据页面，展示行情、AI 建议、市场列表、当前市场详情、倒计时、持仓和交易按钮。
- `GoldAdvisoryManager.java`：黄金行情与 AI 投研建议管理，统一首页和 AI 使用的行情来源。
- `AppExecutors.java`：黄金市场模块的后台线程和主线程回调工具。

### 2.2 Android XML 前端页面

`activity_gold_note_market.xml` 已改造成用户端交易页面：

- 展示黄金价格、涨跌幅、行情来源和更新时间。
- 保留 DeepSeek AI 投研助手入口。
- 展示“官方验证池 / 开发测试池”状态条。
- 展示链上市场列表，而不是只固定展示 `gameId = 1`。
- 展示当前市场详情、看涨/看跌概率、总池子、倒计时和我的持仓。
- 提供买入看涨、买入看跌、卖出和领取奖励入口。
- 已移除“创建市场”按钮，用户端不再负责创建市场。

新增或调整的前端资源包括：

- `bg_gold_market_status.xml`：正式版官方验证池样式。
- `bg_gold_market_status_debug.xml`：Debug 开发测试池样式。
- `bg_gold_market_panel.xml`：链上市场列表项样式。
- `custom_button_blue.xml`、`custom_button_background.xml`：按钮样式与钱包界面风格统一。

### 2.3 链上智能合约

新增 `contracts/PredictionMarket.sol`，用于 BrokerChain 测试链上的黄金预测市场。合约支持：

- 官方 Oracle 创建预测市场。
- 用户买入选项份额。
- 用户在到期前卖出份额。
- 官方 Oracle 到期结算。
- 官方 Oracle 触发流局退款。
- 用户按持仓领取奖励。
- 读取 `gameCount`、单个市场详情、用户份额和批量市场信息。

## 3. 安全与架构改造

### 3.1 用户端不再创建市场

本次重点处理“是否允许 App 用户自己部署或创建黄金博弈池”的安全问题。结论是：普通用户不应在正式 App 中自由部署、创建或切换任意合约。

原因包括：

- 恶意合约可能伪装成黄金市场，诱导用户转入 BKC。
- 用户自行创建市场会绕过 Oracle 规则、开奖规则和参数限制。
- 多个未登记合约会造成资金池、赔率和结算来源混乱。
- App 难以验证任意合约是否与官方规则一致。

因此当前方案改为：官方或测试人员在 Remix / Oracle 端部署合约并创建市场，App 只读取官方确认的合约列表，普通用户只参与交易和领取奖励。

### 3.2 官方合约列表与多市场聚合

`GoldMarketSecurityPolicy.java` 和 `GoldMarketRepository.java` 支持官方合约列表：

- Release 模式只读取代码中配置的官方合约地址。
- Debug 模式允许配置多个测试合约地址，每行一个。
- App 启动或刷新时逐个合约读取 `gameCount()`。
- 对每个合约按 `gameId` 递增读取 `getGameInfo(id)` 和用户持仓。
- 前端只展示进行中的市场；已到期且用户无持仓的市场会被隐藏。
- 每个市场对象保存自己的 `contractAddress`，交易时不会打到错误合约。

当前实现是轻量合约列表方案。后续正式化建议使用 `MarketRegistry` 合约：App 固定连接 Registry，官方每部署一个市场合约就注册到 Registry，App 自动读取官方合约列表。

### 3.3 合约权限加固

`PredictionMarket.sol` 已加固以下权限和边界：

- `createGame` 增加 `onlyOracle`，只有官方 Oracle/部署者可以创建市场。
- `resolveGame` 增加 `onlyOracle`，普通用户不能自行开奖。
- `triggerRefund` 增加 `onlyOracle`，普通用户不能随意触发退款。
- 增加 `MIN_GAME_DURATION`、`MAX_GAME_DURATION`、`MAX_OPTIONS`，限制市场参数。
- 增加 `validGame` 和选项合法性检查。
- 修正退款时按全市场份额比例计算，避免按单选项份额导致资金超付。
- 使用 `call` 转账并检查返回值，避免静默失败。
- 增加 `_durationToChainUnits`，兼容 BrokerChain 上 `block.timestamp` 可能以毫秒计数的问题。

### 3.4 移除客户端部署能力

App 不再打包预测市场部署字节码，也不在用户界面暴露创建市场入口。合约部署应通过 Remix、部署脚本、官方后台或治理流程完成。App 的职责是读取、展示和参与官方验证市场。

## 4. 交易流程与市场运行逻辑

### 4.1 买入看涨 / 看跌

用户点击“买入看涨”时，App 调用当前市场所属合约的：

```solidity
buyShares(gameId, 0)
```

用户点击“买入看跌”时，App 调用：

```solidity
buyShares(gameId, 1)
```

用户输入的 BKC 金额作为 `msg.value` 进入合约。合约会增加市场 `totalLiquidity`，根据虚拟储备池计算用户获得的份额，并写入：

```solidity
userShares[gameId][user][option]
```

因此“投入金额”和“份额数量”不是一回事。例如初始虚拟池为看涨 100 BKC、看跌 100 BKC 时，买入 0.1 BKC 看涨会得到约 0.2 份看涨票据。

### 4.2 卖出和结算

到期前用户可以卖出份额，合约按当前虚拟储备池价格计算可取回 BKC，并收取 2% 手续费。到期后由官方 Oracle 调用 `resolveGame` 确定胜出选项，胜方用户调用 `claimReward(gameId, option)` 按胜方份额比例瓜分池子。若无法结算，官方可以触发退款，所有持仓用户按份额比例退款。

## 5. Bug 修复与体验改进

### 5.1 本地 RPC 调用兼容

BrokerChain 本地 RPC 对 `eth_call` 和写交易字段要求较严格。本次为本地 `eth_call` 和 `eth_sendTransaction` 补齐 `gas`、`gasPrice`、`value` 等字段，并在写交易后等待 receipt，避免 App 交易提交后 UI 过早刷新导致池子和持仓看起来没有变化。

### 5.2 多合约市场读取

之前 App 固定读取单一合约的 `gameId = 1`，导致用户部署新合约后 App 仍然显示旧市场或“暂无进行中的市场”。现在 App 会从合约列表中聚合所有进行中的市场，并把交易路由到市场所属合约。

### 5.3 市场序号显示

前端不再直接显示链上 `#1`、`#2`。链上历史市场不会被物理删除，过期市场被隐藏后，App 使用当前列表的展示顺序显示“市场 1、市场 2”，避免用户看到 `#3`、`#4` 产生误解。

### 5.4 倒计时修复

BrokerChain 测试环境中 `block.timestamp` 可能是毫秒级，而 Solidity 常规环境通常是秒级。合约侧新增 `_durationToChainUnits`，App 侧倒计时也兼容秒级和毫秒级 deadline，避免出现 `493840987:02:30` 这类异常倒计时。

### 5.5 持仓小数显示修复

预测市场份额按 `1e18` 精度存储。之前前端用 `%.0f` 显示份额，导致 `0.2` 份被四舍五入显示成 `0`。现在改为 `BigDecimal` 格式化，最多保留 6 位小数，并在极小正数时显示 `<0.000001`，避免用户误以为没有持仓。

### 5.6 AI 投研助手修复

保留 DeepSeek AI 投研助手，但删掉与当前黄金票据场景无关的“经济扫描 / NFT 市场 / 深度分析”入口。AI 现在聚焦黄金票据建议，并使用同一份黄金行情和链上市场数据生成 prompt，避免 AI 金价和首页金价不一致。

同时改进 `DeepSeekClient`：

- 增加连接和读取超时。
- 读取 HTTP error stream。
- 对无 API Key、401、429、5xx 等错误给出更明确提示。
- 修复 AI Assistant 一直显示“思考中”的 loading 替换逻辑。

## 6. 测试覆盖

`GoldMarketRepositoryTest.java` 目前覆盖 20 个用例，主要包括：

- BKC 金额到 wei 的精确解析。
- 非法金额输入拒绝。
- `claimReward(gameId, optionId)` ABI 参数。
- 本地 RPC `eth_call` 和写交易字段。
- `gameCount()` ABI。
- Release 模式只使用官方合约和官方 RPC 策略。
- Debug 模式允许开发测试合约列表。
- App 不打包预测市场部署字节码。
- 合约源码必须限制创建市场权限。
- XML 前端不暴露创建市场按钮。
- DeepSeek 投研助手保留并聚焦黄金研究。
- 市场模型携带 `contractAddress`，交易按选中市场路由。
- 倒计时兼容秒级和毫秒级 deadline。
- 持仓小数显示不丢失。
- 黄金行情携带来源和更新时间。

## 7. 最新验证记录

2026-05-25 已执行并通过：

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests com.example.brokerfi.xc.agent.gold.GoldMarketRepositoryTest --rerun-tasks
.\gradlew.bat :app:assembleDebug --rerun-tasks
```

验证结果：黄金市场单元测试通过，debug APK 构建通过。Gradle 仍有 Java 8 source/target 在 JDK 21 下的弃用警告，不影响当前构建结果。

## 8. 当前使用和部署建议

### 8.1 测试环境

1. 在本地启动 BrokerChain 节点和钱包 RPC。
2. 使用 Remix 部署新版 `contracts/PredictionMarket.sol`。
3. 用部署者账号调用 `createGame` 创建黄金市场。
4. 在 Debug App 中长按黄金价格区域，打开“配置官方合约列表”。
5. 填入 RPC 地址和一个或多个测试合约地址，每行一个。
6. 刷新黄金票据页面，App 会自动读取这些合约下的进行中市场。
7. 选择市场后测试买入、卖出、到期结算、退款和领取奖励。

### 8.2 正式环境

1. 将官方部署合约地址写入 `GoldMarketSecurityPolicy.DEFAULT_CONTRACT_ADDRESS`，或后续替换为 `MarketRegistry` 地址。
2. Release App 不开放合约地址配置和创建市场入口。
3. 普通用户只能参与官方验证市场。
4. Oracle/管理员负责创建市场、结算和退款。
5. App 只负责展示官方市场列表、提交交易、显示持仓和领取奖励。

## 9. 后续建议

- 实现 `MarketRegistry` 合约，替代 Debug 手动合约列表。
- 为 `PredictionMarket.sol` 增加 Solidity 单元测试，覆盖退款比例、开奖时机、非法选项、重复领取等场景。
- 将 `.gradle`、`app/build` 等构建产物从 Git 跟踪中移除，降低工作区噪音。
- 将 Java source/target 或 Gradle toolchain 策略统一，减少 JDK 21 下的弃用警告。
- 为 App 增加更完整的端到端测试脚本，自动部署合约、创建市场、买入、结算和领取奖励。
