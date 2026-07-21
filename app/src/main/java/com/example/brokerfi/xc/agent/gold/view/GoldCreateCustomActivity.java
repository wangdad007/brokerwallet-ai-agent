package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldBenchmarkCatalog;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCreationPolicy;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketTemplateCatalog;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldCreatePoolViewModel;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class GoldCreateCustomActivity extends AppCompatActivity {
    private static final TimeZone BEIJING = TimeZone.getTimeZone(GoldMarketCreationPolicy.TIMEZONE);

    private GoldCreatePoolViewModel viewModel;
    private String templateType;
    private String templateTitle;
    private Calendar startCalendar;
    private Calendar endCalendar;
    private TextView tvTemplateName;
    private TextView tvTemplateDetail;
    private EditText etParam1;
    private EditText etParam2;
    private EditText etInitialLiquidity;
    private Button btnSelectStartTime;
    private Button btnSelectTime;
    private Button btnDeploy;
    private Button btnSelectImage;
    private ImageView ivPoolIcon;
    private LinearLayout containerOperator;
    private LinearLayout containerDirection;
    private LinearLayout containerBenchmark;
    private Spinner spinnerOperator;
    private Spinner spinnerDirection;
    private Spinner spinnerBenchmark;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private byte[] selectedImageData;
    private byte[] templateImageData;
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleImageResult(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_create_custom);
        dateFormat.setTimeZone(BEIJING);
        templateType = getIntent().getStringExtra("TEMPLATE_TYPE");
        templateTitle = getIntent().getStringExtra("TEMPLATE_TITLE");
        if (!GoldMarketTemplateCatalog.isCreatable(templateType)) {
            templateType = GoldMarketTemplateCatalog.TYPE_PRICE;
        }
        GoldMarketTemplateCatalog.Template template = GoldMarketTemplateCatalog.forType(templateType);
        if (templateTitle == null || templateTitle.trim().isEmpty()) templateTitle = template.title;

        viewModel = new ViewModelProvider(this).get(GoldCreatePoolViewModel.class);
        initViews();
        applyDefaultWindow(0, 2);
        applyTemplateDefaultCover();
        setupTemplateUI();
        String aiData = getIntent().getStringExtra("AI_PARSED_DATA");
        if (aiData != null) applyAiParsedData(aiData);
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getIsDeploying().observe(this, deploying -> {
            btnDeploy.setEnabled(!deploying);
            btnDeploy.setText(deploying ? "Deploying…" : "Deploy Market");
        });
        viewModel.getTxStatus().observe(this, status -> {
            if (status != null) {
                Toast.makeText(this, status, Toast.LENGTH_LONG).show();
                if (status.startsWith("Success") || status.equals("Success")) finish();
            }
        });
        viewModel.getError().observe(this, error -> {
            if (error != null) {
                new AlertDialog.Builder(this).setTitle("Market Creation Failed").setMessage(error)
                        .setPositiveButton("OK", null).show();
            }
        });
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvTemplateName = findViewById(R.id.tv_template_name);
        tvTemplateDetail = findViewById(R.id.tv_template_detail);
        etParam1 = findViewById(R.id.et_param1);
        etParam2 = findViewById(R.id.et_param2);
        etInitialLiquidity = findViewById(R.id.et_initial_liquidity);
        btnSelectStartTime = findViewById(R.id.btn_select_start_time);
        btnSelectTime = findViewById(R.id.btn_select_time);
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivPoolIcon = findViewById(R.id.iv_pool_icon);
        btnDeploy = findViewById(R.id.btn_deploy);
        containerOperator = findViewById(R.id.container_technical);
        spinnerOperator = findViewById(R.id.spinner_operator);
        containerDirection = findViewById(R.id.container_direction);
        spinnerDirection = findViewById(R.id.spinner_direction);
        containerBenchmark = findViewById(R.id.container_benchmark);
        spinnerBenchmark = findViewById(R.id.spinner_benchmark);
        spinnerBenchmark.setAdapter(new BenchmarkAdapter());

        btnSelectStartTime.setOnClickListener(v -> showDatePicker(true));
        btnSelectTime.setOnClickListener(v -> showDatePicker(false));
        btnSelectImage.setOnClickListener(v -> pickImage());
        btnDeploy.setOnClickListener(v -> attemptShowSummary());
    }

    private void setupTemplateUI() {
        GoldMarketTemplateCatalog.Template template = GoldMarketTemplateCatalog.forType(templateType);
        tvTemplateName.setText(template.title);
        tvTemplateDetail.setText(template.hint
                + ". Resolution uses verified market data and full-day observation periods based on Beijing time.");
        containerDirection.setVisibility(View.GONE);
        containerOperator.setVisibility(View.GONE);
        containerBenchmark.setVisibility(View.GONE);
        etParam1.setVisibility(View.GONE);
        etParam2.setVisibility(View.GONE);

        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(templateType)) {
            showDirectionOptions(true);
        } else if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType)) {
            showDirectionOptions(false);
        } else if (GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD.equals(templateType)) {
            showOrderedOperator();
            etParam1.setVisibility(View.VISIBLE);
            etParam1.setHint("Absolute return threshold (%)");
            etParam1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD.equals(templateType)) {
            showOrderedOperator();
            etParam1.setVisibility(View.VISIBLE);
            etParam1.setHint("Target price (USD/oz)");
            etParam1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(templateType)) {
            containerOperator.setVisibility(View.VISIBLE);
            setSpinnerItems(spinnerOperator, Arrays.asList("Inside closed range", "Outside closed range"));
            etParam1.setVisibility(View.VISIBLE);
            etParam2.setVisibility(View.VISIBLE);
            etParam1.setHint("Lower price bound (USD/oz)");
            etParam2.setHint("Upper price bound (USD/oz)");
            int numeric = android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
            etParam1.setInputType(numeric);
            etParam2.setInputType(numeric);
        } else if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(templateType)) {
            containerBenchmark.setVisibility(View.VISIBLE);
        }
    }

    private void showDirectionOptions(boolean allowFlat) {
        containerDirection.setVisibility(View.VISIBLE);
        List<String> values = allowFlat
                ? Arrays.asList("Up", "Down", "Flat")
                : Arrays.asList("Up", "Down");
        setSpinnerItems(spinnerDirection, values);
    }

    private void showOrderedOperator() {
        containerOperator.setVisibility(View.VISIBLE);
        setSpinnerItems(spinnerOperator, Arrays.asList("At Least", "At Most"));
    }

    private void setSpinnerItems(Spinner spinner, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void applyDefaultWindow(int startDays, int durationDays) {
        Calendar now = Calendar.getInstance(BEIJING);
        GoldMarketCreationPolicy.Window window = GoldMarketCreationPolicy.normalizeWindow(
                now, startDays, durationDays,
                GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType));
        startCalendar = window.start;
        endCalendar = window.end;
        updateDateButtons();
    }

    private void applyAiParsedData(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            String parsedType = json.optString("type", templateType);
            if (GoldMarketTemplateCatalog.isCreatable(parsedType)) templateType = parsedType;
            setupTemplateUI();
            etParam1.setText(json.optString("param1", ""));
            etParam2.setText(json.optString("param2", ""));
            spinnerDirection.setSelection(json.optInt("directionIdx", 0));
            spinnerOperator.setSelection(json.optInt("operatorIdx", 0));
            spinnerBenchmark.setSelection(GoldBenchmarkCatalog.indexOf(
                    json.optString("param1", "BTC")));
            etInitialLiquidity.setText(json.optString("liquidity", "1"));
            applyDefaultWindow(json.optInt("startDaysFromNow", 0),
                    json.optInt("durationDays", 2));
            applyTemplateDefaultCover();
            Toast.makeText(this, "AI-generated market rules applied", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Log.e("GoldCreate", "apply AI data failed", error);
            Toast.makeText(this, "Invalid AI rule: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDatePicker(boolean start) {
        Calendar target = start ? startCalendar : endCalendar;
        if (!start) {
            try {
                GoldMarketCreationPolicy.validateSelectedWindow(startCalendar, endCalendar,
                        GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType));
            } catch (IllegalArgumentException ignored) {
                endCalendar = GoldMarketCreationPolicy.defaultEndForSelectedStart(startCalendar,
                        2, GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType));
                target = endCalendar;
                updateDateButtons();
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance(BEIJING);
            selected.clear();
            selected.set(year, month, day, 0, 0, 0);
            Calendar normalized = GoldMarketCreationPolicy.normalizeSelectedDate(selected);
            if (!sameDate(selected, normalized)) {
                Toast.makeText(this, "No valid settlement boundary on the selected date; moved to "
                        + dateFormat.format(normalized.getTime()), Toast.LENGTH_LONG).show();
            }
            if (start) {
                startCalendar = normalized;
                int preferredDays = selectedDurationDays();
                endCalendar = GoldMarketCreationPolicy.defaultEndForSelectedStart(
                        startCalendar, preferredDays,
                        GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType));
                Toast.makeText(this, "End date adjusted to the 1–4 full-day rule", Toast.LENGTH_SHORT).show();
            } else {
                endCalendar = normalized;
            }
            updateDateButtons();
        }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH));
        if (start) {
            dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000L);
        } else {
            Calendar minEnd = (Calendar) startCalendar.clone();
            minEnd.add(Calendar.DAY_OF_YEAR, 1);
            Calendar maxEnd = (Calendar) startCalendar.clone();
            maxEnd.add(Calendar.DAY_OF_YEAR, 4);
            dialog.getDatePicker().setMinDate(minEnd.getTimeInMillis());
            dialog.getDatePicker().setMaxDate(maxEnd.getTimeInMillis());
        }
        dialog.show();
    }

    private int selectedDurationDays() {
        try {
            return GoldMarketCreationPolicy.validateSelectedWindow(startCalendar, endCalendar,
                    GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType)).durationDays();
        } catch (IllegalArgumentException ignored) {
            return 2;
        }
    }

    private static boolean sameDate(Calendar left, Calendar right) {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private void updateDateButtons() {
        btnSelectStartTime.setText("Start: " + dateFormat.format(startCalendar.getTime()) + " 00:00");
        btnSelectTime.setText("End: " + dateFormat.format(endCalendar.getTime()) + " 00:00");
    }

    private void attemptShowSummary() {
        try {
            boolean streak = GoldMarketTemplateCatalog.TYPE_STREAK.equals(templateType);
            GoldMarketCreationPolicy.Window window = GoldMarketCreationPolicy.validateSelectedWindow(
                    startCalendar, endCalendar, streak);
            startCalendar = window.start;
            endCalendar = window.end;
            String param1 = etParam1.getText().toString().trim();
            String param2 = etParam2.getText().toString().trim();
            if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(templateType)) {
                GoldBenchmarkCatalog.Benchmark benchmark = (GoldBenchmarkCatalog.Benchmark)
                        spinnerBenchmark.getSelectedItem();
                param1 = benchmark == null ? "BTC" : benchmark.symbol;
            }
            int direction = spinnerDirection.getSelectedItemPosition();
            int operator = spinnerOperator.getSelectedItemPosition();
            JSONObject rule = GoldMarketCreationPolicy.buildRule(
                    templateType, param1, param2, direction, operator,
                    startCalendar, endCalendar);
            String title = GoldMarketCreationPolicy.buildTitle(
                    templateType, param1, param2, direction, operator, window);
            String condition = GoldMarketCreationPolicy.buildCondition(
                    templateType, param1, param2, direction, operator, window);
            Calendar now = Calendar.getInstance(BEIJING);
            long duration = GoldMarketCreationPolicy.contractDurationSeconds(now, endCalendar);
            updateDateButtons();
            showSummaryDialog(title, condition, rule, duration);
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSummaryDialog(String title, String condition,
                                   JSONObject rule, long durationSeconds) {
        String liquidity = etInitialLiquidity.getText().toString().trim();
        if (liquidity.isEmpty()) liquidity = "1";
        final java.math.BigInteger liquidityWei = GoldMarketRepository.parseTokenAmountToWei(liquidity);
        if (liquidityWei == null) {
            Toast.makeText(this, "Invalid initial liquidity amount", Toast.LENGTH_SHORT).show();
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_gold_pool_summary, null);
        ((TextView) content.findViewById(R.id.tv_summary_id)).setText("Ready to deploy: " + title);
        ((TextView) content.findViewById(R.id.tv_summary_logic)).setText(
                condition + "\nInitial liquidity: " + liquidity + " BKC");
        ((TextView) content.findViewById(R.id.tv_summary_period)).setText(
                dateFormat.format(startCalendar.getTime()) + " to "
                        + dateFormat.format(endCalendar.getTime()) + " (Beijing time)");
        ((TextView) content.findViewById(R.id.tv_summary_creator)).setText(viewModel.getWalletAddress());
        ((TextView) content.findViewById(R.id.tv_summary_time)).setText(dateFormat.format(new Date()));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        Button confirm = content.findViewById(R.id.btn_summary_close);
        confirm.setText("Confirm & Deploy");
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            byte[] cover = selectedImageData != null ? selectedImageData : templateImageData;
            viewModel.createGame(title, condition, cover, "Premium", Arrays.asList("YES", "NO"),
                    durationSeconds, liquidityWei, templateType, rule);
        });
        dialog.show();
    }

    private void applyTemplateDefaultCover() {
        int iconRes = GoldMarketTemplateIcon.forType(templateType);
        ivPoolIcon.setImageResource(iconRes);
        templateImageData = renderDrawableAsPng(iconRes);
        if (iconRes != R.drawable.apartment_icon) btnSelectImage.setText("Change Image");
    }

    private byte[] renderDrawableAsPng(int drawableRes) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, drawableRes);
            if (drawable == null) return null;
            Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            return output.toByteArray();
        } catch (Exception error) {
            Log.w("GoldCreate", "template cover rendering failed", error);
            return null;
        }
    }

    private void pickImage() {
        imagePickerLauncher.launch(new Intent(
                Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }

    private void handleImageResult(Uri uri) {
        try {
            Glide.with(this).load(uri).into(ivPoolIcon);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                if (bitmap != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output);
                    selectedImageData = output.toByteArray();
                }
            }
        } catch (Exception error) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private final class BenchmarkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return GoldBenchmarkCatalog.all().size();
        }

        @Override
        public GoldBenchmarkCatalog.Benchmark getItem(int position) {
            return GoldBenchmarkCatalog.all().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return bindBenchmarkView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return bindBenchmarkView(position, convertView, parent);
        }

        private View bindBenchmarkView(int position, View convertView, ViewGroup parent) {
            View view = convertView == null
                    ? LayoutInflater.from(GoldCreateCustomActivity.this).inflate(
                    R.layout.item_gold_benchmark_spinner, parent, false)
                    : convertView;
            GoldBenchmarkCatalog.Benchmark benchmark = getItem(position);
            TextView icon = view.findViewById(R.id.tv_benchmark_icon);
            TextView label = view.findViewById(R.id.tv_benchmark_name);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(benchmark.color);
            icon.setBackground(badge);
            icon.setText(benchmark.glyph);
            icon.setContentDescription(benchmark.symbol + " icon");
            label.setText(benchmark.symbol + " · " + benchmark.name);
            return view;
        }
    }
}
