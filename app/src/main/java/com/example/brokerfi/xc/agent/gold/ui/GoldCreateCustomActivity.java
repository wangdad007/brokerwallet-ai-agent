package com.example.brokerfi.xc.agent.gold.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GoldCreateCustomActivity extends AppCompatActivity {

    private String templateType;
    private String templateTitle;
    
    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private boolean startSelected = false;
    private boolean endSelected = false;

    private TextView tvTemplateName, tvTemplateDetail;
    private EditText etParam1, etInitialLiquidity;
    private Button btnSelectStartTime, btnSelectTime, btnDeploy;
    private LinearLayout containerTechnical, containerDirection;
    private Spinner spinnerIndicator, spinnerOperator, spinnerDirection;

    private GoldMarketRepository repository;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_create_custom);

        templateType = getIntent().getStringExtra("TEMPLATE_TYPE");
        templateTitle = getIntent().getStringExtra("TEMPLATE_TITLE");

        String pk = StorageUtil.getCurrentPrivatekey(this);
        repository = new GoldMarketRepository(this, pk);

        initViews();
        setupTemplateUI();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvTemplateName = findViewById(R.id.tv_template_name);
        tvTemplateDetail = findViewById(R.id.tv_template_detail);
        etParam1 = findViewById(R.id.et_param1);
        etInitialLiquidity = findViewById(R.id.et_initial_liquidity);
        btnSelectStartTime = findViewById(R.id.btn_select_start_time);
        btnSelectTime = findViewById(R.id.btn_select_time);
        btnDeploy = findViewById(R.id.btn_deploy);
        
        containerTechnical = findViewById(R.id.container_technical);
        spinnerIndicator = findViewById(R.id.spinner_indicator);
        spinnerOperator = findViewById(R.id.spinner_operator);

        containerDirection = findViewById(R.id.container_direction);
        spinnerDirection = findViewById(R.id.spinner_direction);

        btnSelectStartTime.setOnClickListener(v -> showDatePicker(true));
        btnSelectTime.setOnClickListener(v -> showDatePicker(false));
        btnDeploy.setOnClickListener(v -> attemptShowSummary());

        // Setup Technical Spinners
        List<String> indicators = Arrays.asList("RSI (14)", "MACD (12,26,9)", "KDJ (9,3,3)", "BOLL (20,2)");
        ArrayAdapter<String> adapterInd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, indicators);
        adapterInd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIndicator.setAdapter(adapterInd);

        List<String> operators = Arrays.asList("大于 (Above)", "小于 (Below)", "交叉向上 (Cross Up)", "交叉向下 (Cross Down)");
        ArrayAdapter<String> adapterOp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, operators);
        adapterOp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOperator.setAdapter(adapterOp);

        // Setup Direction Spinner
        List<String> directions = Arrays.asList("上涨 (Price Up)", "下跌 (Price Down)", "持平 (Flat/Range)");
        ArrayAdapter<String> adapterDir = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, directions);
        adapterDir.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDirection.setAdapter(adapterDir);
    }

    private void setupTemplateUI() {
        tvTemplateName.setText(templateTitle);
        containerTechnical.setVisibility(View.GONE);
        containerDirection.setVisibility(View.GONE);
        etParam1.setVisibility(View.VISIBLE);
        
        switch (templateType) {
            case "TYPE_PRICE":
                tvTemplateDetail.setText("对比两个时间点的黄金价格。博弈在结束日时，金价相对于开始日是涨、跌还是持平。");
                etParam1.setVisibility(View.GONE);
                containerDirection.setVisibility(View.VISIBLE);
                break;
            case "TYPE_VOLATILITY":
                tvTemplateDetail.setText("博弈设定周期内的价格剧烈程度。波幅 = (最高价-最低价)/昨日收盘价。");
                etParam1.setHint("波动率门槛 (%)");
                break;
            case "TYPE_VOLUME":
                tvTemplateDetail.setText("博弈上海黄金交易所指定交易日的成交总量。单位为吨。");
                etParam1.setHint("目标成交量 (吨)");
                btnSelectStartTime.setVisibility(View.GONE); // Specific day only
                btnSelectTime.setText("选择交易日: 未选择");
                break;
            case "TYPE_TECHNICAL":
                tvTemplateDetail.setText("博弈特定技术指标是否达到设定形态。由系统 K 线数据实时计算。");
                containerTechnical.setVisibility(View.VISIBLE);
                etParam1.setHint("触发数值 (如: 70)");
                break;
            case "TYPE_TOUCH":
                tvTemplateDetail.setText("极值触碰博弈。在周期内金价只要曾达到过目标价即满足条件。");
                etParam1.setHint("触碰价格 (USD)");
                break;
            case "TYPE_RELATIVE":
                tvTemplateDetail.setText("博弈黄金相对于其他避险/风险资产的阶段收益率。");
                etParam1.setHint("对比标的 (如: BTC)");
                break;
        }
    }

    private void showDatePicker(boolean isStart) {
        Calendar now = Calendar.getInstance();
        Calendar target = isStart ? startCalendar : endCalendar;

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            target.set(Calendar.YEAR, year);
            target.set(Calendar.MONTH, month);
            target.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            target.set(Calendar.HOUR_OF_DAY, isStart ? 0 : 23);
            target.set(Calendar.MINUTE, isStart ? 0 : 59);
            target.set(Calendar.SECOND, 0);

            if (isStart) {
                startSelected = true;
                btnSelectStartTime.setText("开始日期: " + dateFormat.format(target.getTime()));
            } else {
                endSelected = true;
                btnSelectTime.setText("截止日期: " + dateFormat.format(target.getTime()));
            }
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void attemptShowSummary() {
        String p1 = etParam1.getText().toString().trim();
        if (p1.isEmpty() && !templateType.equals("TYPE_PRICE")) {
            Toast.makeText(this, "请输入定制参数", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!endSelected) {
            Toast.makeText(this, "请选择日期", Toast.LENGTH_SHORT).show();
            return;
        }

        long nowMs = System.currentTimeMillis();
        long startMs = startSelected ? startCalendar.getTimeInMillis() : nowMs;
        long endMs = endCalendar.getTimeInMillis();

        if (endMs <= startMs) {
            Toast.makeText(this, "截止日期必须晚于开始日期", Toast.LENGTH_SHORT).show();
            return;
        }

        String condition = generateConditionString(p1);
        String descriptiveTitle = generateDescriptiveTitle(p1);
        
        showSummaryDialog(descriptiveTitle, condition, startMs, endMs);
    }

    private String generateDescriptiveTitle(String p1) {
        String startStr = dateFormat.format(startCalendar.getTime());
        String endStr = dateFormat.format(endCalendar.getTime());
        
        switch (templateType) {
            case "TYPE_PRICE":
                String dir = spinnerDirection.getSelectedItem().toString().split(" ")[0];
                return String.format("%s 至 %s 黄金价格 %s", startStr, endStr, dir);
            case "TYPE_VOLATILITY":
                return String.format("%s 前黄金波幅超过 %s%%", endStr, p1);
            case "TYPE_VOLUME":
                return String.format("%s 当日成交量超过 %s 吨", endStr, p1);
            case "TYPE_TOUCH":
                return String.format("博弈周期内金价触及 %s USD", p1);
            case "TYPE_TECHNICAL":
                return String.format("指标 %s 触发 %s %s", spinnerIndicator.getSelectedItem(), spinnerOperator.getSelectedItem(), p1);
            case "TYPE_RELATIVE":
                return String.format("黄金收益率跑赢 %s", p1);
            default:
                return templateTitle;
        }
    }

    private String generateConditionString(String p1) {
        String period = startSelected ? "从 " + dateFormat.format(startCalendar.getTime()) + " 到 " : "截至 ";
        period += dateFormat.format(endCalendar.getTime());

        switch (templateType) {
            case "TYPE_PRICE":
                return String.format("博弈黄金价格在 %s 相对基准 %s", period, spinnerDirection.getSelectedItem());
            case "TYPE_VOLATILITY":
                return String.format("博弈周期内波幅 >= %s%% (%s)", p1, period);
            case "TYPE_VOLUME":
                return String.format("博弈指定日成交量 >= %s 吨 (%s)", p1, dateFormat.format(endCalendar.getTime()));
            case "TYPE_TECHNICAL":
                return String.format("指标 %s %s %s (%s)", spinnerIndicator.getSelectedItem(), spinnerOperator.getSelectedItem(), p1, period);
            case "TYPE_TOUCH":
                return String.format("金价曾触及 %s USD (%s)", p1, period);
            case "TYPE_RELATIVE":
                return String.format("黄金收益率跑赢 %s (%s)", p1, period);
            default:
                return "自定义博弈: " + p1;
        }
    }

    private void showSummaryDialog(String title, String condition, long startMs, long endMs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gold_pool_summary, null);
        builder.setView(dialogView);

        TextView tvId = dialogView.findViewById(R.id.tv_summary_id);
        TextView tvLogic = dialogView.findViewById(R.id.tv_summary_logic);
        TextView tvPeriod = dialogView.findViewById(R.id.tv_summary_period);
        TextView tvCreator = dialogView.findViewById(R.id.tv_summary_creator);
        TextView tvTime = dialogView.findViewById(R.id.tv_summary_time);
        Button btnConfirm = dialogView.findViewById(R.id.btn_summary_close);

        tvId.setText("待部署: " + title);
        tvLogic.setText(condition);
        
        String startStr = startSelected ? dateFormat.format(new Date(startMs)) : "当前立即开始";
        tvPeriod.setText(startStr + " 至 " + dateFormat.format(new Date(endMs)));
        
        tvCreator.setText(repository.getWalletAddress());
        tvTime.setText(dateFormat.format(new Date()));
        btnConfirm.setText("确认并部署至区块链");

        AlertDialog dialog = builder.create();
        dialog.setCancelable(true);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            performDeploy(title, condition, (endMs - System.currentTimeMillis()) / 1000);
        });
        dialog.show();
    }

    private void performDeploy(String title, String condition, long durationSec) {
        btnDeploy.setEnabled(false);
        btnDeploy.setText("正在部署...");
        
        repository.createGame(title, condition, "", "Premium Gold Pool", Arrays.asList("达成 (YES)", "未达成 (NO)"), durationSec, new GoldMarketRepository.TxCallback() {
            @Override
            public void onTxSent(String txHash) {
                Toast.makeText(GoldCreateCustomActivity.this, "交易已发送", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConfirmed(String message) {
                Toast.makeText(GoldCreateCustomActivity.this, "部署成功", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String error) {
                btnDeploy.setEnabled(true);
                btnDeploy.setText("部署博弈池");
                Toast.makeText(GoldCreateCustomActivity.this, "部署失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
