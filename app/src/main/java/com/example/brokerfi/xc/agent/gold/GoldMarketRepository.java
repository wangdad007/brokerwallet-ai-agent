package com.example.brokerfi.xc.agent.gold;

import android.util.Log;

import org.json.JSONObject;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GoldMarketRepository {
    private static final String TAG = "GoldMarketRepo";

    private static final String CONTRACT_ADDRESS = "0xf8e81D47203A594245E36C48e151709F0C19fBe8";
    public static final int GOLD_GAME_ID = 1;

    private final String privateKey;

    public GoldMarketRepository(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getWalletAddress() {
        return BrokerChainClient.getAddress(privateKey);
    }

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface TxCallback {
        void onTxSent(String txHash);
        void onConfirmed(String message);
        void onError(String error);
    }

    private String extractHexResult(String responseJson) {
        if (responseJson == null || responseJson.isEmpty()) return "0x";
        try {
            if (responseJson.trim().startsWith("{")) {
                JSONObject obj = new JSONObject(responseJson);
                String res = "0x";
                if (obj.has("result")) {
                    res = obj.getString("result");
                } else if (obj.has("data")) {
                    res = obj.getString("data");
                }
                if (res.toLowerCase().contains("reverted") || res.toLowerCase().contains("error")) {
                    return "0x";
                }
                return res;
            }
            if (responseJson.toLowerCase().contains("reverted")) return "0x";
            return responseJson.trim();
        } catch (Exception e) {
            return "0x";
        }
    }

    private String ethCall(org.web3j.abi.datatypes.Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        String response = BrokerChainClient.sendEthCall(privateKey, CONTRACT_ADDRESS, data);
        return extractHexResult(response);
    }

    public void sendTransaction(BigInteger value, org.web3j.abi.datatypes.Function function,
                                 String successMsg, TxCallback callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                String data = FunctionEncoder.encode(function);
                String valueHex = value.compareTo(BigInteger.ZERO) > 0
                        ? value.toString(16) : "0x0";
                String response = BrokerChainClient.sendEthTx(
                        privateKey, CONTRACT_ADDRESS, data, valueHex);

                if (response == null || response.toLowerCase().contains("error")
                        || response.toLowerCase().contains("failed")) {
                    postError(callback, "交易失败: " + response);
                } else {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        callback.onTxSent("Transaction Sent");
                        callback.onConfirmed(successMsg);
                    });
                }
            } catch (Exception e) {
                postError(callback, "交易异常: " + e.getMessage());
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
                if (infoHex == null || infoHex.equals("0x")) {
                    postError(callback, "获取市场信息失败");
                    return;
                }
                List<Type> res = FunctionReturnDecoder.decode(infoHex, fInfo.getOutputParameters());
                if (res.isEmpty()) {
                    postError(callback, "数据解析为空");
                    return;
                }

                GameModel model = new GameModel();
                model.id = id;
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

                org.web3j.abi.datatypes.Function fExtra = new org.web3j.abi.datatypes.Function(
                    "getGameExtraData",
                    Arrays.asList(new Uint256(id), new Address(getWalletAddress())),
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
            "buyShares",
            Arrays.asList(new Uint256(gameId), new Uint8(optionId)),
            Collections.emptyList());
        sendTransaction(amountWei, f, "买入成功", callback);
    }

    public void sellShares(int gameId, int optionId, BigInteger shareAmount, TxCallback callback) {
        org.web3j.abi.datatypes.Function f = new org.web3j.abi.datatypes.Function(
            "sellShares",
            Arrays.asList(new Uint256(gameId), new Uint8(optionId), new Uint256(shareAmount)),
            Collections.emptyList());
        sendTransaction(BigInteger.ZERO, f, "卖出成功", callback);
    }

    public void claimReward(int gameId, TxCallback callback) {
        org.web3j.abi.datatypes.Function f = new org.web3j.abi.datatypes.Function(
            "claimReward",
            Collections.singletonList(new Uint256(gameId)),
            Collections.emptyList());
        sendTransaction(BigInteger.ZERO, f, "领取成功", callback);
    }

    private void postError(TxCallback callback, String error) {
        AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
    }

    private <T> void postError(DataCallback<T> callback, String error) {
        AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
    }

    public static class GameModel {
        public int id;
        public String desc;
        public String condition;
        public String avatarUrl;
        public String detailedInfo;
        public List<String> optionNames;
        public int optionCount;
        public BigInteger totalPool;
        public boolean isResolved;
        public int winningOption;
        public long deadlineSec;
        public boolean isRefunded;
        public List<BigInteger> virtualReserves;
        public List<BigInteger> myShares;
    }
}
