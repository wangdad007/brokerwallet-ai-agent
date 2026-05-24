# BrokerWallet AI Agent 工作报告：黄金票据预测市场与安全改造

## 1. 项目来源与提交目标

- 原始项目：[HuangLab-SYSU/brokerwallet-academic](https://github.com/HuangLab-SYSU/brokerwallet-academic)
- 当前开发仓库：[wangdad007/brokerwallet-ai-agent](https://github.com/wangdad007/brokerwallet-ai-agent)
- 当前分支：`prediction-market`
- 上游基线：`upstream/master`，commit `09add95`
- 本次安全改造提交：`69538b5 feat: secure gold market contract access`

本项目是在原始 BrokerWallet Android 钱包项目基础上扩展的 AI Agent 与黄金票据预测市场版本。原项目主要提供 BrokerChain 钱包、账户、转账、NFT、证明等基础能力；当前分支在不重写原钱包主体的前提下，新增了黄金市场 AI 分析、链上预测市场交互和智能合约测试支持。

## 2. 相对原始项目的主要新增内容

### 2.1 新增黄金票据预测市场模块

新增 `app/src/main/java/com/example/brokerfi/xc/agent/gold/` 模块，核心文件包括：

- `BrokerChainClient.java`：封装 BrokerChain 网关请求，支持 `eth_call`、`eth_sendTransaction`、余额查询和地址推导。
- `GoldMarketRepository.java`：App 与链上预测市场合约交互层，负责读取市场信息、买入、卖出、创建测试市场和领取奖励。
- `GoldNoteMarketActivity.java`：黄金票据市场页面，展示黄金价格、AI 建议、市场池、倒计时、持仓和交易按钮。
- `GoldAdvisoryManager.java`：黄金行情与 AI 投资建议管理。
- `AppExecutors.java`：黄金市场模块的后台线程和主线程回调工具。

### 2.2 新增 Android XML 前端页面

新增并改造 `activity_gold_note_market.xml`，实现黄金票据预测市场页面：

- 黄金价格展示区域。
- AI 投资建议卡片。
- 官方验证池 / 开发测试池状态条。
- 当前市场池、看涨/看跌概率、总池、倒计时。
- 买入、卖出、领取奖励操作入口。
- Debug 模式下的测试创建市场入口。

新增两个状态背景：

- `bg_gold_market_status.xml`：正式版官方验证池样式。
- `bg_gold_market_status_debug.xml`：Debug 开发测试池样式。

### 2.3 新增链上智能合约

新增 `contracts/PredictionMarket.sol`，用于 BrokerChain 测试链上的黄金预测市场。合约支持：

- 创建预测市场。
- 买入选项份额。
- 卖出份额。
- Oracle 到期结算。
- 到期流局退款。
- 按持仓领取奖励。
- 批量读取市场信息。

## 3. 本次安全改造内容

本次重点处理“是否允许 App 用户自己部署黄金博弈池”的安全问题。结论是：普通用户不应在正式 App 中自由部署或切换任意合约，正式版只应连接官方验证合约；部署、合约地址配置和测试创建市场只保留在 Debug/测试模式。

### 3.1 App 端新增安全策略

新增 `GoldMarketSecurityPolicy.java`：

- 固定官方合约地址入口。
- Release 模式忽略本地保存的自定义合约地址。
- Release 模式忽略自定义 RPC URL。
- 只有 Debug 模式允许开发测试工具。

这样可以避免普通用户被引导到未审计、恶意或权限不透明的预测市场合约。

### 3.2 黄金市场页面同步安全状态

`GoldNoteMarketActivity.java` 做了对应调整：

- Release 模式显示“官方验证池”。
- Release 模式隐藏“创建市场”按钮。
- Release 模式长按黄金价格不会打开合约配置，只提示正式版只能连接官方验证市场。
- Debug 模式显示“开发测试池”，可配置测试合约和 RPC，可创建测试市场。

XML 前端也新增了安全状态条，让用户能直接看到当前连接模式。

### 3.3 合约权限加固

`PredictionMarket.sol` 加固了以下权限和边界：

- `createGame` 增加 `onlyOracle`，只有官方 Oracle/部署者可以创建市场。
- `resolveGame` 增加 `onlyOracle`，普通用户不能自行开奖。
- `triggerRefund` 增加 `onlyOracle`，普通用户不能随意触发退款。
- 增加 `MIN_GAME_DURATION`、`MAX_GAME_DURATION`、`MAX_OPTIONS`，限制市场参数。
- 增加 `validGame` 和选项合法性检查。
- 修正退款时按全市场份额比例计算，避免按单选项份额导致资金超付。
- 使用 `call` 转账并检查返回值，避免静默失败。

### 3.4 移除 App 内部署字节码

删除 App 端打包部署字节码的做法，避免把“任意用户从客户端部署预测市场合约”的能力放进正式客户端。合约部署应通过 Remix、部署脚本、官方后台或治理流程完成，App 只连接官方确认的合约。

## 4. Bug 修复与测试补充

### 4.1 修复 SecretPhraseActivity lint 问题

`activity_secret_phrase.xml` 中存在 `android:onClick="nextAction"`，但 `SecretPhraseActivity` 缺少对应方法，导致 lint 报错。本次补充 `nextAction(View)`，恢复助记词确认流程跳转。

### 4.2 修复领取奖励 ABI 不匹配

合约函数为：

```solidity
claimReward(uint256 _gameId, uint8 _option)
```

App 原逻辑只传 `gameId`，会导致调用签名不匹配。本次改为根据中奖选项或退款持仓选项调用 `claimReward(gameId, optionId)`。

### 4.3 修复金额解析风险

买入和卖出输入由 `double` 转换改为 `BigDecimal` 精确解析到 wei，避免浮点精度问题和非法输入崩溃。

### 4.4 新增单元测试

新增 `GoldMarketRepositoryTest.java`，覆盖：

- BKC 金额到 wei 的解析。
- 非法金额输入拒绝。
- `claimReward(gameId, optionId)` ABI 参数。
- Release 模式只使用官方合约和官方 RPC 策略。
- Debug 模式允许开发测试覆盖。
- App 不打包预测市场部署字节码。
- 合约源码必须限制创建市场权限。

## 5. 验证记录

已执行并通过：

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
.\gradlew.bat --console=plain --no-daemon :app:testDebugUnitTest
.\gradlew.bat --console=plain --no-daemon :app:lintDebug
.\gradlew.bat --console=plain --no-daemon :app:assembleDebug
npx --yes solc@0.8.26 --bin contracts/PredictionMarket.sol
git diff --check
```

其中 Android 单元测试共 9 个测试通过；lint 和 debug APK 构建通过；Solidity 合约可由 `solc 0.8.26` 编译。

## 6. 当前使用和部署建议

### 6.1 测试环境

1. 使用 Remix 部署新的 `contracts/PredictionMarket.sol`。
2. 用部署者账号调用 `createGame` 创建 `gameId = 1`。
3. 在 Debug App 中长按黄金价格区域，配置测试合约地址和 RPC。
4. 验证买入、卖出、开奖、退款和领取奖励流程。

### 6.2 正式环境

1. 将官方部署合约地址写入 `GoldMarketSecurityPolicy.DEFAULT_CONTRACT_ADDRESS`。
2. Release App 不开放合约地址配置和创建市场入口。
3. 普通用户只能参与官方验证池。
4. Oracle/管理员负责创建市场、结算和退款。

## 7. 后续建议

- 将 `GoldMarketSecurityPolicy.DEFAULT_CONTRACT_ADDRESS` 改为正式部署地址。
- 增加 `GoldPoolFactory` 或 Registry 合约，未来通过工厂合约创建和登记官方池。
- App 从 Factory/Registry 事件读取池子列表，而不是长期固定 `GOLD_GAME_ID = 1`。
- 将 `.gradle`、`app/build` 等构建产物从 Git 跟踪中移除，降低工作区噪音。
- 补充更多合约层测试，例如退款比例、开奖时机、非法选项、重复领取等。
