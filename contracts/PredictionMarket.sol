// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract PolymarketGoldV2 is Ownable, ReentrancyGuard {

    // ------------------- 核心数据结构 (IPFS 瘦身版) -------------------
    struct Game {
        uint256 id;
        string ipfsCID;        // 指向 IPFS 上 JSON 规则文件的唯一指纹
        uint256 totalPool;     // 总锁仓资金 (Wei)
        bool isResolved;       // 是否已开奖
        uint8 winningOption;   // 赢家选项 (0: YES, 1: NO)
        uint256 deadlineSec;   // 绝对截止时间戳
        bool isRefunded;       // 是否已退款
        uint256 reserveYES;    // AMM 储备池 YES
        uint256 reserveNO;     // AMM 储备池 NO
    }

    // [新增] 用于批量视图返回的数据传输对象 (DTO)，完美解决 Stack too deep 问题
    struct ParticipatedGameDTO {
        uint256 id;
        string ipfsCID;
        uint256 totalPool;
        uint256 deadlineSec;
        bool isResolved;
        bool isRefunded;
        uint8 winningOption;
        uint256 reserveNO;
        uint256 reserveYES;
        uint256 mySharesYES;
        uint256 mySharesNO;
    }

    uint256 public gameCount;
    mapping(uint256 => Game) public games;

    // 用户持仓: gameId => userAddress => optionId => 持有的币数量
    mapping(uint256 => mapping(address => mapping(uint8 => uint256))) public userShares;

    event GameCreated(uint256 indexed gameId, string ipfsCID, uint256 liquidity);
    event SharesBought(uint256 indexed gameId, address indexed buyer, uint8 optionId, uint256 amountIn, uint256 sharesOut);
    event SharesSold(
        uint256 indexed gameId,
        address indexed seller,
        uint8 optionId,
        uint256 sharesIn,
        uint256 amountOut
    );
    event GameResolved(uint256 indexed gameId, uint8 winningOption);
    event RewardClaimed(uint256 indexed gameId, address indexed user, uint256 reward);

    constructor() Ownable(msg.sender) {}

    // ------------------- 核心业务逻辑 -------------------

    function createGame(string memory _ipfsCID, uint256 _durationSec) external payable {
        require(bytes(_ipfsCID).length > 0, "CID cannot be empty");
        require(msg.value > 0, "Must inject initial liquidity");
        require(_durationSec > 0, "Duration must be > 0");

        gameCount++;
        Game storage newGame = games[gameCount];
        newGame.id = gameCount;
        newGame.ipfsCID = _ipfsCID;
        newGame.deadlineSec = block.timestamp + _durationSec;
        newGame.totalPool = msg.value;
        newGame.reserveYES = msg.value;
        newGame.reserveNO = msg.value;

        emit GameCreated(gameCount, _ipfsCID, msg.value);
    }

    function buyShares(uint256 _gameId, uint8 _optionId) external payable nonReentrant {
        Game storage game = games[_gameId];
        uint256 amount = msg.value;
        require(amount > 0, "Amount must be > 0");
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");
        require(_optionId < 2, "Only YES(0) and NO(1) options allowed");

        uint256 k = game.reserveYES * game.reserveNO;
        uint256 sharesToUser = amount;

        if (_optionId == 0) {
            game.reserveNO += amount;
            uint256 newReserveYES = k / game.reserveNO;
            sharesToUser += (game.reserveYES - newReserveYES);
            game.reserveYES = newReserveYES;
        } else {
            // 修复了原代码中这里的缩进问题
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
     * @notice Returns the BKC collateral received for selling outcome shares.
     *
     * Selling is the exact inverse of buyShares. For a YES sale of S shares,
     * collateral x is chosen so that:
     *
     *   (reserveYES + S - x) * (reserveNO - x)
     *     >= reserveYES * reserveNO
     *
     * Integer rounding is deliberately conservative: the pool invariant may
     * increase by a few wei, but it can never decrease.
     */
    function quoteSellShares(
        uint256 _gameId,
        uint8 _optionId,
        uint256 _shareAmount
    ) public view returns (uint256 amountOut) {
        Game storage game = games[_gameId];
        require(_gameId > 0 && _gameId <= gameCount, "Game does not exist");
        require(_optionId < 2, "Only YES(0) and NO(1) options allowed");
        require(_shareAmount > 0, "Share amount must be > 0");

        if (_optionId == 0) {
            return _calculateSellReturn(game.reserveYES, game.reserveNO, _shareAmount);
        }
        return _calculateSellReturn(game.reserveNO, game.reserveYES, _shareAmount);
    }

    /**
     * @notice Sells YES or NO shares back to the AMM before the deadline.
     * @param _minAmountOut User-provided slippage protection in wei.
     */
    function sellShares(
        uint256 _gameId,
        uint8 _optionId,
        uint256 _shareAmount,
        uint256 _minAmountOut
    ) external nonReentrant {
        Game storage game = games[_gameId];
        require(_gameId > 0 && _gameId <= gameCount, "Game does not exist");
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");
        require(_optionId < 2, "Only YES(0) and NO(1) options allowed");
        require(_shareAmount > 0, "Share amount must be > 0");
        require(
            userShares[_gameId][msg.sender][_optionId] >= _shareAmount,
            "Insufficient shares"
        );

        uint256 amountOut = quoteSellShares(_gameId, _optionId, _shareAmount);
        require(amountOut > 0, "Sale amount is too small");
        require(amountOut >= _minAmountOut, "Slippage limit exceeded");
        require(amountOut <= game.totalPool, "Insufficient pool collateral");
        require(amountOut <= address(this).balance, "Insufficient contract balance");

        userShares[_gameId][msg.sender][_optionId] -= _shareAmount;
        if (_optionId == 0) {
            // amountOut YES and NO are merged into collateral. The remaining
            // sold YES shares return to the virtual reserve.
            game.reserveYES += _shareAmount - amountOut;
            game.reserveNO -= amountOut;
        } else {
            game.reserveNO += _shareAmount - amountOut;
            game.reserveYES -= amountOut;
        }
        game.totalPool -= amountOut;

        (bool success, ) = payable(msg.sender).call{value: amountOut}("");
        require(success, "Transfer failed");

        emit SharesSold(_gameId, msg.sender, _optionId, _shareAmount, amountOut);
    }

    function _calculateSellReturn(
        uint256 heldReserve,
        uint256 oppositeReserve,
        uint256 shareAmount
    ) internal pure returns (uint256) {
        uint256 b = heldReserve + oppositeReserve + shareAmount;
        uint256 discriminant = b * b - 4 * oppositeReserve * shareAmount;
        uint256 root = _sqrt(discriminant);
        if (root * root < discriminant) {
            root += 1;
        }
        return (b - root) / 2;
    }

    function _sqrt(uint256 value) internal pure returns (uint256 result) {
        if (value == 0) return 0;
        uint256 x = 1 << ((log2(value) + 1) >> 1);
        unchecked {
            for (uint256 i = 0; i < 7; ++i) {
                x = (x + value / x) >> 1;
            }
            result = x < value / x ? x : value / x;
        }
    }

    function log2(uint256 value) internal pure returns (uint256 result) {
        unchecked {
            if (value >> 128 > 0) { value >>= 128; result += 128; }
            if (value >> 64 > 0) { value >>= 64; result += 64; }
            if (value >> 32 > 0) { value >>= 32; result += 32; }
            if (value >> 16 > 0) { value >>= 16; result += 16; }
            if (value >> 8 > 0) { value >>= 8; result += 8; }
            if (value >> 4 > 0) { value >>= 4; result += 4; }
            if (value >> 2 > 0) { value >>= 2; result += 2; }
            if (value >> 1 > 0) { result += 1; }
        }
    }

    function resolveGame(uint256 _gameId, uint8 _winningOption) external onlyOwner {
        Game storage game = games[_gameId];
        require(!game.isResolved && !game.isRefunded, "Already resolved");
        require(block.timestamp >= game.deadlineSec, "Game not finished yet");
        require(_winningOption < 2, "Invalid winning option");

        game.isResolved = true;
        game.winningOption = _winningOption;

        uint256 poolWinningShares = (_winningOption == 0) ? game.reserveYES : game.reserveNO;
        if (poolWinningShares > 0) {
            (bool success, ) = payable(owner()).call{value: poolWinningShares}("");
            require(success, "Admin liquidity reclaim failed");
        }

        emit GameResolved(_gameId, _winningOption);
    }

    function claimReward(uint256 _gameId, uint8 _optionId) external nonReentrant {
        Game storage game = games[_gameId];
        require(game.isResolved, "Game not resolved yet");
        require(_optionId == game.winningOption, "Not the winning option");

        uint256 shares = userShares[_gameId][msg.sender][_optionId];
        require(shares > 0, "No winning shares to claim");

        uint256 payout = shares;
        userShares[_gameId][msg.sender][_optionId] = 0;

        (bool success, ) = payable(msg.sender).call{value: payout}("");
        require(success, "Transfer failed");

        emit RewardClaimed(_gameId, msg.sender, payout);
    }

    // ------------------- 单个视图 -------------------

    function getGameInfo(uint256 _gameId) external view returns (
        string memory ipfsCID,
        uint256 totalPool,
        bool isResolved,
        uint8 winningOption,
        uint256 deadlineSec,
        bool isRefunded
    ) {
        Game storage g = games[_gameId];
        return (g.ipfsCID, g.totalPool, g.isResolved, g.winningOption, g.deadlineSec, g.isRefunded);
    }

    function getGameExtraData(uint256 _gameId, address _user) external view returns (
        uint256[] memory _virtualReserves,
        uint256[] memory _myShares
    ) {
        Game storage g = games[_gameId];
        _virtualReserves = new uint256[](2);
        _myShares = new uint256[](2);
        _virtualReserves[0] = g.reserveNO;
        _virtualReserves[1] = g.reserveYES;
        _myShares[0] = userShares[_gameId][_user][0];
        _myShares[1] = userShares[_gameId][_user][1];

        return (_virtualReserves, _myShares);
    }

    // =========================================================
    // 批量查询视图层：一次性返回全市场所有博弈池
    // =========================================================

    function getAllGames() external view returns (
        uint256[] memory ids,
        string[] memory cids,
        uint256[] memory pools,
        uint256[] memory deadlines,
        bool[] memory isResolvedArr,
        bool[] memory isRefundedArr,
        uint8[] memory winningOptions
    ) {
        uint256 count = gameCount;
        ids = new uint256[](count);
        cids = new string[](count);
        pools = new uint256[](count);
        deadlines = new uint256[](count);
        isResolvedArr = new bool[](count);
        isRefundedArr = new bool[](count);
        winningOptions = new uint8[](count);

        for (uint256 i = 1; i <= count; i++) {
            uint256 idx = i - 1;
            Game storage g = games[i];
            ids[idx] = g.id;
            cids[idx] = g.ipfsCID;
            pools[idx] = g.totalPool;
            deadlines[idx] = g.deadlineSec;
            isResolvedArr[idx] = g.isResolved;
            isRefundedArr[idx] = g.isRefunded;
            winningOptions[idx] = g.winningOption;
        }
    }

    function getAllGamesExtraData(address _user) external view returns (
        uint256[] memory reservesNO,
        uint256[] memory reservesYES,
        uint256[] memory mySharesYES,
        uint256[] memory mySharesNO
    ) {
        uint256 count = gameCount;
        reservesNO = new uint256[](count);
        reservesYES = new uint256[](count);
        mySharesYES = new uint256[](count);
        mySharesNO = new uint256[](count);

        for (uint256 i = 1; i <= count; i++) {
            uint256 idx = i - 1;
            Game storage g = games[i];
            reservesNO[idx] = g.reserveNO;
            reservesYES[idx] = g.reserveYES;
            mySharesYES[idx] = userShares[i][_user][0];
            mySharesNO[idx] = userShares[i][_user][1];
        }
    }

    // =========================================================
    // 专属视图：一次性只返回该用户参与过（有持仓）的博弈池，极致节省带宽
    // =========================================================

    function getMyParticipatedGames(address _user) external view returns (ParticipatedGameDTO[] memory) {
        uint256 total = gameCount;
        uint256 myCount = 0;

        // 第一遍：统计该地址参与过的博弈池数量，用于在内存中分配固定长度的数组
        for (uint256 i = 1; i <= total; i++) {
            if (userShares[i][_user][0] > 0 || userShares[i][_user][1] > 0) {
                myCount++;
            }
        }

        // 初始化结构体数组（这只占用堆栈里的 1 个变量槽位）
        ParticipatedGameDTO[] memory result = new ParticipatedGameDTO[](myCount);

        // 第二遍：将真实数据填充进数组
        uint256 index = 0;
        for (uint256 i = 1; i <= total; i++) {
            uint256 sharesY = userShares[i][_user][0];
            uint256 sharesN = userShares[i][_user][1];

            if (sharesY > 0 || sharesN > 0) {
                Game storage g = games[i];

                result[index] = ParticipatedGameDTO({
                    id: g.id,
                    ipfsCID: g.ipfsCID,
                    totalPool: g.totalPool,
                    deadlineSec: g.deadlineSec,
                    isResolved: g.isResolved,
                    isRefunded: g.isRefunded,
                    winningOption: g.winningOption,
                    reserveNO: g.reserveNO,
                    reserveYES: g.reserveYES,
                    mySharesYES: sharesY,
                    mySharesNO: sharesN
                });

                index++;
            }
        }

        return result;
    }
}
