package com.example.brokerfi.xc.agent.gold.model.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * BackendApiClient - Go 后端数据库交互客户端
 *
 * 职责：DApp 与 Go 后端 PostgreSQL 数据库之间的桥梁。
 *
 * 设计原则（减少链交互时延）：
 * - 读操作：优先从后端 DB 读取缓存数据（快速），失败时回退到 IPFS/链上直读
 * - 写操作：先完成 IPFS 上传 + 链上交易，然后同步元数据到后端 DB
 *
 * 后端 API 基础路径：{BASE_URL}/api/v1/gold/
 *
 * URL 自动切换规则：
 * - local 分支默认：AgentConfig.BACKEND_BASE_URL（Android 模拟器 → 宿主机 localhost）
 * - 可手动覆盖：SharedPreferences "backend_prefs" → "base_url"
 */
public class BackendApiClient {
    private static final String TAG = "BackendApiClient";

    private static final String PREFS_NAME = "backend_prefs";
    private static final String KEY_BASE_URL = "base_url";

    private static final String API_PREFIX = AgentConfig.BACKEND_GOLD_API_PREFIX;

    private static final Gson gson = new Gson();
    private static final int CONNECT_TIMEOUT_MS = AgentConfig.HTTP_CONNECT_TIMEOUT_MS;
    private static final int READ_TIMEOUT_MS = AgentConfig.HTTP_READ_TIMEOUT_MS;
    private static final int FAST_WRITE_CONNECT_TIMEOUT_MS = AgentConfig.FAST_WRITE_CONNECT_TIMEOUT_MS;
    private static final int FAST_WRITE_READ_TIMEOUT_MS = AgentConfig.FAST_WRITE_READ_TIMEOUT_MS;

    private static String cachedBaseUrl = null;

    /**
     * 获取当前生效的 Base URL
     * 优先级：手动设置 > Debug/Release 自动选择
     */
    public static String getBaseUrl(Context ctx) {
        if (cachedBaseUrl != null) return cachedBaseUrl;

        // 1. 检查是否有手动设置的 URL（开发调试用）
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String manual = prefs.getString(KEY_BASE_URL, null);
            if (manual != null && !manual.trim().isEmpty()) {
                cachedBaseUrl = manual.trim();
                Log.d(TAG, "Using manually configured Base URL: " + cachedBaseUrl);
                return cachedBaseUrl;
            }
        }

        // 2. local 分支始终使用本地后端，避免 Release 包回退到远程服务器
        cachedBaseUrl = AgentConfig.BACKEND_BASE_URL;
        Log.d(TAG, "Base URL selected automatically: " + cachedBaseUrl + " (local-supervisor)");
        return cachedBaseUrl;
    }

    /**
     * 手动设置 Base URL（用于开发调试切换服务器）
     */
    public static void setBaseUrl(Context ctx, String url) {
        cachedBaseUrl = (url != null && !url.trim().isEmpty()) ? url.trim() : null;
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_BASE_URL, cachedBaseUrl).apply();
        }
        Log.d(TAG, "Base URL updated: " + cachedBaseUrl);
    }

    /**
     * 清除手动设置，恢复自动选择
     */
    public static void resetBaseUrl(Context ctx) {
        cachedBaseUrl = null;
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_BASE_URL).apply();
        }
        Log.d(TAG, "Base URL reset to automatic selection");
    }

    // ==================== 内部 HTTP 工具 ====================

    private static String resolveBaseUrl() {
        // 无 Context 时的回退：local 分支始终使用本地后端
        // GoldMarketRepository 有 Context，调用前会通过 getBaseUrl(ctx) 触发缓存
        if (cachedBaseUrl != null) return cachedBaseUrl;
        return AgentConfig.BACKEND_BASE_URL;
    }

    private static String doGet(String path) throws Exception {
        String base = resolveBaseUrl();
        String fullUrl = base + API_PREFIX + path;
        Log.d(TAG, "GET " + fullUrl);
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            String body = readStream(conn.getInputStream());
            Log.d(TAG, "GET " + path + " -> 200 OK (" + body.length() + " bytes)");
            return body;
        } else {
            String err = readStream(conn.getErrorStream());
            Log.w(TAG, "GET " + fullUrl + " -> HTTP " + code + ": " + err);
            throw new Exception("Backend GET " + path + " failed: HTTP " + code + " - " + err);
        }
    }

    private static String doPost(String path, String jsonBody) throws Exception {
        return doPost(path, jsonBody, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    private static String doPost(String path, String jsonBody,
                                 int connectTimeoutMs, int readTimeoutMs) throws Exception {
        String base = resolveBaseUrl();
        String fullUrl = base + API_PREFIX + path;
        Log.d(TAG, "POST " + fullUrl);
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            String body = readStream(conn.getInputStream());
            Log.d(TAG, "POST " + path + " -> 200 OK");
            return body;
        } else {
            String err = readStream(conn.getErrorStream());
            Log.w(TAG, "POST " + fullUrl + " -> HTTP " + code + ": " + err);
            throw new Exception("Backend POST " + path + " failed: HTTP " + code + " - " + err);
        }
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return "";
        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    // ==================== 游戏元数据 API（DB 读写） ====================

    /**
     * 获取后端当前运行策略。是否允许创建已结束的演示池由后端的同一个
     * sentinel.auto_resolve_enabled 开关派生，前端不保存第二份配置。
     */
    public static RuntimePolicy fetchRuntimePolicy() throws Exception {
        String body = doGet("/runtime-policy");
        return gson.fromJson(body, RuntimePolicy.class);
    }

    public static class RuntimePolicy {
        @SerializedName("auto_resolve_enabled")
        public boolean autoResolveEnabled = true;

        @SerializedName("allow_expired_market_creation")
        public boolean allowExpiredMarketCreation;

        @SerializedName("demo_duration_seconds")
        public long demoDurationSeconds = 1L;
    }

    /**
     * 从后端 DB 获取所有游戏的元数据（标题、条件、图片等）
     * 相比从 IPFS 逐个下载，DB 查询快得多
     *
     * GET /api/v1/gold/games
     * Response: { "games": [ GameMetaDTO, ... ] }
     */
    public static List<GameMetaDTO> fetchAllGameMetadata() throws Exception {
        String body = doGet("/games");
        JSONObject json = new JSONObject(body);
        JSONArray arr = json.getJSONArray("games");
        Type listType = new TypeToken<List<GameMetaDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    /**
     * 从后端 DB 获取单个游戏的元数据
     *
     * GET /api/v1/gold/games/{gameId}
     * Response: GameMetaDTO
     */
    public static GameMetaDTO fetchGameMetadata(int gameId) throws Exception {
        String body = doGet("/games/" + gameId);
        return gson.fromJson(body, GameMetaDTO.class);
    }

    /**
     * 同步游戏元数据到后端 DB（创建或更新）
     * 调用时机：创建游戏完成后（IPFS 已上传 + 链上交易已确认）
     *
     * POST /api/v1/gold/games/sync
     * Body: GameMetaSyncReq
     * Response: { "success": true, "game_id": 123 }
     */
    public static boolean syncGameMetadata(GameMetaSyncReq req) throws Exception {
        String body = doPost("/games/sync", gson.toJson(req));
        JSONObject json = new JSONObject(body);
        return json.optBoolean("success", false);
    }

    // ==================== 链上状态缓存 API ====================

    /**
     * 从后端 DB 获取缓存的链上状态（避免直接 eth_call 的延迟）
     *
     * GET /api/v1/gold/games/{gameId}/chain-state
     * Response: ChainStateDTO
     */
    public static ChainStateDTO fetchChainState(int gameId, String userAddress) throws Exception {
        String body = doGet("/games/" + gameId + "/chain-state?user_address=" + userAddress);
        return gson.fromJson(body, ChainStateDTO.class);
    }

    /**
     * 批量获取所有游戏的链上缓存状态
     *
     * GET /api/v1/gold/games/chain-states?user_address=0x...
     * Response: { "states": [ ChainStateDTO, ... ] }
     */
    public static List<ChainStateDTO> fetchAllChainStates(String userAddress) throws Exception {
        String body = doGet("/games/chain-states?user_address=" + userAddress);
        JSONObject json = new JSONObject(body);
        JSONArray arr = json.getJSONArray("states");
        Type listType = new TypeToken<List<ChainStateDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    /**
     * 同步链上状态到后端 DB（交易确认后调用）
     *
     * POST /api/v1/gold/games/{gameId}/chain-state/sync
     * Body: ChainStateSyncReq
     */
    public static boolean syncChainState(int gameId, ChainStateSyncReq req) throws Exception {
        String body = doPost("/games/" + gameId + "/chain-state/sync", gson.toJson(req));
        JSONObject json = new JSONObject(body);
        return json.optBoolean("success", false);
    }

    // ==================== 历史价格数据 API ====================

    /**
     * 获取游戏的历史价格数据（用于折线图显示）
     *
     * GET /api/v1/gold/games/{gameId}/history
     * Response: { "history": [ HistoryPointDTO, ... ] }
     */
    public static List<HistoryPointDTO> fetchHistory(int gameId) throws Exception {
        return fetchHistory(gameId, "all");
    }

    public static List<HistoryPointDTO> fetchHistory(int gameId, String range) throws Exception {
        String safeRange = range == null || range.trim().isEmpty() ? "all" : range.trim();
        String body = doGet("/games/" + gameId + "/history?range=" + safeRange);
        JSONObject json = new JSONObject(body);
        JSONArray arr = json.getJSONArray("history");
        Type listType = new TypeToken<List<HistoryPointDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    /**
     * 添加一条历史价格记录（交易完成后调用）
     *
     * POST /api/v1/gold/games/{gameId}/history
     * Body: HistoryPointDTO
     */
    public static boolean addHistoryPoint(int gameId, HistoryPointDTO point) throws Exception {
        String body = doPost("/games/" + gameId + "/history", gson.toJson(point));
        JSONObject json = new JSONObject(body);
        return json.optBoolean("success", false);
    }

    // ==================== 个人总资产历史 API ====================

    /** 保存当前钱包总资产估值；后端按 5 分钟时间桶去重。 */
    public static boolean savePortfolioHistory(String userAddress, String totalValueWei,
                                               int activeMarketCount) throws Exception {
        PortfolioHistorySyncReq req = new PortfolioHistorySyncReq();
        req.userAddress = userAddress;
        req.totalValueWei = totalValueWei;
        req.activeMarketCount = activeMarketCount;
        String body = doPost("/portfolio-history", gson.toJson(req),
                FAST_WRITE_CONNECT_TIMEOUT_MS, FAST_WRITE_READ_TIMEOUT_MS);
        return new JSONObject(body).optBoolean("success", false);
    }

    /** 获取按时间升序排列的个人总资产估值历史。 */
    public static List<PortfolioHistoryPointDTO> fetchPortfolioHistory(String userAddress) throws Exception {
        String body = doGet("/portfolio-history?user_address=" + userAddress + "&limit=256");
        JSONArray arr = new JSONObject(body).getJSONArray("history");
        Type listType = new TypeToken<List<PortfolioHistoryPointDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    /** 获取 AI 智能决策中心的只读聚合数据。 */
    public static AiDecisionCenterDTO fetchAiDecisionCenter(String userAddress) throws Exception {
        String safeAddress = userAddress == null ? "" :
                java.net.URLEncoder.encode(userAddress, "UTF-8");
        String body = doGet("/ai-center?user_address=" + safeAddress);
        return gson.fromJson(body, AiDecisionCenterDTO.class);
    }

    // ==================== 交易同步 API ====================

    /**
     * 交易完成后同步状态到后端 DB
     * 调用时机：buyShares / sellShares / claimReward 交易确认后
     *
     * POST /api/v1/gold/trades/sync
     * Body: TradeSyncReq
     */
    public static boolean syncTrade(TradeSyncReq req) throws Exception {
        // 交易已经链上确认；缓存写入不应因后端抖动长时间阻塞成功反馈。
        String body = doPost("/trades/sync", gson.toJson(req),
                FAST_WRITE_CONNECT_TIMEOUT_MS, FAST_WRITE_READ_TIMEOUT_MS);
        JSONObject json = new JSONObject(body);
        return json.optBoolean("success", false);
    }

    // ==================== 交易历史 API ====================

    /**
     * 获取用户在指定博弈池的交易历史
     *
     * GET /api/v1/gold/trades?game_id=X&user_address=Y
     * Response: { "trades": [ TradeDTO, ... ] }
     */
    public static List<TradeDTO> fetchTradeHistory(int gameId, String userAddress) throws Exception {
        String path = String.format("/trades?game_id=%d&user_address=%s", gameId, userAddress);
        String body = doGet(path);
        JSONObject json = new JSONObject(body);
        JSONArray arr = json.getJSONArray("trades");
        Type listType = new TypeToken<List<TradeDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    // ==================== AI 托管状态 API ====================

    /**
     * 查询 AI 托管状态
     *
     * GET /api/v1/gold/ai-managed?game_id=X&user_address=Y&contract_address=Z
     */
    public static boolean getAiManagedStatus(int gameId, String userAddress, String contractAddress) throws Exception {
        return getAiManagedConfig(gameId, userAddress, contractAddress).enabled;
    }

    public static AiManagedConfig getAiManagedConfig(int gameId, String userAddress,
                                                      String contractAddress) throws Exception {
        String path = String.format("/ai-managed?game_id=%d&user_address=%s&contract_address=%s",
                gameId, userAddress, contractAddress);
        String body = doGet(path);
        JSONObject json = new JSONObject(body);
        AiManagedConfig config = AiManagedConfig.defaults();
        config.enabled = json.optBoolean("enabled", false);
        JSONObject strategy = json.optJSONObject("strategy");
        if (strategy != null) {
            config.strategyType = strategy.optString("strategy_type", config.strategyType);
            config.direction = strategy.optString("direction", config.direction);
            config.buyAmountBKC = strategy.optString("buy_amount_bkc", config.buyAmountBKC);
            config.confidenceMin = strategy.optDouble("confidence_min", config.confidenceMin);
            config.minEdgePercent = strategy.optDouble("min_edge_percent", config.minEdgePercent);
            config.kellyFraction = strategy.optDouble("kelly_fraction", config.kellyFraction);
            config.adaptiveCooldown = strategy.optBoolean(
                    "adaptive_cooldown", config.adaptiveCooldown);
            config.gridLowerPercent = strategy.optDouble(
                    "grid_lower_percent", config.gridLowerPercent);
            config.gridUpperPercent = strategy.optDouble(
                    "grid_upper_percent", config.gridUpperPercent);
            config.gridLevels = strategy.optInt("grid_levels", config.gridLevels);
            config.martingaleTriggerPercent = strategy.optDouble(
                    "martingale_trigger_percent", config.martingaleTriggerPercent);
            config.martingaleMultiplier = strategy.optDouble(
                    "martingale_multiplier", config.martingaleMultiplier);
            config.martingaleMaxRounds = strategy.optInt(
                    "martingale_max_rounds", config.martingaleMaxRounds);
        }
        return config;
    }

    /**
     * 设置 AI 托管状态
     *
     * POST /api/v1/gold/ai-managed
     * Body: { game_id, user_address, enabled, contract_address, private_key }
     */
    public static boolean setAiManagedStatus(int gameId, String userAddress, boolean enabled,
                                              String contractAddress, String privateKey) throws Exception {
        return setAiManagedConfig(gameId, userAddress, enabled, contractAddress, privateKey, null);
    }

    public static boolean setAiManagedConfig(int gameId, String userAddress, boolean enabled,
                                             String contractAddress, String privateKey,
                                             AiManagedConfig config) throws Exception {
        JSONObject json = new JSONObject();
        json.put("game_id", gameId);
        json.put("user_address", userAddress);
        json.put("enabled", enabled);
        json.put("contract_address", contractAddress);
        json.put("private_key", privateKey);
        if (enabled && config != null) {
            JSONObject strategy = new JSONObject();
            strategy.put("strategy_type", config.strategyType);
            strategy.put("direction", config.direction);
            strategy.put("buy_amount_bkc", config.buyAmountBKC);
            strategy.put("confidence_min", config.confidenceMin);
            strategy.put("min_edge_percent", config.minEdgePercent);
            strategy.put("kelly_fraction", config.kellyFraction);
            strategy.put("adaptive_cooldown", config.adaptiveCooldown);
            strategy.put("grid_lower_percent", config.gridLowerPercent);
            strategy.put("grid_upper_percent", config.gridUpperPercent);
            strategy.put("grid_levels", config.gridLevels);
            strategy.put("martingale_trigger_percent", config.martingaleTriggerPercent);
            strategy.put("martingale_multiplier", config.martingaleMultiplier);
            strategy.put("martingale_max_rounds", config.martingaleMaxRounds);
            json.put("strategy", strategy);
        }
        String body = doPost("/ai-managed", json.toString());
        JSONObject resp = new JSONObject(body);
        return resp.optBoolean("success", false);
    }

    public static class AiManagedConfig {
        public boolean enabled;
        @SerializedName("strategy_type")
        public String strategyType;
        public String direction;
        @SerializedName("buy_amount_bkc")
        public String buyAmountBKC;
        @SerializedName("confidence_min")
        public double confidenceMin;
        @SerializedName("min_edge_percent")
        public double minEdgePercent;
        @SerializedName("kelly_fraction")
        public double kellyFraction;
        @SerializedName("adaptive_cooldown")
        public boolean adaptiveCooldown;
        @SerializedName("grid_lower_percent")
        public double gridLowerPercent;
        @SerializedName("grid_upper_percent")
        public double gridUpperPercent;
        @SerializedName("grid_levels")
        public int gridLevels;
        @SerializedName("martingale_trigger_percent")
        public double martingaleTriggerPercent;
        @SerializedName("martingale_multiplier")
        public double martingaleMultiplier;
        @SerializedName("martingale_max_rounds")
        public int martingaleMaxRounds;

        public static AiManagedConfig defaults() {
            AiManagedConfig value = new AiManagedConfig();
            value.enabled = false;
            value.strategyType = "ai";
            value.direction = "yes";
            value.buyAmountBKC = "1";
            value.confidenceMin = 0.70d;
            value.minEdgePercent = 5d;
            value.kellyFraction = 0.25d;
            value.adaptiveCooldown = true;
            value.gridLowerPercent = 35d;
            value.gridUpperPercent = 65d;
            value.gridLevels = 6;
            value.martingaleTriggerPercent = 45d;
            value.martingaleMultiplier = 2d;
            value.martingaleMaxRounds = 4;
            return value;
        }

        public AiManagedConfig copy() {
            AiManagedConfig value = new AiManagedConfig();
            value.enabled = enabled;
            value.strategyType = strategyType;
            value.direction = direction;
            value.buyAmountBKC = buyAmountBKC;
            value.confidenceMin = confidenceMin;
            value.minEdgePercent = minEdgePercent;
            value.kellyFraction = kellyFraction;
            value.adaptiveCooldown = adaptiveCooldown;
            value.gridLowerPercent = gridLowerPercent;
            value.gridUpperPercent = gridUpperPercent;
            value.gridLevels = gridLevels;
            value.martingaleTriggerPercent = martingaleTriggerPercent;
            value.martingaleMultiplier = martingaleMultiplier;
            value.martingaleMaxRounds = martingaleMaxRounds;
            return value;
        }
    }

    public static List<StrategyDTO> fetchStrategies(String userAddress) throws Exception {
        String body = doGet("/strategies?user_address=" + userAddress);
        JSONObject json = new JSONObject(body);
        JSONArray arr = json.optJSONArray("strategies");
        if (arr == null) return new ArrayList<>();
        Type listType = new TypeToken<List<StrategyDTO>>(){}.getType();
        return gson.fromJson(arr.toString(), listType);
    }

    public static class StrategyDTO {
        @SerializedName("game_id")
        public int gameId;
        @SerializedName("contract_address")
        public String contractAddress;
        @SerializedName("enabled_at")
        public String enabledAt;
        @SerializedName("last_trade_at")
        public String lastTradeAt;
        @SerializedName("last_trade_tx")
        public String lastTradeTx;
        @SerializedName("last_error")
        public String lastError;
        @SerializedName("strategy")
        public AiManagedConfig strategy;
    }

    // ==================== DTO 定义 ====================

    /**
     * 游戏元数据 DTO（对应后端 DB 的 gold_games 表）
     */
    public static class GameMetaDTO {
        @SerializedName("game_id")
        public int gameId;

        @SerializedName("contract_address")
        public String contractAddress;

        @SerializedName("ipfs_cid")
        public String ipfsCid;

        @SerializedName("desc")
        public String desc;

        @SerializedName("condition")
        public String condition;

        @SerializedName("avatar_url")
        public String avatarUrl;

        @SerializedName("detailed_info")
        public String detailedInfo;

        @SerializedName("option_yes")
        public String optionYES;

        @SerializedName("option_no")
        public String optionNO;

        @SerializedName("creator_address")
        public String creatorAddress;

        @SerializedName("created_at")
        public String createdAt;
    }

    /**
     * 同步游戏元数据请求
     */
    public static class GameMetaSyncReq {
        @SerializedName("game_id")
        public int gameId;

        @SerializedName("contract_address")
        public String contractAddress;

        @SerializedName("ipfs_cid")
        public String ipfsCid;

        @SerializedName("desc")
        public String desc;

        @SerializedName("condition")
        public String condition;

        @SerializedName("avatar_url")
        public String avatarUrl;

        @SerializedName("detailed_info")
        public String detailedInfo;

        @SerializedName("option_yes")
        public String optionYES;

        @SerializedName("option_no")
        public String optionNO;

        @SerializedName("creator_address")
        public String creatorAddress;

        @SerializedName("duration_sec")
        public long durationSec;

        @SerializedName("initial_liquidity_wei")
        public String initialLiquidityWei;
    }

    /**
     * 链上状态缓存 DTO（由后端定时从链上同步）
     */
    public static class ChainStateDTO {
        @SerializedName("game_id")
        public int gameId;

        @SerializedName("contract_address")
        public String contractAddress;

        @SerializedName("total_pool")
        public String totalPool;

        @SerializedName("is_resolved")
        public boolean isResolved;

        @SerializedName("is_refunded")
        public boolean isRefunded;

        @SerializedName("winning_option")
        public int winningOption;

        @SerializedName("deadline_sec")
        public long deadlineSec;

        @SerializedName("reserve_yes")
        public String reserveYES;

        @SerializedName("reserve_no")
        public String reserveNO;

        @SerializedName("my_shares_yes")
        public String mySharesYES;

        @SerializedName("my_shares_no")
        public String mySharesNO;

        @SerializedName("total_liquidity_shares")
        public String totalLiquidityShares;

        @SerializedName("liquidity_fee_pool")
        public String liquidityFeePool;

        @SerializedName("my_liquidity_shares")
        public String myLiquidityShares;

        @SerializedName("my_liquidity_fees")
        public String myLiquidityFees;

        @SerializedName("updated_at")
        public String updatedAt;
    }

    /**
     * 链上状态同步请求
     */
    public static class ChainStateSyncReq {
        @SerializedName("contract_address")
        public String contractAddress;

        @SerializedName("user_address")
        public String userAddress;

        @SerializedName("total_pool")
        public String totalPool;

        @SerializedName("is_resolved")
        public boolean isResolved;

        @SerializedName("is_refunded")
        public boolean isRefunded;

        @SerializedName("winning_option")
        public int winningOption;

        @SerializedName("deadline_sec")
        public long deadlineSec;

        @SerializedName("reserve_yes")
        public String reserveYES;

        @SerializedName("reserve_no")
        public String reserveNO;

        @SerializedName("my_shares_yes")
        public String mySharesYES;

        @SerializedName("my_shares_no")
        public String mySharesNO;

        @SerializedName("total_liquidity_shares")
        public String totalLiquidityShares;

        @SerializedName("liquidity_fee_pool")
        public String liquidityFeePool;

        @SerializedName("my_liquidity_shares")
        public String myLiquidityShares;

        @SerializedName("my_liquidity_fees")
        public String myLiquidityFees;
    }

    /**
     * 历史价格点 DTO
     */
    public static class HistoryPointDTO {
        @SerializedName("game_id")
        public int gameId;

        @SerializedName("timestamp_sec")
        public long timestampSec;

        @SerializedName("yes_price")
        public float yesPrice;

        @SerializedName("no_price")
        public float noPrice;

        @SerializedName("total_pool")
        public String totalPool;
    }

    public static class PortfolioHistoryPointDTO {
        @SerializedName("timestamp_sec")
        public long timestampSec;

        @SerializedName("total_value_wei")
        public String totalValueWei;

        @SerializedName("active_market_count")
        public int activeMarketCount;
    }

    private static class PortfolioHistorySyncReq {
        @SerializedName("user_address")
        String userAddress;

        @SerializedName("total_value_wei")
        String totalValueWei;

        @SerializedName("active_market_count")
        int activeMarketCount;
    }

    /**
     * 交易历史记录 DTO（从后端查询用户交易历史）
     */
    public static class TradeDTO {
        @SerializedName("trade_type")
        public String tradeType; // "BUY", "SELL", "CLAIM"

        @SerializedName("option_id")
        public int optionId;

        @SerializedName("amount_wei")
        public String amountWei;

        @SerializedName("share_amount_wei")
        public String shareAmountWei;

        @SerializedName("returned_yes_wei")
        public String returnedYesWei;

        @SerializedName("returned_no_wei")
        public String returnedNoWei;

        @SerializedName("is_success")
        public boolean isSuccess;

        @SerializedName("is_ai_managed")
        public boolean isAiManaged;

        @SerializedName("execution_source")
        public String executionSource;

        @SerializedName("tx_hash")
        public String txHash;

        @SerializedName("timestamp_sec")
        public long timestampSec;

        @SerializedName("created_at")
        public String createdAt;

        @SerializedName("my_shares_yes_after")
        public String mySharesYESAfter;

        @SerializedName("my_shares_no_after")
        public String mySharesNOAfter;

        @SerializedName("total_liquidity_shares_after")
        public String totalLiquiditySharesAfter;

        @SerializedName("liquidity_fee_pool_after")
        public String liquidityFeePoolAfter;

        @SerializedName("my_liquidity_shares_after")
        public String myLiquiditySharesAfter;

        @SerializedName("my_liquidity_fees_after")
        public String myLiquidityFeesAfter;
    }

    /**
     * 交易同步请求
     */
    public static class TradeSyncReq {
        @SerializedName("game_id")
        public int gameId;

        @SerializedName("contract_address")
        public String contractAddress;

        @SerializedName("user_address")
        public String userAddress;

        @SerializedName("trade_type")
        public String tradeType; // "BUY", "SELL", "CLAIM"

        @SerializedName("option_id")
        public int optionId;

        @SerializedName("amount_wei")
        public String amountWei;

        @SerializedName("tx_hash")
        public String txHash;

        @SerializedName("is_success")
        public boolean isSuccess;

        @SerializedName("timestamp_sec")
        public long timestampSec;

        @SerializedName("share_amount_wei")
        public String shareAmountWei;

        @SerializedName("returned_yes_wei")
        public String returnedYesWei;

        @SerializedName("returned_no_wei")
        public String returnedNoWei;

        @SerializedName("is_ai_managed")
        public boolean isAiManaged;

        @SerializedName("execution_source")
        public String executionSource;

        // 同步当前链上状态
        @SerializedName("total_pool_after")
        public String totalPoolAfter;

        @SerializedName("reserve_yes_after")
        public String reserveYESAfter;

        @SerializedName("reserve_no_after")
        public String reserveNOAfter;

        @SerializedName("my_shares_yes_after")
        public String mySharesYESAfter;

        @SerializedName("my_shares_no_after")
        public String mySharesNOAfter;

        @SerializedName("total_liquidity_shares_after")
        public String totalLiquiditySharesAfter;

        @SerializedName("liquidity_fee_pool_after")
        public String liquidityFeePoolAfter;

        @SerializedName("my_liquidity_shares_after")
        public String myLiquiditySharesAfter;

        @SerializedName("my_liquidity_fees_after")
        public String myLiquidityFeesAfter;
    }

    public static class AiDecisionCenterDTO {
        @SerializedName("generated_at") public String generatedAt;
        @SerializedName("cache") public AiCenterCacheDTO cache;
        @SerializedName("stats") public AiCenterStatsDTO stats;
        @SerializedName("managed_decisions") public List<AiManagedDecisionDTO> managedDecisions = new ArrayList<>();
        @SerializedName("settlement_audits") public List<AiSettlementAuditDTO> settlementAudits = new ArrayList<>();
        @SerializedName("opportunities") public List<AiOpportunityDTO> opportunities = new ArrayList<>();
    }

    public static class AiCenterCacheDTO {
        @SerializedName("enabled") public boolean enabled;
        @SerializedName("hit") public boolean hit;
        @SerializedName("mode") public String mode;
        @SerializedName("ttl_seconds") public long ttlSeconds;
    }

    public static class AiCenterStatsDTO {
        @SerializedName("managed_markets") public int managedMarkets;
        @SerializedName("recent_decisions") public int recentDecisions;
        @SerializedName("settled_audits") public int settledAudits;
        @SerializedName("opportunities") public int opportunities;
    }

    public static class AiManagedDecisionDTO implements java.io.Serializable {
        @SerializedName("id") public long id;
        @SerializedName("game_id") public int gameId;
        @SerializedName("contract_address") public String contractAddress;
        @SerializedName("market_title") public String marketTitle;
        @SerializedName("observed_at") public long observedAt;
        @SerializedName("action") public String action;
        @SerializedName("confidence") public double confidence;
        @SerializedName("estimated_prob_yes") public double estimatedProbYES;
        @SerializedName("market_prob_yes") public double marketProbYES;
        @SerializedName("probability_edge_percent") public double probabilityEdgePercent;
        @SerializedName("reason") public String reason;
        @SerializedName("history_points") public int historyPoints;
        @SerializedName("outcome") public String outcome;
        @SerializedName("tx_hash") public String txHash;
        @SerializedName("error_summary") public String errorSummary;
    }

    public static class AiModelOpinionDTO implements java.io.Serializable {
        @SerializedName("model_name") public String modelName;
        @SerializedName("model_id") public String modelId;
        @SerializedName("decision") public String decision;
        @SerializedName("confidence") public double confidence;
        @SerializedName("reasoning") public String reasoning;
        @SerializedName("error") public String error;
        @SerializedName("is_final") public boolean isFinal;
    }

    public static class AiSettlementAuditDTO implements java.io.Serializable {
        @SerializedName("id") public long id;
        @SerializedName("game_id") public int gameId;
        @SerializedName("contract_address") public String contractAddress;
        @SerializedName("market_title") public String marketTitle;
        @SerializedName("rule_summary") public String ruleSummary;
        @SerializedName("deterministic_candidate") public String deterministicCandidate;
        @SerializedName("opinions") public List<AiModelOpinionDTO> opinions = new ArrayList<>();
        @SerializedName("final_decision") public String finalDecision;
        @SerializedName("final_confidence") public double finalConfidence;
        @SerializedName("consensus_ratio") public double consensusRatio;
        @SerializedName("final_summary") public String finalSummary;
        @SerializedName("resolved_at") public String resolvedAt;
    }

    public static class AiOpportunityDTO implements java.io.Serializable {
        @SerializedName("decision_id") public long decisionId;
        @SerializedName("game_id") public int gameId;
        @SerializedName("contract_address") public String contractAddress;
        @SerializedName("market_title") public String marketTitle;
        @SerializedName("side") public String side;
        @SerializedName("confidence") public double confidence;
        @SerializedName("estimated_probability") public double estimatedProbability;
        @SerializedName("market_probability") public double marketProbability;
        @SerializedName("edge_percent") public double edgePercent;
        @SerializedName("liquidity_bkc") public double liquidityBKC;
        @SerializedName("deadline_sec") public long deadlineSec;
        @SerializedName("reason") public String reason;
        @SerializedName("observed_at") public long observedAt;
    }
}
