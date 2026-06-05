// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * @title GoldPredictionMarket
 * @dev 专门针对黄金票据博弈设计的智能合约。支持用户自定义创建、AMM 份额交易及奖励结算。
 */
contract GoldPredictionMarket {
    address public owner;
    address public settlementAdmin; // 负责根据 App 代码判定结果进行链上开奖的地址

    uint256 public constant MIN_DURATION = 1 days;
    uint256 private constant INITIAL_VIRTUAL_RESERVE = 100 ether; // 初始流动性基准

    struct Game {
        uint256 id;
        string description;  // 动态生成的标题，如 "2026-07-01 前黄金价格上涨"
        string condition;    // 详细判定逻辑，如 "Price >= 2500 USD"
        string creatorInfo;  // 创建者备注
        uint256 startTime;   // 博弈开始时间戳
        uint256 deadline;    // 博弈截止时间戳
        uint8 optionCount;
        string[] optionNames;
        uint256 totalLiquidity;
        mapping(uint8 => uint256) virtualReserves;
        mapping(uint8 => uint256) optionTotalShares;
        bool isResolved;
        uint8 winningOption;
        bool isRefunded;
        address creator;
    }

    uint256 public gameCount;
    mapping(uint256 => Game) public games;
    mapping(uint256 => mapping(address => mapping(uint8 => uint256))) public userShares;

    event GameCreated(uint256 indexed gameId, address indexed creator, string description, uint256 deadline);
    event GameResolved(uint256 indexed gameId, uint8 winningOption);
    event RewardClaimed(uint256 indexed gameId, address indexed user, uint256 payout);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    modifier onlyAdmin() {
        require(msg.sender == settlementAdmin || msg.sender == owner, "Not authorized");
        _;
    }

    constructor() {
        owner = msg.sender;
        settlementAdmin = msg.sender; // 初始设置部署者为结算员
    }

    /**
     * @dev 用户创建博弈池。App 端生成的描述和逻辑将存入链上。
     */
    function createGame(
    string memory _desc,
    string memory _condition,
    string memory _creatorInfo,
    string[] memory _options,
    uint256 _durationSeconds
    ) public returns (uint256) {
        require(_options.length >= 2, "Options min 2");

        gameCount++;
        Game storage g = games[gameCount];
        g.id = gameCount;
        g.description = _desc;
        g.condition = _condition;
        g.creatorInfo = _creatorInfo;
        g.startTime = block.timestamp;
        g.deadline = block.timestamp + _durationSeconds;
        g.optionCount = uint8(_options.length);
        g.optionNames = _options;
        g.creator = msg.sender;

        for (uint8 i = 0; i < g.optionCount; i++) {
            g.virtualReserves[i] = INITIAL_VIRTUAL_RESERVE;
        }

        emit GameCreated(gameCount, msg.sender, _desc, g.deadline);
        return gameCount;
    }

    /**
     * @dev 买入博弈份额 (AMM 逻辑)
     */
    function buyShares(uint256 _gameId, uint8 _option) public payable {
        Game storage g = games[_gameId];
        require(block.timestamp < g.deadline, "Expired");
        require(!g.isResolved, "Closed");
        require(msg.value > 0, "No value");

        uint256 totalVirtual = 0;
        for(uint8 i=0; i<g.optionCount; i++) totalVirtual += g.virtualReserves[i];

        uint256 sharesMinted = (msg.value * totalVirtual) / g.virtualReserves[_option];

        userShares[_gameId][msg.sender][_option] += sharesMinted;
        g.optionTotalShares[_option] += sharesMinted;
        g.virtualReserves[_option] += msg.value;
        g.totalLiquidity += msg.value;
    }

    /**
     * @dev 管理员/结算系统调用此函数开奖。
     * App 端 if-else 判定结果后，由此函数锁定最终胜出选项。
     */
    function resolveGame(uint256 _gameId, uint8 _winningOption) public onlyAdmin {
        Game storage g = games[_gameId];
        require(block.timestamp >= g.deadline, "Not ended");
        require(!g.isResolved, "Already resolved");

        g.isResolved = true;
        g.winningOption = _winningOption;

        emit GameResolved(_gameId, _winningOption);
    }

    /**
     * @dev 用户领取奖励
     */
    function claimReward(uint256 _gameId, uint8 _option) public {
        Game storage g = games[_gameId];
        require(g.isResolved, "Not resolved");
        require(_option == g.winningOption, "Not winner");

        uint256 sharesOwned = userShares[_gameId][msg.sender][_option];
        require(sharesOwned > 0, "No shares");

        uint256 winningShares = g.optionTotalShares[_option];
        uint256 payout = (sharesOwned * g.totalLiquidity) / winningShares;

        userShares[_gameId][msg.sender][_option] = 0;
        payable(msg.sender).transfer(payout);

        emit RewardClaimed(_gameId, msg.sender, payout);
    }

    // 设置结算员地址 (用于自动化脚本)
    function setSettlementAdmin(address _admin) public onlyOwner {
        settlementAdmin = _admin;
    }

    // 获取博弈详情的辅助函数
    function getGameInfo(uint256 _id) public view returns (
    string memory desc, string memory cond, uint256 deadline, bool resolved, uint8 winner, uint256 pool
    ) {
        Game storage g = games[_id];
        return (g.description, g.condition, g.deadline, g.isResolved, g.winningOption, g.totalLiquidity);
    }
}