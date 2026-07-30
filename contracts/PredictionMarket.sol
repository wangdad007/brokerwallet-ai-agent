// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/math/Math.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract PolymarketGoldV2 is Ownable, ReentrancyGuard {
    uint256 public constant TRADING_FEE_BPS = 100;
    uint256 private constant BPS_DENOMINATOR = 10_000;
    uint256 private constant FEE_ACCUMULATOR_SCALE = 1e18;

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

    // LP 权益：创建者的初始流动性会铸造成首批 LP 份额，后续提供者按池深度增发。
    mapping(uint256 => uint256) public totalLiquidityShares;
    mapping(uint256 => mapping(address => uint256)) public liquidityShares;
    // The creator's first LP tranche anchors the market. It cannot be
    // withdrawn while trading is active; settlement or refund unlocks it.
    mapping(uint256 => address) public gameCreators;
    mapping(uint256 => uint256) public creatorLockedLiquidityShares;

    // 1% 交易费由当前 LP 按份额累计，新增 LP 不会分走加入前已经产生的费用。
    mapping(uint256 => uint256) public liquidityFeePool;
    mapping(uint256 => uint256) public accumulatedFeePerLiquidityShare;
    mapping(uint256 => mapping(address => uint256)) public liquidityFeeDebt;
    mapping(uint256 => mapping(address => uint256)) public unclaimedLiquidityFees;

    event GameCreated(uint256 indexed gameId, string ipfsCID, uint256 liquidity);
    event SharesBought(uint256 indexed gameId, address indexed buyer, uint8 optionId, uint256 amountIn, uint256 sharesOut);
    event SharesSold(
        uint256 indexed gameId,
        address indexed seller,
        uint8 optionId,
        uint256 sharesIn,
        uint256 amountOut
    );
    event TradingFeeAccrued(uint256 indexed gameId, uint256 feeAmount);
    event LiquidityAdded(
        uint256 indexed gameId,
        address indexed provider,
        uint256 amountIn,
        uint256 liquiditySharesOut,
        uint256 returnedYES,
        uint256 returnedNO
    );
    event LiquidityRemoved(
        uint256 indexed gameId,
        address indexed provider,
        uint256 liquiditySharesIn,
        uint256 collateralOut,
        uint256 feeOut,
        uint256 returnedYES,
        uint256 returnedNO
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
        totalLiquidityShares[gameCount] = msg.value;
        liquidityShares[gameCount][msg.sender] = msg.value;
        gameCreators[gameCount] = msg.sender;
        creatorLockedLiquidityShares[gameCount] = msg.value;

        emit GameCreated(gameCount, _ipfsCID, msg.value);
        emit LiquidityAdded(gameCount, msg.sender, msg.value, msg.value, 0, 0);
    }

    function buyShares(uint256 _gameId, uint8 _optionId) external payable nonReentrant {
        Game storage game = games[_gameId];
        uint256 amount = msg.value;
        require(amount > 0, "Amount must be > 0");
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");
        require(_optionId < 2, "Only YES(0) and NO(1) options allowed");

        uint256 fee = Math.mulDiv(amount, TRADING_FEE_BPS, BPS_DENOMINATOR);
        uint256 netAmount = amount - fee;
        require(netAmount > 0, "Amount is too small after fee");

        uint256 k = game.reserveYES * game.reserveNO;
        uint256 sharesToUser = netAmount;

        if (_optionId == 0) {
            game.reserveNO += netAmount;
            uint256 newReserveYES = k / game.reserveNO;
            sharesToUser += (game.reserveYES - newReserveYES);
            game.reserveYES = newReserveYES;
        } else {
            game.reserveYES += netAmount;
            uint256 newReserveNO = k / game.reserveYES;
            sharesToUser += (game.reserveNO - newReserveNO);
            game.reserveNO = newReserveNO;
        }

        userShares[_gameId][msg.sender][_optionId] += sharesToUser;
        game.totalPool += amount;
        _accrueTradingFee(_gameId, fee);

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

        uint256 grossAmountOut = _optionId == 0
            ? _calculateSellReturn(game.reserveYES, game.reserveNO, _shareAmount)
            : _calculateSellReturn(game.reserveNO, game.reserveYES, _shareAmount);
        uint256 fee = Math.mulDiv(grossAmountOut, TRADING_FEE_BPS, BPS_DENOMINATOR);
        return grossAmountOut - fee;
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

        uint256 grossAmountOut = _optionId == 0
            ? _calculateSellReturn(game.reserveYES, game.reserveNO, _shareAmount)
            : _calculateSellReturn(game.reserveNO, game.reserveYES, _shareAmount);
        uint256 fee = Math.mulDiv(grossAmountOut, TRADING_FEE_BPS, BPS_DENOMINATOR);
        uint256 amountOut = grossAmountOut - fee;
        require(amountOut > 0, "Sale amount is too small");
        require(amountOut >= _minAmountOut, "Slippage limit exceeded");
        require(amountOut <= game.totalPool, "Insufficient pool collateral");
        require(amountOut <= address(this).balance, "Insufficient contract balance");

        userShares[_gameId][msg.sender][_optionId] -= _shareAmount;
        if (_optionId == 0) {
            // amountOut YES and NO are merged into collateral. The remaining
            // sold YES shares return to the virtual reserve.
            game.reserveYES += _shareAmount - grossAmountOut;
            game.reserveNO -= grossAmountOut;
        } else {
            game.reserveNO += _shareAmount - grossAmountOut;
            game.reserveYES -= grossAmountOut;
        }
        game.totalPool -= amountOut;
        _accrueTradingFee(_gameId, fee);

        (bool success, ) = payable(msg.sender).call{value: amountOut}("");
        require(success, "Transfer failed");

        emit SharesSold(_gameId, msg.sender, _optionId, _shareAmount, amountOut);
    }

    /**
     * @notice Quotes a BKC liquidity contribution without changing the market probability.
     * The contribution is split into a complete YES/NO set. Only the proportional
     * amounts enter the pool; any surplus single-sided shares are returned to the LP.
     */
    function quoteAddLiquidity(
        uint256 _gameId,
        uint256 _amount
    ) public view returns (
        uint256 liquiditySharesOut,
        uint256 returnedYES,
        uint256 returnedNO
    ) {
        Game storage game = games[_gameId];
        require(_gameId > 0 && _gameId <= gameCount, "Game does not exist");
        require(_amount > 0, "Amount must be > 0");

        uint256 supply = totalLiquidityShares[_gameId];
        uint256 maxReserve = Math.max(game.reserveYES, game.reserveNO);
        require(supply > 0 && maxReserve > 0, "Liquidity pool is unavailable");

        liquiditySharesOut = Math.mulDiv(_amount, supply, maxReserve);
        require(liquiditySharesOut > 0, "Liquidity amount is too small");

        uint256 depositYES = Math.mulDiv(game.reserveYES, _amount, maxReserve);
        uint256 depositNO = Math.mulDiv(game.reserveNO, _amount, maxReserve);
        returnedYES = _amount - depositYES;
        returnedNO = _amount - depositNO;
    }

    /**
     * @notice Adds BKC liquidity and mints transferable-value LP accounting shares.
     * @param _minLiquidityShares Slippage protection for the LP shares minted.
     */
    function addLiquidity(
        uint256 _gameId,
        uint256 _minLiquidityShares
    ) external payable nonReentrant {
        Game storage game = games[_gameId];
        require(_gameId > 0 && _gameId <= gameCount, "Game does not exist");
        require(!game.isResolved && !game.isRefunded, "Game already ended");
        require(block.timestamp < game.deadlineSec, "Past deadline");
        require(msg.value > 0, "Amount must be > 0");

        _settleLiquidityFees(_gameId, msg.sender);

        (
            uint256 liquiditySharesOut,
            uint256 returnedYES,
            uint256 returnedNO
        ) = quoteAddLiquidity(_gameId, msg.value);
        require(
            liquiditySharesOut >= _minLiquidityShares,
            "Liquidity slippage limit exceeded"
        );

        uint256 depositYES = msg.value - returnedYES;
        uint256 depositNO = msg.value - returnedNO;
        game.reserveYES += depositYES;
        game.reserveNO += depositNO;
        game.totalPool += msg.value;

        totalLiquidityShares[_gameId] += liquiditySharesOut;
        liquidityShares[_gameId][msg.sender] += liquiditySharesOut;
        _syncLiquidityFeeDebt(_gameId, msg.sender);

        if (returnedYES > 0) {
            userShares[_gameId][msg.sender][0] += returnedYES;
        }
        if (returnedNO > 0) {
            userShares[_gameId][msg.sender][1] += returnedNO;
        }

        emit LiquidityAdded(
            _gameId,
            msg.sender,
            msg.value,
            liquiditySharesOut,
            returnedYES,
            returnedNO
        );
    }

    /**
     * @notice Quotes LP withdrawal. Before settlement, equal YES/NO inventory
     * is merged back to BKC and surplus inventory is returned as outcome shares.
     * After settlement, the LP receives its proportional winning reserve.
     */
    function quoteRemoveLiquidity(
        uint256 _gameId,
        address _provider,
        uint256 _liquidityShareAmount
    ) public view returns (
        uint256 collateralOut,
        uint256 feeOut,
        uint256 returnedYES,
        uint256 returnedNO
    ) {
        Game storage game = games[_gameId];
        require(_gameId > 0 && _gameId <= gameCount, "Game does not exist");
        uint256 supply = totalLiquidityShares[_gameId];
        require(_liquidityShareAmount > 0, "LP share amount must be > 0");
        require(_liquidityShareAmount <= liquidityShares[_gameId][_provider], "Insufficient LP shares");
        require(supply > 0, "Liquidity pool is unavailable");
        if (!game.isResolved && !game.isRefunded) {
            require(block.timestamp < game.deadlineSec, "Settlement is pending");
            require(_liquidityShareAmount < supply, "Cannot remove all active liquidity");
            if (_provider == gameCreators[_gameId]) {
                require(
                    liquidityShares[_gameId][_provider] - _liquidityShareAmount
                        >= creatorLockedLiquidityShares[_gameId],
                    "Creator initial liquidity is locked"
                );
            }
        }

        uint256 reserveYESOut = _liquidityShareAmount == supply
            ? game.reserveYES
            : Math.mulDiv(game.reserveYES, _liquidityShareAmount, supply);
        uint256 reserveNOOut = _liquidityShareAmount == supply
            ? game.reserveNO
            : Math.mulDiv(game.reserveNO, _liquidityShareAmount, supply);

        if (game.isResolved) {
            collateralOut = game.winningOption == 0 ? reserveYESOut : reserveNOOut;
        } else if (game.isRefunded) {
            collateralOut = Math.min(reserveYESOut, reserveNOOut);
        } else {
            collateralOut = Math.min(reserveYESOut, reserveNOOut);
            returnedYES = reserveYESOut - collateralOut;
            returnedNO = reserveNOOut - collateralOut;
        }
        feeOut = _pendingLiquidityFees(_gameId, _provider);
    }

    /**
     * @notice Burns LP shares and returns proportional BKC plus accrued trading fees.
     */
    function removeLiquidity(
        uint256 _gameId,
        uint256 _liquidityShareAmount,
        uint256 _minAmountOut
    ) external nonReentrant {
        Game storage game = games[_gameId];
        _settleLiquidityFees(_gameId, msg.sender);

        (
            uint256 collateralOut,
            uint256 feeOut,
            uint256 returnedYES,
            uint256 returnedNO
        ) = quoteRemoveLiquidity(_gameId, msg.sender, _liquidityShareAmount);
        uint256 totalAmountOut = collateralOut + feeOut;
        require(totalAmountOut >= _minAmountOut, "Liquidity slippage limit exceeded");
        require(totalAmountOut <= game.totalPool, "Insufficient pool collateral");
        require(totalAmountOut <= address(this).balance, "Insufficient contract balance");

        uint256 supply = totalLiquidityShares[_gameId];
        uint256 reserveYESOut = _liquidityShareAmount == supply
            ? game.reserveYES
            : Math.mulDiv(game.reserveYES, _liquidityShareAmount, supply);
        uint256 reserveNOOut = _liquidityShareAmount == supply
            ? game.reserveNO
            : Math.mulDiv(game.reserveNO, _liquidityShareAmount, supply);

        game.reserveYES -= reserveYESOut;
        game.reserveNO -= reserveNOOut;
        totalLiquidityShares[_gameId] = supply - _liquidityShareAmount;
        liquidityShares[_gameId][msg.sender] -= _liquidityShareAmount;
        unclaimedLiquidityFees[_gameId][msg.sender] = 0;
        liquidityFeePool[_gameId] -= feeOut;
        game.totalPool -= totalAmountOut;
        _syncLiquidityFeeDebt(_gameId, msg.sender);

        if (returnedYES > 0) {
            userShares[_gameId][msg.sender][0] += returnedYES;
        }
        if (returnedNO > 0) {
            userShares[_gameId][msg.sender][1] += returnedNO;
        }

        if (totalAmountOut > 0) {
            (bool success, ) = payable(msg.sender).call{value: totalAmountOut}("");
            require(success, "Transfer failed");
        }

        emit LiquidityRemoved(
            _gameId,
            msg.sender,
            _liquidityShareAmount,
            collateralOut,
            feeOut,
            returnedYES,
            returnedNO
        );
    }

    function _accrueTradingFee(uint256 _gameId, uint256 _fee) internal {
        if (_fee == 0) return;
        uint256 supply = totalLiquidityShares[_gameId];
        require(supply > 0, "Liquidity pool is unavailable");
        liquidityFeePool[_gameId] += _fee;
        accumulatedFeePerLiquidityShare[_gameId] +=
            Math.mulDiv(_fee, FEE_ACCUMULATOR_SCALE, supply);
        emit TradingFeeAccrued(_gameId, _fee);
    }

    function _settleLiquidityFees(uint256 _gameId, address _provider) internal {
        uint256 accrued = Math.mulDiv(
            liquidityShares[_gameId][_provider],
            accumulatedFeePerLiquidityShare[_gameId],
            FEE_ACCUMULATOR_SCALE
        );
        uint256 debt = liquidityFeeDebt[_gameId][_provider];
        if (accrued > debt) {
            unclaimedLiquidityFees[_gameId][_provider] += accrued - debt;
        }
        liquidityFeeDebt[_gameId][_provider] = accrued;
    }

    function _syncLiquidityFeeDebt(uint256 _gameId, address _provider) internal {
        liquidityFeeDebt[_gameId][_provider] = Math.mulDiv(
            liquidityShares[_gameId][_provider],
            accumulatedFeePerLiquidityShare[_gameId],
            FEE_ACCUMULATOR_SCALE
        );
    }

    function _pendingLiquidityFees(
        uint256 _gameId,
        address _provider
    ) internal view returns (uint256) {
        uint256 accrued = Math.mulDiv(
            liquidityShares[_gameId][_provider],
            accumulatedFeePerLiquidityShare[_gameId],
            FEE_ACCUMULATOR_SCALE
        );
        uint256 debt = liquidityFeeDebt[_gameId][_provider];
        uint256 pending = accrued > debt ? accrued - debt : 0;
        return unclaimedLiquidityFees[_gameId][_provider] + pending;
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
        require(payout <= game.totalPool, "Insufficient pool collateral");
        game.totalPool -= payout;

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

    function getLiquidityPosition(
        uint256 _gameId,
        address _provider
    ) external view returns (
        uint256 _totalLiquidityShares,
        uint256 _myLiquidityShares,
        uint256 _claimableFees,
        uint256 _feePool
    ) {
        return (
            totalLiquidityShares[_gameId],
            liquidityShares[_gameId][_provider],
            _pendingLiquidityFees(_gameId, _provider),
            liquidityFeePool[_gameId]
        );
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
    // 专属视图：一次性只返回该用户参与过（持有结果份额或 LP 份额）的博弈池
    // =========================================================

    function getMyParticipatedGames(address _user) external view returns (ParticipatedGameDTO[] memory) {
        uint256 total = gameCount;
        uint256 myCount = 0;

        // 第一遍：统计该地址参与过的博弈池数量，用于在内存中分配固定长度的数组
        for (uint256 i = 1; i <= total; i++) {
            if (
                userShares[i][_user][0] > 0 ||
                userShares[i][_user][1] > 0 ||
                liquidityShares[i][_user] > 0
            ) {
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

            if (sharesY > 0 || sharesN > 0 || liquidityShares[i][_user] > 0) {
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
