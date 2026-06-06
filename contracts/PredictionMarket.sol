// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract PolymarketGold is Ownable, ReentrancyGuard {

    struct Game {
        uint256 id;
        string desc;
        string condition;
        string avatarUrl;
        string detailedInfo;
        string[] optionNames;
        uint256 totalPool;     // 总资金池 (wei)
        bool isResolved;
        uint8 winningOption;
        uint256 deadlineSec;
        bool isRefunded;

        // FPMM (恒定乘积做市商) 核心储备金
        uint256 reserveYES; // 0号选项储备
        uint256 reserveNO;  // 1号选项储备
    }

    uint256 public gameCount;
    mapping(uint256 => Game) public games;

    // gameId => user => optionId => 持有的 YES/NO 币数量
    mapping(uint256 => mapping(address => mapping(uint8 => uint256))) public userShares;

    event GameCreated(uint256 indexed gameId, string desc, uint256 liquidity);
    event SharesBought(uint256 indexed gameId, address indexed buyer, uint8 optionId, uint256 amountIn, uint256 sharesOut);
    event SharesSold(uint256 indexed gameId, address indexed seller, uint8 optionId, uint256 sharesIn, uint256 amountOut);
    event GameResolved(uint256 indexed gameId, uint8 winningOption);
    event RewardClaimed(uint256 indexed gameId, address indexed user, uint256 reward);

    constructor() Ownable(msg.sender) {}

    /**
     * @dev 内部工具：计算平方根，用于二次方程求解
     */
    function sqrt(uint256 y) internal pure returns (uint256 z) {
        if (y > 3) {
            z = y;
            uint256 x = y / 2 + 1;
            while (x < z) {
                z = x;
                x = (y / x + x) / 2;
            }
        } else if (y != 0) {
        z = 1;
    }
    }

    /**
     * @dev 创建博弈池 (必须注入初始流动性 msg.value)
     */
    function createGame(
    string memory _desc,
    string memory _condition,
    string memory _avatarUrl,
    string memory _detailedInfo,
    string[] memory _optionNames,
    uint256 _durationSec
    ) external payable {
    // Polymarket 的 AMM 数学模型最适合二元市场
        require(_optionNames.length == 2, "Polymarket AMM requires exactly 2 options (YES/NO)");
        require(msg.value > 0, "Must provide initial AMM liquidity");

        gameCount++;
        Game storage newGame = games[gameCount];
        newGame.id = gameCount;
        newGame.desc = _desc;
        newGame.condition = _condition;
        newGame.avatarUrl = _avatarUrl;
        newGame.detailedInfo = _detailedInfo;
        newGame.optionNames = _optionNames;
        newGame.deadlineSec = block.timestamp + _durationSec;

        newGame.totalPool = msg.value;

        // 初始状态：池子生成等量的 YES 币和 NO 币
        newGame.reserveYES = msg.value;
        newGame.reserveNO = msg.value;

        emit GameCreated(gameCount, _desc, msg.value);
    }

    /**
     * @dev Polymarket 核心逻辑：买入份额 (通过恒定乘积 x * y = k 自动计算价格)
     */
    function buyShares(uint256 _gameId, uint8 _optionId) external payable nonReentrant {
        Game storage game = games[_gameId];
        uint256 amount = msg.value;
        require(amount > 0, "Amount must be > 0");
        require(!game.isResolved && !game.isRefunded, "Game ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");

        uint256 k = game.reserveYES * game.reserveNO; // 恒定乘积 k
        uint256 sharesToUser = amount; // 存入 amount 将首先等量铸造 YES 和 NO 币

        if (_optionId == 0) {
        // 用户想要 YES 币：将铸造的 NO 币全部卖给池子
            game.reserveNO += amount;
            uint256 newReserveYES = k / game.reserveNO;

            // 池子找零给用户的 YES 币
            sharesToUser += (game.reserveYES - newReserveYES);
            game.reserveYES = newReserveYES;

        } else {
    // 用户想要 NO 币：将铸造的 YES 币全部卖给池子
        game.reserveYES += amount;
        uint256 newReserveNO = k / game.reserveYES;

        sharesToUser += (game.reserveNO - newReserveNO);
        game.reserveNO = newReserveNO;
    }

        userShares[_gameId][msg.sender][_optionId] += sharesToUser;
        game.totalPool += amount;

        emit SharesBought(_gameId, msg.sender, _optionId, amount, sharesToUser);
    }

    /**
     * @dev Polymarket 核心逻辑：卖出平仓 (求解一元二次方程)
     */
    function sellShares(uint256 _gameId, uint8 _optionId, uint256 _shareAmount) external nonReentrant {
        Game storage game = games[_gameId];
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(userShares[_gameId][msg.sender][_optionId] >= _shareAmount, "Insufficient shares");

        uint256 x;
        uint256 y;

        if (_optionId == 0) {
            x = game.reserveYES;
            y = game.reserveNO;
        } else {
        x = game.reserveNO;
        y = game.reserveYES;
    }

        // 二次方程求解：用户退回 _shareAmount 个代币，能拿回多少基础代币(deltaB)？
        // 方程: deltaB^2 - (x + y + shareAmount) * deltaB + y * shareAmount = 0
        uint256 b = x + y + _shareAmount;
        uint256 c = y * _shareAmount;

        // 判别式：b^2 - 4ac
        uint256 discriminant = (b * b) - (4 * c);

        // 求根公式取较小根：(b - sqrt(discriminant)) / 2
        uint256 returnAmount = (b - sqrt(discriminant)) / 2;

        require(returnAmount > 0, "Return amount too small");
        require(game.totalPool >= returnAmount, "Insufficient pool funds");

        // 状态更新
        userShares[_gameId][msg.sender][_optionId] -= _shareAmount;
        game.totalPool -= returnAmount;

        if (_optionId == 0) {
            game.reserveYES = x + _shareAmount - returnAmount;
            game.reserveNO = y - returnAmount;
        } else {
        game.reserveNO = x + _shareAmount - returnAmount;
        game.reserveYES = y - returnAmount;
    }

        (bool success, ) = payable(msg.sender).call{value: returnAmount}("");
        require(success, "Transfer failed");

        emit SharesSold(_gameId, msg.sender, _optionId, _shareAmount, returnAmount);
    }

    /**
     * @dev 本地 App 节点清算接口 (由 Admin 触发)
     */
    function resolveGame(uint256 _gameId, uint8 _winningOption) external onlyOwner {
        Game storage game = games[_gameId];
        require(!game.isResolved && !game.isRefunded, "Already resolved");

        game.isResolved = true;
        game.winningOption = _winningOption;

        // 【资金守恒机制】
        // 获胜侧的代币 = 1 BKC。池子里尚未被用户买走的获胜代币，退还给做市商(Admin)。
        uint256 poolWinningShares = (_winningOption == 0) ? game.reserveYES : game.reserveNO;
        if (poolWinningShares > 0) {
            (bool success, ) = payable(owner()).call{value: poolWinningShares}("");
            require(success, "Admin liquidity reclaim failed");
        }

        emit GameResolved(_gameId, _winningOption);
    }

    /**
     * @dev 提取奖励：Polymarket 经典特征，1 获胜币 = 1 基础币
     */
    function claimReward(uint256 _gameId, uint8 _optionId) external nonReentrant {
        Game storage game = games[_gameId];
        require(game.isResolved, "Game not resolved yet");
        require(_optionId == game.winningOption, "Not the winning option");

        uint256 shares = userShares[_gameId][msg.sender][_optionId];
        require(shares > 0, "No winning shares to claim");

        // 1 YES 币 = 1 WEI 完美兑付
        uint256 payout = shares;

        // 清零防重入
        userShares[_gameId][msg.sender][_optionId] = 0;

        (bool success, ) = payable(msg.sender).call{value: payout}("");
        require(success, "Transfer failed");

        emit RewardClaimed(_gameId, msg.sender, payout);
    }

    // =========================================================
    // 视图层：严格对齐 Android Web3j 的 Java 解析器，免修改 App
    // =========================================================

    function getGameInfo(uint256 _gameId) external view returns (
    string memory desc, string memory condition, string memory avatarUrl, string memory detailedInfo,
    string[] memory optionNames, uint8 optionCount, uint256 totalPool,
    bool isResolved, uint8 winningOption, uint256 deadlineSec, bool isRefunded
    ) {
        Game storage g = games[_gameId];
        return (g.desc, g.condition, g.avatarUrl, g.detailedInfo, g.optionNames,
        uint8(g.optionNames.length), g.totalPool, g.isResolved,
        g.winningOption, g.deadlineSec, g.isRefunded);
    }

    /**
     * @dev 极其巧妙的 UI 兼容设计：
     * Java UI 是通过 ( res0 / (res0+res1) ) 来计算 YES 币的胜率进度条的。
     * 在 AMM 模型中，如果 NO 的库存（reserveNO）越多，说明 YES 越被人买断货，YES 的价格/胜率就越高！
     * 因此，这里我们把 reserveNO 当作 res0 返回，将 reserveYES 当作 res1 返回，
     * 这样 Android App 的蓝红进度条就能精准反映出 Polymarket 的实时隐含胜率！
     */
    function getGameExtraData(uint256 _gameId, address _user) external view returns (
    uint256[] memory _virtualReserves,
    uint256[] memory _myShares
    ) {
        Game storage g = games[_gameId];
        _virtualReserves = new uint256[](2);
        _myShares = new uint256[](2);

        // 交叉返回以兼容 UI 胜率计算
        _virtualReserves[0] = g.reserveNO;
        _virtualReserves[1] = g.reserveYES;

        _myShares[0] = userShares[_gameId][_user][0];
        _myShares[1] = userShares[_gameId][_user][1];

        return (_virtualReserves, _myShares);
    }
}