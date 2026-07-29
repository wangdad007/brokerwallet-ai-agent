package com.example.brokerfi.xc.agent.gold.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.data.AppExecutors;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchPromptBuilder;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchAnalysisPresenter;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GoldMarketDetailViewModel extends AndroidViewModel {
    private final Application application;
    private final String privateKey;
    private final GoldMarketRepository repository;
    private final MutableLiveData<GoldMarketRepository.GameModel> currentGame = new MutableLiveData<>();
    private final MutableLiveData<String> marketAiSummary = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> tradeError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> txStatus = new MutableLiveData<>();
    private final MutableLiveData<String> debugToast = new MutableLiveData<>();
    private final MutableLiveData<ChartData> chartData = new MutableLiveData<>();
    private final MutableLiveData<BackendApiClient.AiManagedConfig> aiManagedConfig =
            new MutableLiveData<>(BackendApiClient.AiManagedConfig.defaults());
    private final AtomicBoolean gameInfoRequestInFlight = new AtomicBoolean(false);
    private final AtomicInteger chartRequestSequence = new AtomicInteger(0);

    private String marketAiContext = "";
    private String selectedChartRange = "1d";

    public static final class ChartData {
        public final int gameId;
        public final String range;
        public final List<BackendApiClient.HistoryPointDTO> history;
        public final List<BackendApiClient.TradeDTO> trades;

        ChartData(int gameId, String range,
                  List<BackendApiClient.HistoryPointDTO> history,
                  List<BackendApiClient.TradeDTO> trades) {
            this.gameId = gameId;
            this.range = range;
            this.history = history;
            this.trades = trades;
        }
    }

    public GoldMarketDetailViewModel(@NonNull Application application) {
        super(application);
        this.application = application;
        this.privateKey = StorageUtil.getCurrentPrivatekey(application);
        repository = new GoldMarketRepository(application, privateKey);
    }

    private GoldMarketRepository repositoryFor(String contractAddress) {
        if (contractAddress == null || contractAddress.trim().isEmpty()) {
            return repository;
        }
        return new GoldMarketRepository(application, privateKey, contractAddress);
    }

    public LiveData<GoldMarketRepository.GameModel> getCurrentGame() { return currentGame; }
    public LiveData<String> getMarketAiSummary() { return marketAiSummary; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getTradeError() { return tradeError; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getTxStatus() { return txStatus; }
    public LiveData<String> getDebugToast() { return debugToast; }
    public LiveData<ChartData> getChartData() { return chartData; }
    public LiveData<BackendApiClient.AiManagedConfig> getAiManagedConfig() { return aiManagedConfig; }
    public String getMarketAiContext() { return marketAiContext; }
    public String getWalletAddress() { return repository.getWalletAddress(); }

    public void loadChartData(int gameId, String range) {
        selectedChartRange = range == null || range.trim().isEmpty() ? "1d" : range;
        final String requestRange = selectedChartRange;
        final int requestSequence = chartRequestSequence.incrementAndGet();
        AppExecutors.getInstance().networkIO().execute(() -> {
            List<BackendApiClient.HistoryPointDTO> history;
            List<BackendApiClient.TradeDTO> trades = Collections.emptyList();
            try {
                history = BackendApiClient.fetchHistory(gameId, requestRange);
            } catch (Exception e) {
                if (requestSequence == chartRequestSequence.get()) {
                    error.postValue("Unable to load chart data: " + e.getMessage());
                }
                return;
            }
            try {
                String wallet = getWalletAddress();
                if (wallet != null && !wallet.trim().isEmpty()) {
                    trades = BackendApiClient.fetchTradeHistory(gameId, wallet);
                }
            } catch (Exception ignored) {
                // The market chart remains useful even when personal trade markers are unavailable.
            }
            if (requestSequence == chartRequestSequence.get()) {
                chartData.postValue(new ChartData(gameId, requestRange, history, trades));
            }
        });
    }

    public void loadGameInfo(int gameId) {
        loadGameInfo(gameId, null);
    }

    public void loadGameInfo(int gameId, String contractAddress) {
        loadGameInfo(gameId, contractAddress, true);
    }

    public void refreshGameInfo(int gameId, String contractAddress) {
        loadGameInfo(gameId, contractAddress, false);
    }

    private void loadGameInfo(int gameId, String contractAddress, boolean showLoading) {
        if (!gameInfoRequestInFlight.compareAndSet(false, true)) return;
        if (showLoading) isLoading.setValue(true);
        GoldMarketRepository activeRepository = repositoryFor(contractAddress);
        activeRepository.getGameInfo(gameId, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                GoldMarketRepository aiRepository = repositoryFor(model != null ? model.contractAddress : contractAddress);
                aiRepository.getAiManagedConfig(gameId,
                        new GoldMarketRepository.DataCallback<BackendApiClient.AiManagedConfig>() {
                    @Override
                    public void onSuccess(BackendApiClient.AiManagedConfig config) {
                        BackendApiClient.AiManagedConfig resolved = config == null
                                ? BackendApiClient.AiManagedConfig.defaults() : config;
                        if (model != null) model.isManaged = resolved.enabled;
                        aiManagedConfig.postValue(resolved);
                        finishGameInfoRequest(showLoading);
                        currentGame.postValue(model);
                    }
                    @Override
                    public void onError(String err) {
                        finishGameInfoRequest(showLoading);
                        currentGame.postValue(model);
                    }
                });
            }
            @Override
            public void onError(String err) {
                finishGameInfoRequest(showLoading);
                if (showLoading) error.postValue(err);
            }
            @Override
            public void onTiming(String source, long durationMs, boolean isFallback) {
                if (!showLoading) return;
                String msg = source + " | " + String.format(java.util.Locale.US, "%.2fs", durationMs / 1000.0);
                debugToast.postValue(msg);
            }
        });
    }

    private void finishGameInfoRequest(boolean showLoading) {
        gameInfoRequestInFlight.set(false);
        if (showLoading) isLoading.postValue(false);
    }

    public void startAiAnalysis() {
        GoldMarketRepository.GameModel model = currentGame.getValue();
        if (model == null) return;
        
        isLoading.setValue(true);
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(model, System.currentTimeMillis(), quote);
                fetchAiSummary();
            }
            @Override
            public void onError(String err) {
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(model, System.currentTimeMillis(), null);
                fetchAiSummary();
            }
        });
    }

    public void toggleAiManaged(int gameId, boolean enabled) {
        toggleAiManaged(gameId, null, enabled);
    }

    public void toggleAiManaged(int gameId, String contractAddress, boolean enabled) {
        repositoryFor(contractAddress).toggleAiManaged(gameId, enabled, new GoldMarketRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                BackendApiClient.AiManagedConfig config = aiManagedConfig.getValue();
                if (config == null) config = BackendApiClient.AiManagedConfig.defaults();
                config = config.copy();
                config.enabled = Boolean.TRUE.equals(result);
                aiManagedConfig.postValue(config);
                GoldMarketRepository.GameModel model = currentGame.getValue();
                if (model != null && model.id == gameId) {
                    model.isManaged = result;
                    currentGame.postValue(model);
                }
            }
            @Override
            public void onError(String err) {
                error.postValue(err);
                // 恢复 UI 状态
                GoldMarketRepository.GameModel model = currentGame.getValue();
                if (model != null) currentGame.postValue(model);
            }
        });
    }

    public void configureAiManaged(int gameId, String contractAddress,
                                   BackendApiClient.AiManagedConfig config) {
        if (config == null) return;
        BackendApiClient.AiManagedConfig requested = config.copy();
        requested.enabled = true;
        repositoryFor(contractAddress).configureAiManaged(gameId, true, requested,
                new GoldMarketRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                requested.enabled = Boolean.TRUE.equals(result);
                aiManagedConfig.postValue(requested);
                GoldMarketRepository.GameModel model = currentGame.getValue();
                if (model != null && model.id == gameId) {
                    model.isManaged = requested.enabled;
                    currentGame.postValue(model);
                }
                txStatus.postValue("AI trading settings saved");
            }

            @Override
            public void onError(String err) {
                error.postValue(err);
                GoldMarketRepository.GameModel model = currentGame.getValue();
                if (model != null) currentGame.postValue(model);
            }
        });
    }

    private void requestAiSummary(GoldMarketRepository.GameModel model) {
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(model, System.currentTimeMillis(), quote);
                fetchAiSummary();
            }
            @Override
            public void onError(String err) {
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(model, System.currentTimeMillis(), null);
                fetchAiSummary();
            }
        });
    }

    private void fetchAiSummary() {
        DeepSeekClient.chatForParsing(
                GoldMarketResearchAnalysisPresenter.systemPrompt(),
                GoldMarketResearchAnalysisPresenter.buildPrompt(marketAiContext),
                new DeepSeekClient.ChatCallback() {
                    @Override public void onSuccess(String response) {
                        isLoading.postValue(false);
                        marketAiSummary.postValue(response);
                    }
                    @Override public void onError(String err) {
                        isLoading.postValue(false);
                        error.postValue("AI error: " + err);
                    }
                });
    }

    public void buyShares(int gameId, int optionId, BigInteger amountWei) {
        buyShares(gameId, null, optionId, amountWei);
    }

    public void buyShares(int gameId, String contractAddress, int optionId, BigInteger amountWei) {
        repositoryFor(contractAddress).buyShares(gameId, optionId, amountWei, new GoldMarketRepository.TxCallback() {
            @Override public void onTxSent(String txHash) { txStatus.postValue("Sent: " + txHash); }
            @Override public void onConfirmed(String msg) {
                txStatus.postValue("Confirmed: " + msg);
                // 核心交易缓存已在确认回调前写入，无需再固定等待 2 秒。
                loadGameInfo(gameId, contractAddress);
                loadChartData(gameId, selectedChartRange);
            }
            @Override public void onError(String err) { tradeError.postValue(err); }
        });
    }

    public void sellShares(int gameId, String contractAddress, int optionId,
                           BigInteger shareAmountWei, BigInteger minAmountOutWei,
                           BigInteger quotedAmountOutWei) {
        repositoryFor(contractAddress).sellShares(
                gameId, optionId, shareAmountWei, minAmountOutWei, quotedAmountOutWei,
                new GoldMarketRepository.TxCallback() {
                    @Override public void onTxSent(String txHash) {
                        txStatus.postValue("卖出交易已提交");
                    }
                    @Override public void onConfirmed(String msg) {
                        txStatus.postValue("卖出成功，BKC 已返回钱包");
                        loadGameInfo(gameId, contractAddress);
                        loadChartData(gameId, selectedChartRange);
                    }
                    @Override public void onError(String err) {
                        tradeError.postValue("卖出失败：\n\n" + err);
                    }
                });
    }

    public void claimReward(int gameId, int optionId) {
        claimReward(gameId, null, optionId);
    }

    public void claimReward(int gameId, String contractAddress, int optionId) {
        repositoryFor(contractAddress).claimReward(gameId, optionId, new GoldMarketRepository.TxCallback() {
            @Override public void onTxSent(String txHash) {
                txStatus.postValue("收益领取交易已提交");
            }
            @Override public void onConfirmed(String msg) {
                txStatus.postValue("收益领取成功");
                loadGameInfo(gameId, contractAddress);
            }
            @Override public void onError(String err) { tradeError.postValue("领取收益失败：\n\n" + err); }
        });
    }
}
