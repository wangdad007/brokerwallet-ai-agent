// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract PolymarketGoldV2 is Ownable, ReentrancyGuard {

// ------------------- 核心数据结构 (IPFS 瘦身版) -------------------
    struct Game {
        uint256 id;
        string ipfsCID;        // 【核心改动】指向 IPFS 上 JSON 规则文件的唯一指纹 (借书码)
        uint256 totalPool;     // 总锁仓资金 (Wei)
        bool isResolved;       // 是否已开奖
        uint8 winningOption;   // 赢家选项 (0: YES, 1: NO)
        uint256 deadlineSec;   // 绝对截止时间戳
        bool isRefunded;

        // AMM 自动做市商的储备池
        uint256 reserveYES;
        uint256 reserveNO;
    }

    uint256 public gameCount;
    mapping(uint256 => Game) public games;

    // 用户持仓: gameId => userAddress => optionId => 持有的 YES 或 NO 币数量
    mapping(uint256 => mapping(address => mapping(uint8 => uint256))) public userShares;

    // 事件中也只记录 CID，极大节省上链成本
    event GameCreated(uint256 indexed gameId, string ipfsCID, uint256 liquidity);
    event SharesBought(uint256 indexed gameId, address indexed buyer, uint8 optionId, uint256 amountIn, uint256 sharesOut);
    event GameResolved(uint256 indexed gameId, uint8 winningOption);
    event RewardClaimed(uint256 indexed gameId, address indexed user, uint256 reward);

    constructor() Ownable(msg.sender) {}

    // ------------------- 核心业务逻辑 -------------------

    /**
     * @dev 1. 部署博弈池 (现在只需传入短小精悍的 CID 和倒计时时长)
     */
    function createGame(
    string memory _ipfsCID,
    uint256 _durationSec
    ) external payable {
        require(bytes(_ipfsCID).length > 0, "CID cannot be empty");
        require(msg.value > 0, "Must inject initial liquidity");
        require(_durationSec > 0, "Duration must be > 0");

        gameCount++;
        Game storage newGame = games[gameCount];
        newGame.id = gameCount;
        newGame.ipfsCID = _ipfsCID;

        // 相对时间秒数转为绝对时间戳
        newGame.deadlineSec = block.timestamp + _durationSec;

        newGame.totalPool = msg.value;
        newGame.reserveYES = msg.value;
        newGame.reserveNO = msg.value;

        emit GameCreated(gameCount, _ipfsCID, msg.value);
    }

    /**
     * @dev 2. 买入 YES 或 NO 币 (Polymarket 核心定价公式)
     */
    function buyShares(uint256 _gameId, uint8 _optionId) external payable nonReentrant {
        Game storage game = games[_gameId];
        uint256 amount = msg.value;
        require(amount > 0, "Amount must be > 0");
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");
        require(_optionId < 2, "Only YES(0) and NO(1) options allowed");

        // 恒定乘积 k = x * y
        uint256 k = game.reserveYES * game.reserveNO;
        uint256 sharesToUser = amount;

        if (_optionId == 0) {
        // 买入 YES 币：将等额 NO 币留给系统，换取 YES 币
            game.reserveNO += amount;
            uint256 newReserveYES = k / game.reserveNO;
            sharesToUser += (game.reserveYES - newReserveYES);
            game.reserveYES = newReserveYES;
        } else {
    // 买入 NO 币：将等额 YES 币留给系统，换取 NO 币
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
     * @dev 3. 管理员/App后台本地裁决后写入开奖结果
     */
    function resolveGame(uint256 _gameId, uint8 _winningOption) external onlyOwner {
        Game storage game = games[_gameId];
        require(!game.isResolved && !game.isRefunded, "Already resolved");
        require(block.timestamp >= game.deadlineSec, "Game not finished yet");
        require(_winningOption < 2, "Invalid winning option");

        game.isResolved = true;
        game.winningOption = _winningOption;

        // 【资金守恒】：将系统中没人买的赢方代币，退回给部署池子的人（做市商成本回收）
        uint256 poolWinningShares = (_winningOption == 0) ? game.reserveYES : game.reserveNO;
        if (poolWinningShares > 0) {
            (bool success, ) = payable(owner()).call{value: poolWinningShares}("");
            require(success, "Admin liquidity reclaim failed");
        }

        emit GameResolved(_gameId, _winningOption);
    }

    /**
     * @dev 4. 赢家 1:1 提取奖金
     */
    function claimReward(uint256 _gameId, uint8 _optionId) external nonReentrant {
        Game storage game = games[_gameId];
        require(game.isResolved, "Game not resolved yet");
        require(_optionId == game.winningOption, "Not the winning option");

        uint256 shares = userShares[_gameId][msg.sender][_optionId];
        require(shares > 0, "No winning shares to claim");

        uint256 payout = shares; // 1 个获胜币 = 1 Wei

        userShares[_gameId][msg.sender][_optionId] = 0; // 防重入清零

        (bool success, ) = payable(msg.sender).call{value: payout}("");
        require(success, "Transfer failed");

        emit RewardClaimed(_gameId, msg.sender, payout);
    }

    // =========================================================
    // 视图层：返回值由原来的 11 个参数精简为 6 个核心参数
    // =========================================================

    function getGameInfo(uint256 _gameId) external view returns (
    string memory ipfsCID,
    uint256 totalPool,
    bool isResolved,
    uint8 winningOption,
    uint256 deadlineSec,
    bool isRefunded
    ) {
        Game storage g = games[_gameId];
        return (
        g.ipfsCID,
        g.totalPool,
        g.isResolved,
        g.winningOption,
        g.deadlineSec,
        g.isRefunded
        );
    }

    // 依然保留交叉返回，让你的 Android 双色胜率进度条自动精准显示！
    function getGameExtraData(uint256 _gameId, address _user) external view returns (
    uint256[] memory _virtualReserves,
    uint256[] memory _myShares
    ) {
        Game storage g = games[_gameId];
        _virtualReserves = new uint256[](2);
        _myShares = new uint256[](2);

        // NO 的库存对应 YES 的隐含价格
        _virtualReserves[0] = g.reserveNO;
        _virtualReserves[1] = g.reserveYES;

        _myShares[0] = userShares[_gameId][_user][0];
        _myShares[1] = userShares[_gameId][_user][1];

        return (_virtualReserves, _myShares);
    }
}