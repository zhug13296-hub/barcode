package com.example.barcodeoffline;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import androidx.core.content.ContextCompat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.example.barcodeoffline.ScanPreferences;
import com.google.android.material.chip.ChipGroup;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 生成页面 — Material Chip格式选择、多格式条码生成
 */
public class GenerateActivity extends AppCompatActivity {

    private EditText inputText;
    private ImageView previewImage;
    private TextView infoLabel;
    private Bitmap latestBitmap;
    private BarcodeFormat currentFormat = BarcodeFormat.QR_CODE;
    private ScanDbHelper db;
    private ExecutorService worker;
    private int generationRequestId = 0;

    private static final Object[][] GEN_FORMATS = {
            {"二维码 QR", BarcodeFormat.QR_CODE},
            {"Code128", BarcodeFormat.CODE_128},
            {"EAN-13", BarcodeFormat.EAN_13},
            {"EAN-8", BarcodeFormat.EAN_8},
            {"UPC-A", BarcodeFormat.UPC_A},
            {"Code39", BarcodeFormat.CODE_39},
            {"Code93", BarcodeFormat.CODE_93},
            {"Codabar", BarcodeFormat.CODABAR},
            {"ITF", BarcodeFormat.ITF},
            {"DataMatrix", BarcodeFormat.DATA_MATRIX},
            {"PDF417", BarcodeFormat.PDF_417},
            {"Aztec", BarcodeFormat.AZTEC},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generate);
        db = new ScanDbHelper(this);
        worker = Executors.newSingleThreadExecutor();

        String initValue = getIntent().getStringExtra("initValue");

        inputText = findViewById(R.id.input_text);
        previewImage = findViewById(R.id.preview_image);
        infoLabel = findViewById(R.id.info_label);

        if (initValue != null && !initValue.isEmpty()) {
            inputText.setText(initValue);
        }

        initFormatChips();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_generate).setOnClickListener(v -> generatePreview());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveBitmap());
        findViewById(R.id.btn_share).setOnClickListener(v -> shareBitmap());

        if (initValue != null && !initValue.isEmpty()) {
            generatePreview();
        }
    }

    private void initFormatChips() {
        ChipGroup group = findViewById(R.id.format_group);
        for (int i = 0; i < GEN_FORMATS.length; i++) {
            final BarcodeFormat fmt = (BarcodeFormat) GEN_FORMATS[i][1];
            String label = (String) GEN_FORMATS[i][0];

            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(fmt == currentFormat);
            chip.setClickable(true);
            chip.setTextSize(13);
            chip.setChipMinHeightResource(R.dimen.btn_height_sm);
            chip.setCheckedIconVisible(false);
            chip.setChipCornerRadiusResource(R.dimen.radius_full);
            chip.setChipStrokeColorResource(R.color.outline_variant);
            chip.setChipStrokeWidth(0.5f);
            chip.setTag(fmt);

            boolean selected = fmt == currentFormat;
            chip.setChipBackgroundColorResource(selected ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_secondary));

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFormat = (BarcodeFormat) buttonView.getTag();
                    refreshFormatChips(group);
                    if (inputText.getText().length() > 0) generatePreview();
                }
            });
            group.addView(chip);
        }
    }

    private void refreshFormatChips(ChipGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            BarcodeFormat fmt = (BarcodeFormat) chip.getTag();
            boolean selected = fmt == currentFormat;
            chip.setOnCheckedChangeListener(null);
            chip.setChecked(selected);
            chip.setChipBackgroundColorResource(selected ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_secondary));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFormat = (BarcodeFormat) buttonView.getTag();
                    refreshFormatChips(group);
                    if (inputText.getText().length() > 0) generatePreview();
                }
            });
        }
    }

    private void generatePreview() {
        String value = inputText.getText().toString().trim();
        if (value.isEmpty()) {
            toast("请输入内容");
            return;
        }
        BarcodeFormat format = currentFormat;
        int[] size = getBarcodeSize(format);
        int requestId = ++generationRequestId;
        infoLabel.setText("正在生成...");
        worker.execute(() -> {
            try {
                Bitmap bitmap = createBarcode(value, format, size[0], size[1]);
                runOnUiThread(() -> {
                    if (requestId != generationRequestId || isFinishing()) {
                        bitmap.recycle();
                        return;
                    }
                    Bitmap oldBitmap = latestBitmap;
                    latestBitmap = bitmap;
                    previewImage.setImageBitmap(latestBitmap);
                    if (oldBitmap != null && oldBitmap != latestBitmap && !oldBitmap.isRecycled()) {
                        oldBitmap.recycle();
                    }
                    infoLabel.setText(format.name() + " · " + size[0] + "×" + size[1] + " · " + value.length() + "字符");
                    db.insertOrUpdateLatest(new ScanDbHelper.Record(ScanDbHelper.MODE_GENERATE, format.name(), value, 0));
                    toast("已生成");
                });
            } catch (WriterException e) {
                runOnUiThread(() -> {
                    if (requestId != generationRequestId || isFinishing()) return;
                    infoLabel.setText("");
                    toast("生成失败: 该内容不支持此格式");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestId != generationRequestId || isFinishing()) return;
                    infoLabel.setText("");
                    toast("生成失败: " + safeMessage(e));
                });
            }
        });
    }

    private int[] getBarcodeSize(BarcodeFormat format) {
        switch (format) {
            case QR_CODE: case AZTEC: case DATA_MATRIX: return new int[]{720, 720};
            case PDF_417: return new int[]{900, 400};
            case EAN_13: case UPC_A: return new int[]{900, 300};
            case EAN_8: case ITF: return new int[]{700, 300};
            default: return new int[]{900, 280};
        }
    }

    private Bitmap createBarcode(String value, BarcodeFormat format, int width, int height) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int fgColor = ScanPreferences.getQrForegroundColor(this);
        int bgColor = ScanPreferences.getQrBackgroundColor(this);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? fgColor : bgColor;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void saveBitmap() {
        if (latestBitmap == null) { toast("请先生成预览"); return; }
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            return;
        }
        try {
            String name = "barcode_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date()) + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BarcodeOfflineTool");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("无法创建图片文件");
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) throw new Exception("无法打开输出流");
                latestBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BarcodeOfflineTool");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(file);
                latestBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
            }
            toast("已保存到相册");
        } catch (Exception e) {
            toast("保存失败: " + e.getMessage());
        }
    }

    private void shareBitmap() {
        if (latestBitmap == null) { toast("请先生成预览"); return; }
        try {
            File cacheDir = new File(getCacheDir(), "share");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, "barcode.png");
            FileOutputStream fos = new FileOutputStream(file);
            latestBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享条码图片"));
        } catch (Exception e) {
            toast("分享失败: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveBitmap();
        } else {
            toast("没有存储权限，无法保存图片");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
        if (latestBitmap != null && !latestBitmap.isRecycled()) {
            latestBitmap.recycle();
            latestBitmap = null;
        }
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
