// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract PredictionMarket {
    address public officialOracle;

    uint256 public constant MIN_GAME_DURATION = 5 minutes;
    uint256 public constant MAX_GAME_DURATION = 30 days;
    uint256 public constant MAX_OPTIONS = 8;
    uint256 private constant INITIAL_VIRTUAL_RESERVE = 100 ether;
    uint256 private constant MIN_VIRTUAL_RESERVE = 10 ether;

    struct Game {
        uint256 id;
        string description;
        string condition;
        string avatarUrl;
        string detailedInfo;
        string[] optionNames;
        uint8 optionCount;
        uint256 totalLiquidity;
        mapping(uint8 => uint256) virtualReserves;
        mapping(uint8 => uint256) optionTotalShares;
        bool isResolved;
        uint8 winningOption;
        uint256 deadline;
        bool isRefunded;
    }

    uint256 public gameCount;
    mapping(uint256 => Game) public games;
    mapping(uint256 => mapping(address => mapping(uint8 => uint256))) public userShares;

    event GameCreated(uint256 indexed gameId, address indexed creator, string description, uint256 deadline);
    event GameResolved(uint256 indexed gameId, uint8 winningOption);
    event GameRefunded(uint256 indexed gameId);
    event RewardClaimed(uint256 indexed gameId, address indexed user, uint8 option, uint256 payout);

    modifier onlyOracle() {
        require(msg.sender == officialOracle, "Not Oracle");
        _;
    }

    modifier validGame(uint256 _gameId) {
        require(_gameId > 0 && _gameId <= gameCount, "Invalid game");
        _;
    }

    constructor() {
        officialOracle = msg.sender;
    }

    function createGame(
        string memory _desc,
        string memory _condition,
        string memory _avatarUrl,
        string memory _detailedInfo,
        string[] memory _optionNames,
        uint256 _duration
    ) public onlyOracle {
        require(_optionNames.length >= 2, "At least 2 options");
        require(_optionNames.length <= MAX_OPTIONS, "Too many options");
        require(_duration >= MIN_GAME_DURATION && _duration <= MAX_GAME_DURATION, "Invalid duration");

        gameCount++;
        Game storage g = games[gameCount];
        g.id = gameCount;
        g.description = _desc;
        g.condition = _condition;
        g.avatarUrl = _avatarUrl;
        g.detailedInfo = _detailedInfo;
        g.optionNames = _optionNames;
        g.optionCount = uint8(_optionNames.length);
        g.deadline = block.timestamp + _durationToChainUnits(_duration);

        for (uint8 i = 0; i < g.optionCount; i++) {
            g.virtualReserves[i] = INITIAL_VIRTUAL_RESERVE;
        }

        emit GameCreated(gameCount, msg.sender, _desc, g.deadline);
    }

    function buyShares(uint256 _gameId, uint8 _option) public payable validGame(_gameId) {
        Game storage g = games[_gameId];

        require(_option < g.optionCount, "Invalid option");
        require(block.timestamp < g.deadline, "Expired");
        require(!g.isResolved && !g.isRefunded, "Closed");
        require(msg.value > 0, "Need to pay BKC");

        uint256 totalVirtual = _totalVirtualReserves(g);
        uint256 sharesMinted = (msg.value * totalVirtual) / g.virtualReserves[_option];

        userShares[_gameId][msg.sender][_option] += sharesMinted;
        g.optionTotalShares[_option] += sharesMinted;
        g.totalLiquidity += msg.value;
        g.virtualReserves[_option] += msg.value;
    }

    function sellShares(uint256 _gameId, uint8 _option, uint256 _sharesToSell) public validGame(_gameId) {
        Game storage g = games[_gameId];

        require(_option < g.optionCount, "Invalid option");
        require(_sharesToSell > 0, "Need shares");
        require(block.timestamp < g.deadline, "Expired");
        require(!g.isResolved && !g.isRefunded, "Closed");
        require(userShares[_gameId][msg.sender][_option] >= _sharesToSell, "Insufficient shares");

        uint256 totalVirtual = _totalVirtualReserves(g);
        uint256 rawAmount = (_sharesToSell * g.virtualReserves[_option]) / totalVirtual;
        uint256 fee = (rawAmount * 2) / 100;
        uint256 returnAmount = rawAmount - fee;

        require(returnAmount <= g.totalLiquidity, "Insufficient liquidity");
        require(g.virtualReserves[_option] - returnAmount >= MIN_VIRTUAL_RESERVE, "Reserve too low after sell");

        userShares[_gameId][msg.sender][_option] -= _sharesToSell;
        g.optionTotalShares[_option] -= _sharesToSell;
        g.virtualReserves[_option] -= returnAmount;
        g.totalLiquidity -= returnAmount;

        (bool sent, ) = payable(msg.sender).call{value: returnAmount}("");
        require(sent, "Payout failed");
    }

    function resolveGame(uint256 _gameId, uint8 _winningOption, bytes32 _vdfProof)
        public
        onlyOracle
        validGame(_gameId)
    {
        _vdfProof;
        Game storage g = games[_gameId];

        require(block.timestamp >= g.deadline, "Not ended");
        require(!g.isResolved && !g.isRefunded, "Closed");
        require(_winningOption < g.optionCount, "Invalid option");

        g.isResolved = true;
        g.winningOption = _winningOption;

        emit GameResolved(_gameId, _winningOption);
    }

    function triggerRefund(uint256 _gameId) public onlyOracle validGame(_gameId) {
        Game storage g = games[_gameId];

        require(block.timestamp > g.deadline, "Active");
        require(!g.isResolved && !g.isRefunded, "Closed");

        g.isRefunded = true;

        emit GameRefunded(_gameId);
    }

    function claimReward(uint256 _gameId, uint8 _option) public validGame(_gameId) {
        Game storage g = games[_gameId];

        require(_option < g.optionCount, "Invalid option");
        uint256 sharesOwned = userShares[_gameId][msg.sender][_option];
        require(sharesOwned > 0, "No shares");

        uint256 payout;
        if (g.isRefunded) {
            uint256 totalShares = _totalOptionShares(g);
            require(totalShares > 0, "No shares issued");
            payout = (sharesOwned * g.totalLiquidity) / totalShares;
        } else {
            require(g.isResolved && _option == g.winningOption, "Not winner shares");
            uint256 winningShares = g.optionTotalShares[g.winningOption];
            require(winningShares > 0, "No winner shares");
            payout = (sharesOwned * g.totalLiquidity) / winningShares;
        }

        userShares[_gameId][msg.sender][_option] = 0;

        (bool sent, ) = payable(msg.sender).call{value: payout}("");
        require(sent, "Payout failed");

        emit RewardClaimed(_gameId, msg.sender, _option, payout);
    }

    function getGameInfo(uint256 _id)
        public
        view
        validGame(_id)
        returns (
            string memory,
            string memory,
            string memory,
            string memory,
            string[] memory,
            uint8,
            uint256,
            bool,
            uint8,
            uint256,
            bool
        )
    {
        Game storage g = games[_id];
        return (
            g.description,
            g.condition,
            g.avatarUrl,
            g.detailedInfo,
            g.optionNames,
            g.optionCount,
            g.totalLiquidity,
            g.isResolved,
            g.winningOption,
            g.deadline,
            g.isRefunded
        );
    }

    function getGameExtraData(uint256 _gameId, address _user)
        public
        view
        validGame(_gameId)
        returns (uint256[] memory reserves, uint256[] memory stakesOrShares)
    {
        Game storage g = games[_gameId];

        uint256[] memory _reserves = new uint256[](g.optionCount);
        uint256[] memory _shares = new uint256[](g.optionCount);

        for (uint8 i = 0; i < g.optionCount; i++) {
            _reserves[i] = g.virtualReserves[i];
            _shares[i] = userShares[_gameId][_user][i];
        }

        return (_reserves, _shares);
    }

    function getBatchGames()
        public
        view
        returns (
            uint256[] memory ids,
            string[] memory descriptions,
            string[] memory avatarUrls,
            uint256[] memory totalPools,
            bool[] memory resolvedStates,
            uint256[] memory deadlines,
            uint256[] memory optionCounts
        )
    {
        ids = new uint256[](gameCount);
        descriptions = new string[](gameCount);
        avatarUrls = new string[](gameCount);
        totalPools = new uint256[](gameCount);
        resolvedStates = new bool[](gameCount);
        deadlines = new uint256[](gameCount);
        optionCounts = new uint256[](gameCount);

        for (uint256 i = 1; i <= gameCount; i++) {
            Game storage g = games[i];
            ids[i - 1] = g.id;
            descriptions[i - 1] = g.description;
            avatarUrls[i - 1] = g.avatarUrl;
            totalPools[i - 1] = g.totalLiquidity;
            resolvedStates[i - 1] = g.isResolved;
            deadlines[i - 1] = g.deadline;
            optionCounts[i - 1] = uint256(g.optionCount);
        }

        return (ids, descriptions, avatarUrls, totalPools, resolvedStates, deadlines, optionCounts);
    }

    function _totalVirtualReserves(Game storage g) private view returns (uint256 totalVirtual) {
        for (uint8 i = 0; i < g.optionCount; i++) {
            totalVirtual += g.virtualReserves[i];
        }
    }

    function _durationToChainUnits(uint256 durationSeconds) private view returns (uint256) {
        if (block.timestamp > 10000000000) {
            return durationSeconds * 1000;
        }
        return durationSeconds;
    }

    function _totalOptionShares(Game storage g) private view returns (uint256 totalShares) {
        for (uint8 i = 0; i < g.optionCount; i++) {
            totalShares += g.optionTotalShares[i];
        }
    }
}
