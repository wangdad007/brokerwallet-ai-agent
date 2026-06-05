package com.example.brokerfi.xc.agent.gold.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.brokerfi.BuildConfig;
import com.example.brokerfi.xc.agent.gold.logic.GoldMarketSecurityPolicy;

import org.json.JSONObject;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GoldMarketRepository {
    private static final String TAG = "GoldMarketRepo";

    private static final String PREFS_NAME = "gold_market_prefs";
    private static final String KEY_CONTRACT_ADDR = "contract_address";
    private static final String KEY_CONTRACT_ADDRS = "contract_addresses";
    private static final String KEY_RPC_URL = "rpc_url";
    private static final BigDecimal WEI_PER_BKC = new BigDecimal("1000000000000000000");
    private static final BigInteger LOCAL_RPC_CALL_GAS_LIMIT = new BigInteger("5000000");
    private static final BigInteger LOCAL_RPC_CALL_GAS_PRICE = BigInteger.ZERO;
    private static final BigInteger LOCAL_RPC_CALL_VALUE = BigInteger.ZERO;
    public static final int GOLD_GAME_ID = 1;

    private static List<String> cachedAddresses;

    private final String privateKey;
    private final String contractAddress;
    private final boolean useLocalRpc;
    private final Web3j web3j;
    private final Credentials credentials;
    private final String walletAddress;
    private final boolean developerMarketToolsEnabled;

    // ── config ──

    public static String getContractAddress(Context ctx) {
        return getContractAddresses(ctx).get(0);
    }

    public static List<String> getContractAddresses(Context ctx) {
        boolean developerToolsEnabled = GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG);
        if (cachedAddresses != null && developerToolsEnabled) return new ArrayList<>(cachedAddresses);
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_CONTRACT_ADDRS, null);
        if (saved == null || saved.trim().isEmpty()) {
            saved = prefs.getString(KEY_CONTRACT_ADDR, null);
        }
        cachedAddresses = GoldMarketSecurityPolicy.resolveContractAddresses(developerToolsEnabled, saved);
        return new ArrayList<>(cachedAddresses);
    }

    static List<String> parseContractAddresses(String rawAddresses) {
        return GoldMarketSecurityPolicy.parseContractAddresses(rawAddresses);
    }

    public static void setContractAddress(Context ctx, String address) {
        if (!GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG)) return;
        setContractAddresses(ctx, Collections.singletonList(address));
    }

    public static void setContractAddresses(Context ctx, List<String> addresses) {
        if (!GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG)) return;
        StringBuilder joined = new StringBuilder();
        List<String> validAddresses = new ArrayList<>();
        for (String address : addresses) {
            if (!GoldMarketSecurityPolicy.isValidContractAddress(address)) continue;
            validAddresses.add(address.trim());
            if (joined.length() > 0) joined.append('\n');
            joined.append(address.trim());
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONTRACT_ADDRS, joined.toString())
                .putString(KEY_CONTRACT_ADDR, validAddresses.isEmpty() ? "" : validAddresses.get(0))
                .apply();
        cachedAddresses = GoldMarketSecurityPolicy.resolveContractAddresses(true, joined.toString());
    }

    public static String getRpcUrl(Context ctx) {
        String saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RPC_URL, "");
        return GoldMarketSecurityPolicy.resolveRpcUrl(
                GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG), saved);
    }

    public static void setRpcUrl(Context ctx, String url) {
        if (!GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG)) return;
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_RPC_URL, url).apply();
    }

    // ── constructor ──

    public GoldMarketRepository(Context ctx, String privateKey) {
        this(ctx, privateKey, getContractAddress(ctx));
    }

    public GoldMarketRepository(Context ctx, String privateKey, String contractAddress) {
        this.privateKey = privateKey;
        this.developerMarketToolsEnabled = GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG);
        this.contractAddress = contractAddress == null ? getContractAddress(ctx) : contractAddress.trim();
        String rpcUrl = getRpcUrl(ctx);
        this.useLocalRpc = rpcUrl != null && !rpcUrl.isEmpty();
        if (useLocalRpc) {
            Log.d(TAG, "LocalRPC mode: url=" + rpcUrl + " wallet=" + Credentials.create(privateKey).getAddress());
            this.web3j = Web3j.build(new HttpService(rpcUrl));
            this.credentials = Credentials.create(privateKey);
            this.walletAddress = credentials.getAddress();
        } else {
            this.web3j = null;
            this.credentials = null;
            this.walletAddress = BrokerChainClient.getAddress(privateKey);
        }
    }

    public String getWalletAddress() {
        return walletAddress != null ? walletAddress : "";
    }

    public String getBoundContractAddress() {
        return contractAddress;
    }

    // ── callbacks ──

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface TxCallback {
        void onTxSent(String txHash);
        void onConfirmed(String message);
        void onError(String error);
    }

    public static BigInteger parseTokenAmountToWei(String amountText) {
        if (amountText == null) return null;
        String normalized = amountText.trim();
        if (normalized.isEmpty()) return null;
        try {
            BigDecimal amount = new BigDecimal(normalized);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
            BigInteger wei = amount.multiply(WEI_PER_BKC).toBigIntegerExact();
            return wei.compareTo(BigInteger.ZERO) > 0 ? wei : null;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    public static org.web3j.abi.datatypes.Function buildClaimRewardFunction(int gameId, int optionId) {
        return new org.web3j.abi.datatypes.Function(
            "claimReward",
            Arrays.asList(new Uint256(gameId), new Uint8(optionId)),
            Collections.emptyList());
    }

    public static org.web3j.abi.datatypes.Function buildGameCountFunction() {
        return new org.web3j.abi.datatypes.Function(
            "gameCount",
            Collections.emptyList(),
            Collections.singletonList(new TypeReference<Uint256>() {}));
    }

    // ── RPC transport ──

    static Transaction buildLocalEthCallTransaction(String from, String to, String data) {
        return Transaction.createFunctionCallTransaction(
                from,
                null,
                LOCAL_RPC_CALL_GAS_PRICE,
                LOCAL_RPC_CALL_GAS_LIMIT,
                to,
                LOCAL_RPC_CALL_VALUE,
                data);
    }

    static Transaction buildLocalWriteTransaction(String from, String to, String data, BigInteger value) {
        return Transaction.createFunctionCallTransaction(
                from,
                null,
                LOCAL_RPC_CALL_GAS_PRICE,
                LOCAL_RPC_CALL_GAS_LIMIT,
                to,
                value,
                data);
    }

    private String ethCall(org.web3j.abi.datatypes.Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        if (useLocalRpc) {
            Log.d(TAG, "standard ethCall to=" + contractAddress + " data=" + data.substring(0, Math.min(66, data.length())) + "...");
            Transaction txn = buildLocalEthCallTransaction(walletAddress, contractAddress, data);
            EthCall resp = web3j.ethCall(txn, DefaultBlockParameterName.LATEST).send();
            Log.d(TAG, "standard ethCall result: hasError=" + resp.hasError() + " value=" + resp.getValue());
            if (resp.hasError()) throw new Exception(resp.getError().getMessage());
            String value = resp.getValue();
            if (value == null || value.isEmpty()) {
                throw new Exception("BrokerChain local RPC returned empty eth_call result; check call fields and game id");
            }
            return value;
        } else {
            String response = BrokerChainClient.sendEthCall(privateKey, contractAddress, data);
            Log.d(TAG, "ethCall response: " + (response != null ? response.substring(0, Math.min(200, response.length())) : "null"));
            return extractHexResult(response);
        }
    }

    private void sendTransaction(BigInteger value, org.web3j.abi.datatypes.Function function,
                                 String successMsg, TxCallback callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                String data = FunctionEncoder.encode(function);
                if (useLocalRpc) {
                    standardSendTx(value, data, successMsg, callback);
                } else {
                    brokerChainSendTx(value, data, successMsg, callback);
                }
            } catch (Exception e) {
                postError(callback, "交易异常: " + e.getMessage());
            }
        });
    }

    private void standardSendTx(BigInteger value, String data, String successMsg, TxCallback callback) throws Exception {
        Transaction txn = buildLocalWriteTransaction(walletAddress, contractAddress, data, value);
        Log.d(TAG, "standard eth_sendTransaction to=" + contractAddress
                + " value=" + txn.getValue()
                + " data=" + data.substring(0, Math.min(66, data.length())) + "...");
        EthSendTransaction resp = web3j.ethSendTransaction(txn).send();
        if (resp.hasError()) {
            postError(callback, resp.getError().getMessage());
        } else if (resp.getTransactionHash() == null || resp.getTransactionHash().isEmpty()) {
            postError(callback, "本地 RPC 未返回交易哈希，交易未确认提交");
        } else {
            String txHash = resp.getTransactionHash();
            Log.d(TAG, "standard eth_sendTransaction hash=" + txHash);
            AppExecutors.getInstance().mainThread().execute(() -> {
                callback.onTxSent(txHash);
            });
            waitForLocalReceipt(txHash);
            AppExecutors.getInstance().mainThread().execute(() -> {
                callback.onConfirmed(successMsg);
            });
        }
    }

    private void waitForLocalReceipt(String txHash) throws Exception {
        for (int i = 0; i < 12; i++) {
            Thread.sleep(2500);
            EthGetTransactionReceipt receiptResp = web3j.ethGetTransactionReceipt(txHash).send();
            if (receiptResp.hasError()) {
                throw new Exception(receiptResp.getError().getMessage());
            }
            Optional<TransactionReceipt> receipt = receiptResp.getTransactionReceipt();
            if (receipt.isPresent()) {
                String status = receipt.get().getStatus();
                if ("0x0".equals(status)) {
                    throw new Exception("交易执行失败，链上 receipt status=0x0");
                }
                Log.d(TAG, "local tx confirmed: " + txHash + " status=" + status);
                return;
            }
        }
        throw new Exception("交易已提交但 30 秒内未确认，请稍后手动刷新");
    }

    private void brokerChainSendTx(BigInteger value, String data, String successMsg, TxCallback callback) throws Exception {
        String valueHex = value.compareTo(BigInteger.ZERO) > 0 ? value.toString(16) : "0x0";
        String response = BrokerChainClient.sendEthTx(privateKey, contractAddress, data, valueHex);
        if (response == null || response.toLowerCase().contains("error") || response.toLowerCase().contains("failed")) {
            postError(callback, "交易失败: " + response);
        } else {
            AppExecutors.getInstance().mainThread().execute(() -> {
                callback.onTxSent("Transaction Sent");
                callback.onConfirmed(successMsg);
            });
        }
    }

    private String extractHexResult(String responseJson) {
        if (responseJson == null || responseJson.isEmpty()) { Log.w(TAG, "extractHexResult: null/empty"); return "0x"; }
        try {
            if (responseJson.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(responseJson);
                String res = "0x";
                if (obj.has("result")) res = obj.getString("result");
                else if (obj.has("data")) res = obj.getString("data");
                else Log.w(TAG, "extractHexResult: no result/data. Keys: " + obj.keys());
                if (res.toLowerCase().contains("reverted") || res.toLowerCase().contains("error")) return "0x";
                return res;
            }
            if (responseJson.toLowerCase().contains("reverted")) return "0x";
            return responseJson.trim();
        } catch (Exception e) { return "0x"; }
    }

    // ── contract methods ──

    public void getGameCount(DataCallback<Integer> callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                org.web3j.abi.datatypes.Function function = buildGameCountFunction();
                String hex = ethCall(function);
                List<Type> result = FunctionReturnDecoder.decode(hex, function.getOutputParameters());
                if (result.isEmpty()) {
                    postError(callback, "市场数量解析为空");
                    return;
                }
                int count = ((Uint256) result.get(0)).getValue().intValue();
                AppExecutors.getInstance().mainThread().execute(() -> callback.onSuccess(count));
            } catch (Exception e) {
                postError(callback, "获取市场数量异常: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public void getGameInfo(int id, DataCallback<GameModel> callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                org.web3j.abi.datatypes.Function fInfo = new org.web3j.abi.datatypes.Function(
                    "getGameInfo", Collections.singletonList(new Uint256(id)),
                    Arrays.asList(
                        new TypeReference<Utf8String>() {}, new TypeReference<Utf8String>() {},
                        new TypeReference<Utf8String>() {}, new TypeReference<Utf8String>() {},
                        new TypeReference<DynamicArray<Utf8String>>() {}, new TypeReference<Uint8>() {},
                        new TypeReference<Uint256>() {}, new TypeReference<Bool>() {},
                        new TypeReference<Uint8>() {}, new TypeReference<Uint256>() {},
                        new TypeReference<Bool>() {}
                    ));

                String infoHex = ethCall(fInfo);
                Log.d(TAG, "getGameInfo(" + id + ") infoHex=" + infoHex + " len=" + (infoHex != null ? infoHex.length() : 0));
                if (infoHex == null || infoHex.equals("0x")) {
                    Log.e(TAG, "getGameInfo failed: contract may not exist at " + contractAddress);
                    postError(callback, "获取市场信息失败（合约: " + contractAddress + "）");
                    return;
                }
                List<Type> res = FunctionReturnDecoder.decode(infoHex, fInfo.getOutputParameters());
                if (res.isEmpty()) { postError(callback, "数据解析为空"); return; }

                GameModel model = new GameModel();
                model.id = id;
                model.contractAddress = contractAddress;
                model.desc = ((Utf8String) res.get(0)).getValue();
                model.condition = ((Utf8String) res.get(1)).getValue();
                model.avatarUrl = ((Utf8String) res.get(2)).getValue();
                model.detailedInfo = ((Utf8String) res.get(3)).getValue();

                List<Utf8String> namesList = ((DynamicArray<Utf8String>) res.get(4)).getValue();
                model.optionNames = new ArrayList<>();
                for (Utf8String u : namesList) model.optionNames.add(u.getValue());

                model.optionCount = ((Uint8) res.get(5)).getValue().intValue();
                model.totalPool = ((Uint256) res.get(6)).getValue();
                model.isResolved = ((Bool) res.get(7)).getValue();
                model.winningOption = ((Uint8) res.get(8)).getValue().intValue();
                model.deadlineSec = ((Uint256) res.get(9)).getValue().longValue();
                model.isRefunded = ((Bool) res.get(10)).getValue();

                String addr = getWalletAddress();
                if (addr == null || addr.isEmpty() || addr.equals("0x")) {
                    postError(callback, "无法获取钱包地址"); return;
                }
                org.web3j.abi.datatypes.Function fExtra = new org.web3j.abi.datatypes.Function(
                    "getGameExtraData",
                    Arrays.asList(new Uint256(id), new Address(addr)),
                    Arrays.asList(
                        new TypeReference<DynamicArray<Uint256>>() {},
                        new TypeReference<DynamicArray<Uint256>>() {}
                    ));

                String extraHex = ethCall(fExtra);
                List<Type> extraRes = FunctionReturnDecoder.decode(extraHex, fExtra.getOutputParameters());
                List<Uint256> reservesArray = ((DynamicArray<Uint256>) extraRes.get(0)).getValue();
                List<Uint256> sharesArray = ((DynamicArray<Uint256>) extraRes.get(1)).getValue();

                model.virtualReserves = new ArrayList<>();
                model.myShares = new ArrayList<>();
                for (int opt = 0; opt < model.optionCount; opt++) {
                    model.virtualReserves.add(reservesArray.get(opt).getValue());
                    model.myShares.add(sharesArray.get(opt).getValue());
                }

                AppExecutors.getInstance().mainThread().execute(() -> callback.onSuccess(model));
            } catch (Exception e) {
                postError(callback, "获取详情异常: " + e.getMessage());
            }
        });
    }

    public void buyShares(int gameId, int optionId, BigInteger amountWei, TxCallback callback) {
        org.web3j.abi.datatypes.Function f = new org.web3j.abi.datatypes.Function(
            "buyShares", Arrays.asList(new Uint256(gameId), new Uint8(optionId)), Collections.emptyList());
        sendTransaction(amountWei, f, "买入成功", callback);
    }

    public void sellShares(int gameId, int optionId, BigInteger shareAmount, TxCallback callback) {
        org.web3j.abi.datatypes.Function f = new org.web3j.abi.datatypes.Function(
            "sellShares", Arrays.asList(new Uint256(gameId), new Uint8(optionId), new Uint256(shareAmount)), Collections.emptyList());
        sendTransaction(BigInteger.ZERO, f, "卖出成功", callback);
    }

    public void createGame(String desc, String condition, String avatarUrl,
                           String detailedInfo, List<String> optionNamesList,
                           long durationSec, TxCallback callback) {
        List<Utf8String> utf8Options = new ArrayList<>();
        for (String name : optionNamesList) utf8Options.add(new Utf8String(name));
        org.web3j.abi.datatypes.Function f = new org.web3j.abi.datatypes.Function(
            "createGame", Arrays.asList(
                new Utf8String(desc), new Utf8String(condition),
                new Utf8String(avatarUrl), new Utf8String(detailedInfo),
                new DynamicArray<>(utf8Options), new Uint256(durationSec)),
            Collections.emptyList());
        sendTransaction(BigInteger.ZERO, f, "博弈池创建成功", callback);
    }

    /**
     * 获取水贝模式下的实时点差。
     * 模拟深圳水贝黄金交易：买入价稍高于基准，卖出价稍低于基准。
     */
    public BigDecimal calculateShuibeiPrice(BigDecimal basePrice, boolean isBuy) {
        BigDecimal spread = new BigDecimal("0.005"); // 0.5% 点差
        if (isBuy) {
            return basePrice.multiply(BigDecimal.ONE.add(spread));
        } else {
            return basePrice.multiply(BigDecimal.ONE.subtract(spread));
        }
    }

    public void claimReward(int gameId, int optionId, TxCallback callback) {
        sendTransaction(BigInteger.ZERO, buildClaimRewardFunction(gameId, optionId), "领取成功", callback);
    }

    private void postError(TxCallback callback, String error) {
        AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
    }
    private <T> void postError(DataCallback<T> callback, String error) {
        AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
    }

    public static class GameModel {
        public int id;
        public String contractAddress;
        public String desc, condition, avatarUrl, detailedInfo;
        public List<String> optionNames;
        public int optionCount;
        public BigInteger totalPool;
        public boolean isResolved, isRefunded;
        public int winningOption;
        public long deadlineSec;
        public List<BigInteger> virtualReserves, myShares;
    }
}
