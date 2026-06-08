package com.example.barcodeoffline;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 扫码页面 — 单次/批量模式、闪光灯、格式选择、结构化结果、相册识别
 */
public class ScanActivity extends ComponentActivity {

    private boolean batchMode = false;
    private boolean flashOn = false;
    private int batchCount = 0;
    private int batchIndex = 0;
    private final List<ScanDbHelper.Record> batchRecords = new ArrayList<>();
    private final List<String> selectedFormats = new ArrayList<>();

    private ScanDbHelper db;
    private ScanResultParser.ParsedResult lastParsed;

    // Views
    private TextView titleText, btnScan, btnToggleBatch, btnAction1, btnCopy;
    private TextView resultTypeBadge, resultFormat, resultContent, batchCountView;
    private LinearLayout resultCard, emptyHint, batchCounterBar, batchResultsContainer;
    private ImageView flashBtn;
    private ChipGroup formatChips;

    private ActivityResultLauncher<ScanOptions> scanLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    // 格式选项
    private static final String[][] FORMAT_OPTIONS = {
            {"全部", "ALL"}, {"QR码", "QR_CODE"}, {"Code128", "CODE_128"},
            {"EAN-13", "EAN_13"}, {"EAN-8", "EAN_8"}, {"UPC-A", "UPC_A"},
            {"UPC-E", "UPC_E"}, {"DataMatrix", "DATA_MATRIX"}, {"PDF417", "PDF_417"},
            {"Aztec", "AZTEC"}, {"Code39", "CODE_39"}, {"Code93", "CODE_93"},
            {"ITF", "ITF"}, {"Codabar", "CODABAR"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        db = new ScanDbHelper(this);

        batchMode = getIntent().getBooleanExtra("batchMode", false);
        boolean openGallery = getIntent().getBooleanExtra("openGallery", false);
        selectedFormats.add("ALL");

        initViews();
        initFormatChips();
        updateBatchUI();

        // 扫码 launcher
        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                onScanResult(result.getContents(), result.getFormatName());
            } else {
                toast("已取消扫码");
            }
        });

        // 相册 launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        decodeFromUri(uri);
                    }
                });

        if (openGallery) {
            openGallery();
        }
    }

    private void initViews() {
        titleText = findViewById(R.id.title_text);
        btnScan = findViewById(R.id.btn_scan);
        btnToggleBatch = findViewById(R.id.btn_toggle_batch);
        btnAction1 = findViewById(R.id.btn_action1);
        btnCopy = findViewById(R.id.btn_copy);
        resultTypeBadge = findViewById(R.id.result_type_badge);
        resultFormat = findViewById(R.id.result_format);
        resultContent = findViewById(R.id.result_content);
        batchCountView = findViewById(R.id.batch_count);
        resultCard = findViewById(R.id.result_card);
        emptyHint = findViewById(R.id.empty_hint);
        batchCounterBar = findViewById(R.id.batch_counter_bar);
        batchResultsContainer = findViewById(R.id.batch_results);
        formatChips = findViewById(R.id.format_chips);

        flashBtn = findViewById(R.id.btn_flash);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        flashBtn.setOnClickListener(v -> toggleFlash());
        btnScan.setOnClickListener(v -> startScan());
        btnToggleBatch.setOnClickListener(v -> toggleBatchMode());
        findViewById(R.id.btn_gallery).setOnClickListener(v -> openGallery());
        btnCopy.setOnClickListener(v -> {
            if (lastParsed != null) copyText(lastParsed.rawValue);
        });
        btnAction1.setOnClickListener(v -> {
            if (lastParsed != null) {
                Intent intent = ScanResultParser.createActionIntent(this, lastParsed);
                if (intent != null) {
                    try { startActivity(intent); } catch (Exception e) { copyText(lastParsed.rawValue); }
                } else {
                    copyText(lastParsed.rawValue);
                }
            }
        });
        findViewById(R.id.btn_export_batch).setOnClickListener(v -> exportBatch());
    }

    private void initFormatChips() {
        for (String[] opt : FORMAT_OPTIONS) {
            Chip chip = new Chip(this);
            chip.setText(opt[0]);
            chip.setCheckable(true);
            chip.setChecked(opt[1].equals("ALL"));
            chip.setClickable(true);
            chip.setChipBackgroundColorResource(
                    opt[1].equals("ALL") ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(opt[1].equals("ALL") ? Color.WHITE : getResources().getColor(R.color.text_secondary));
            chip.setChipStrokeColorResource(R.color.outline_variant);
            chip.setChipStrokeWidth(0.5f);
            chip.setChipCornerRadiusResource(R.dimen.radius_full);
            chip.setChipMinHeightResource(R.dimen.btn_height_sm);
            chip.setTextSize(13);
            chip.setCheckedIconVisible(false);
            chip.setTag(opt[1]);

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String format = (String) buttonView.getTag();
                if (format.equals("ALL")) {
                    if (isChecked) {
                        selectedFormats.clear();
                        selectedFormats.add("ALL");
                        refreshChips();
                    }
                } else {
                    selectedFormats.remove("ALL");
                    if (isChecked) {
                        if (!selectedFormats.contains(format)) selectedFormats.add(format);
                    } else {
                        selectedFormats.remove(format);
                        if (selectedFormats.isEmpty()) {
                            selectedFormats.add("ALL");
                            refreshChips();
                        }
                    }
                }
            });
            formatChips.addView(chip);
        }
    }

    private void refreshChips() {
        for (int i = 0; i < formatChips.getChildCount(); i++) {
            Chip chip = (Chip) formatChips.getChildAt(i);
            String format = (String) chip.getTag();
            boolean selected = selectedFormats.contains(format);
            chip.setOnCheckedChangeListener(null);
            chip.setChecked(selected);
            chip.setChipBackgroundColorResource(
                    selected ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(selected ? Color.WHITE : getResources().getColor(R.color.text_secondary));
            // Re-attach listener
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                String fmt = (String) buttonView.getTag();
                if (fmt.equals("ALL")) {
                    if (isChecked) {
                        selectedFormats.clear();
                        selectedFormats.add("ALL");
                        refreshChips();
                    }
                } else {
                    selectedFormats.remove("ALL");
                    if (isChecked) {
                        if (!selectedFormats.contains(fmt)) selectedFormats.add(fmt);
                    } else {
                        selectedFormats.remove(fmt);
                        if (selectedFormats.isEmpty()) {
                            selectedFormats.add("ALL");
                            refreshChips();
                        }
                    }
                }
            });
        }
    }

    private void startScan() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
            return;
        }
        ScanOptions options = new ScanOptions();
        options.setPrompt("将条码放入框内自动识别");
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(false);
        options.setTorchEnabled(flashOn);

        if (selectedFormats.contains("ALL")) {
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        } else {
            options.setBarcodeFormats(selectedFormats);
        }
        scanLauncher.launch(options);
    }

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    /** 从图片URI解码条码 */
    private void decodeFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                toast("无法读取图片");
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                toast("无法解码图片");
                return;
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, getSelectedFormats());

            Result result = new MultiFormatReader().decode(binaryBitmap, hints);
            if (result != null) {
                onScanResult(result.getText(), result.getBarcodeFormat().name());
            } else {
                toast("未识别到条码");
            }
        } catch (com.google.zxing.NotFoundException e) {
            toast("图片中未找到条码");
        } catch (Exception e) {
            toast("识别失败: " + e.getMessage());
        }
    }

    private Collection<com.google.zxing.BarcodeFormat> getSelectedFormats() {
        List<com.google.zxing.BarcodeFormat> formats = new ArrayList<>();
        for (String f : selectedFormats) {
            if (f.equals("ALL")) {
                // Return empty to let reader try all formats
                return new ArrayList<>();
            }
            try {
                formats.add(com.google.zxing.BarcodeFormat.valueOf(f));
            } catch (Exception ignored) {}
        }
        return formats.isEmpty() ? new ArrayList<>() : formats;
    }

    private void onScanResult(String content, String formatName) {
        vibrateOnce();
        lastParsed = ScanResultParser.parse(content);

        ScanDbHelper.Record record = new ScanDbHelper.Record(
                ScanDbHelper.MODE_SCAN, formatName, content,
                batchMode ? ++batchIndex : 0);
        db.insert(record);

        if (batchMode) {
            batchCount++;
            batchRecords.add(record);
            batchCountView.setText("已扫: " + batchCount);
            addBatchResultItem(record, lastParsed);
            startScan();
        } else {
            showSingleResult(lastParsed, formatName);
        }
    }

    private void showSingleResult(ScanResultParser.ParsedResult parsed, String formatName) {
        emptyHint.setVisibility(View.GONE);
        resultCard.setVisibility(View.VISIBLE);

        resultTypeBadge.setText(parsed.displayType);
        resultFormat.setText(formatName);
        resultContent.setText(parsed.friendlyValue);
        btnAction1.setText(parsed.actionLabel);
    }

    private void addBatchResultItem(ScanDbHelper.Record record, ScanResultParser.ParsedResult parsed) {
        batchResultsContainer.setVisibility(View.VISIBLE);

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackgroundResource(R.drawable.bg_card_static);
        item.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        item.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText("#" + record.batchIndex);
        badge.setTextSize(12);
        badge.setTextColor(Color.WHITE);
        badge.setBackgroundResource(R.drawable.bg_badge);
        badge.setPadding(dp(10), dp(2), dp(10), dp(2));
        row.addView(badge);

        TextView typeLabel = new TextView(this);
        typeLabel.setText(" " + parsed.displayType + " · " + record.format);
        typeLabel.setTextSize(12);
        typeLabel.setTextColor(getResources().getColor(R.color.text_hint));
        row.addView(typeLabel);

        item.addView(row);

        TextView content = new TextView(this);
        content.setText(parsed.friendlyValue);
        content.setTextSize(15);
        content.setTextColor(getResources().getColor(R.color.text_primary));
        content.setPadding(0, dp(4), 0, 0);
        content.setMaxLines(3);
        item.addView(content);

        item.setOnClickListener(v -> copyText(record.content));
        batchResultsContainer.addView(item, 0);
    }

    private void toggleBatchMode() {
        batchMode = !batchMode;
        if (batchMode) {
            batchCount = 0;
            batchIndex = 0;
            batchRecords.clear();
            batchResultsContainer.removeAllViews();
            batchResultsContainer.setVisibility(View.GONE);
        }
        updateBatchUI();
    }

    private void updateBatchUI() {
        titleText.setText(batchMode ? "批量扫描" : "扫码识别");
        btnToggleBatch.setText(batchMode ? "单次模式" : "批量模式");
        batchCounterBar.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        if (batchMode) {
            batchCountView.setText("已扫: " + batchCount);
        }
    }

    private void exportBatch() {
        if (batchRecords.isEmpty()) {
            toast("没有扫描记录");
            return;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("序号,类型,格式,内容,时间\n");
        for (ScanDbHelper.Record r : batchRecords) {
            csv.append(r.batchIndex).append(",");
            csv.append(ScanResultParser.parse(r.content).displayType).append(",");
            csv.append(escapeCsv(r.format)).append(",");
            csv.append(escapeCsv(r.content)).append(",");
            csv.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date(r.timestamp))).append("\n");
        }
        shareText(csv.toString(), "batch_scan_" + System.currentTimeMillis() + ".csv", "text/csv");
    }

    private void toggleFlash() {
        flashOn = !flashOn;
        flashBtn.setActivated(flashOn);
        flashBtn.setImageResource(flashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        toast(flashOn ? "闪光灯已开启" : "闪光灯已关闭");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            toast("没有相机权限，无法扫码");
        }
    }

    private void copyText(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("barcode", value));
        toast("已复制");
    }

    private void shareText(String content, String filename, String mimeType) {
        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "export");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            java.io.File file = new java.io.File(cacheDir, filename);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出扫码数据"));
        } catch (Exception e) {
            toast("导出失败: " + e.getMessage());
        }
    }

    private void vibrateOnce() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(80);
            }
        } catch (Exception ignored) {}
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

}
