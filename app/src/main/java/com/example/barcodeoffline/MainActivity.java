package com.example.barcodeoffline;

import android.Manifest;
import androidx.activity.ComponentActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends ComponentActivity {
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_STORAGE = 1002;
    private static final int EYE_BG = Color.rgb(250, 244, 224);
    private static final int CARD_BG = Color.rgb(255, 250, 235);
    private static final int PRIMARY = Color.rgb(111, 76, 41);
    private static final int ACCENT = Color.rgb(174, 122, 54);
    private static final int TEXT = Color.rgb(52, 42, 31);
    private static final String PREF = "barcode_tool_pref";
    private static final String KEY_HISTORY = "history";

    private LinearLayout root;
    private EditText inputText;
    private ImageView previewImage;
    private TextView resultText;
    private Bitmap latestBitmap;
    private String latestValue = "";
    private BarcodeFormat currentFormat = BarcodeFormat.QR_CODE;
    private final ArrayList<String> historyItems = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private ActivityResultLauncher<ScanOptions> scanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                latestValue = result.getContents();
                vibrateOnce();
                copyText(latestValue);
                addHistory("扫描", result.getFormatName(), latestValue);
                showResult("扫描结果", latestValue, result.getFormatName());
            } else {
                toast("已取消扫码");
            }
        });
        loadHistory();
        showHome();
    }

    private void showHome() {
        root = pageRoot();
        TextView title = title("离线条码工具");
        TextView sub = text("扫描一维码/二维码，也可以手动输入内容生成条码。全程离线使用。", 15);
        root.addView(title);
        root.addView(sub);
        root.addView(space(18));
        root.addView(primaryButton("扫码识别", v -> startScan()));
        root.addView(primaryButton("输入生成", v -> showGenerate()));
        root.addView(primaryButton("历史记录", v -> showHistory()));
        root.addView(space(14));
        root.addView(text("护眼暖色主题 · 本地历史 · 一键复制 · 保存图片", 14));
        setContentView(wrap(root));
    }

    private void startScan() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        ScanOptions options = new ScanOptions();
        options.setPrompt("将条码放入框内自动识别");
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(false);
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);
        scanLauncher.launch(options);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveLatestBitmap();
        } else {
            toast(requestCode == REQ_CAMERA ? "没有相机权限，无法扫码" : "没有存储权限，无法保存图片");
        }
    }

    private void showResult(String titleText, String value, String formatName) {
        root = pageRoot();
        root.addView(title(titleText));
        root.addView(label("类型：" + formatName));
        resultText = resultBox(value);
        root.addView(resultText);
        root.addView(primaryButton("复制内容", v -> copyText(value)));
        root.addView(primaryButton("用这个值生成条码", v -> showGenerateWithValue(value)));
        root.addView(secondButton("返回首页", v -> showHome()));
        setContentView(wrap(root));
    }

    private void showGenerate() {
        showGenerateWithValue("");
    }

    private void showGenerateWithValue(String value) {
        root = pageRoot();
        root.addView(title("输入生成"));
        inputText = new EditText(this);
        inputText.setText(value);
        inputText.setTextColor(TEXT);
        inputText.setHintTextColor(Color.rgb(140, 122, 95));
        inputText.setHint("输入要生成的内容");
        inputText.setSingleLine(false);
        inputText.setMinLines(3);
        inputText.setGravity(Gravity.TOP);
        inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputText.setBackgroundColor(CARD_BG);
        inputText.setPadding(18, 18, 18, 18);
        root.addView(inputText, matchWrap());

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton qr = radio("二维码", true);
        RadioButton code = radio("一维码 Code128", false);
        group.addView(qr);
        group.addView(code);
        group.setOnCheckedChangeListener((g, checkedId) -> currentFormat = checkedId == code.getId() ? BarcodeFormat.CODE_128 : BarcodeFormat.QR_CODE);
        root.addView(group);

        previewImage = new ImageView(this);
        previewImage.setBackgroundColor(Color.WHITE);
        previewImage.setPadding(16, 16, 16, 16);
        root.addView(previewImage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        root.addView(primaryButton("生成预览", v -> generatePreview()));
        root.addView(primaryButton("保存图片", v -> saveLatestBitmap()));
        root.addView(primaryButton("分享图片", v -> shareLatestBitmap()));
        root.addView(secondButton("返回首页", v -> showHome()));
        if (value != null && value.trim().length() > 0) {
            generatePreview();
        }
        setContentView(wrap(root));
    }

    private void generatePreview() {
        String value = inputText.getText().toString().trim();
        if (value.length() == 0) {
            toast("请输入内容");
            return;
        }
        try {
            int width = currentFormat == BarcodeFormat.QR_CODE ? 720 : 900;
            int height = currentFormat == BarcodeFormat.QR_CODE ? 720 : 280;
            latestBitmap = createBarcode(value, currentFormat, width, height);
            latestValue = value;
            previewImage.setImageBitmap(latestBitmap);
            addHistory("生成", currentFormat == BarcodeFormat.QR_CODE ? "QR_CODE" : "CODE_128", value);
            toast("已生成");
        } catch (Exception e) {
            toast("生成失败：" + e.getMessage());
        }
    }

    private Bitmap createBarcode(String value, BarcodeFormat format, int width, int height) throws WriterException {
        BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void saveLatestBitmap() {
        if (latestBitmap == null) {
            toast("请先生成预览");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
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
            toast("保存失败：" + e.getMessage());
        }
    }

    private void shareLatestBitmap() {
        if (latestBitmap == null) {
            toast("请先生成预览");
            return;
        }
        try {
            File cacheDir = new File(getCacheDir(), "share");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, "barcode.png");
            FileOutputStream fos = new FileOutputStream(file);
            latestBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享条码图片"));
        } catch (Exception e) {
            toast("分享失败：" + e.getMessage());
        }
    }

    private void showHistory() {
        root = pageRoot();
        root.addView(title("历史记录"));
        if (historyItems.isEmpty()) {
            root.addView(text("暂无历史记录", 16));
        } else {
            ListView listView = new ListView(this);
            historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyItems);
            listView.setAdapter(historyAdapter);
            listView.setOnItemClickListener((parent, view, position, id) -> {
                String item = historyItems.get(position);
                String value = extractValue(item);
                copyText(value);
                showGenerateWithValue(value);
            });
            root.addView(listView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(420)));
        }
        root.addView(primaryButton("清空历史", v -> clearHistory()));
        root.addView(secondButton("返回首页", v -> showHome()));
        setContentView(wrap(root));
    }

    private void addHistory(String action, String format, String value) {
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        String item = time + "  [" + action + "/" + format + "]  " + value;
        historyItems.remove(item);
        historyItems.add(0, item);
        while (historyItems.size() > 100) historyItems.remove(historyItems.size() - 1);
        saveHistory();
    }

    private String extractValue(String item) {
        int idx = item.indexOf("]  ");
        return idx >= 0 ? item.substring(idx + 3) : item;
    }

    private void loadHistory() {
        historyItems.clear();
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String json = sp.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) historyItems.add(arr.getString(i));
        } catch (JSONException ignored) {
        }
    }

    private void saveHistory() {
        JSONArray arr = new JSONArray();
        for (String item : historyItems) arr.put(item);
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void clearHistory() {
        historyItems.clear();
        saveHistory();
        showHistory();
    }

    private void copyText(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("barcode", value));
        toast("已复制");
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
        } catch (Exception ignored) {
        }
    }

    private LinearLayout pageRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(22), dp(20), dp(22));
        layout.setBackgroundColor(EYE_BG);
        return layout;
    }

    private ScrollView wrap(View view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(EYE_BG);
        scroll.addView(view);
        return scroll;
    }

    private TextView title(String value) {
        TextView tv = text(value, 26);
        tv.setTextColor(PRIMARY);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        tv.setPadding(0, 0, 0, dp(14));
        return tv;
    }

    private TextView label(String value) {
        TextView tv = text(value, 15);
        tv.setTextColor(ACCENT);
        return tv;
    }

    private TextView text(String value, int sp) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(TEXT);
        tv.setLineSpacing(4, 1.1f);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    private TextView resultBox(String value) {
        TextView tv = text(value, 18);
        tv.setTextIsSelectable(true);
        tv.setBackgroundColor(CARD_BG);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        return tv;
    }

    private RadioButton radio(String value, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setText(value);
        rb.setTextSize(16);
        rb.setTextColor(TEXT);
        rb.setChecked(checked);
        rb.setId(View.generateViewId());
        return rb;
    }

    private Button primaryButton(String value, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextSize(17);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(PRIMARY);
        btn.setAllCaps(false);
        btn.setOnClickListener(listener);
        btn.setPadding(0, dp(10), 0, dp(10));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(8), 0, dp(8));
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button secondButton(String value, View.OnClickListener listener) {
        Button btn = primaryButton(value, listener);
        btn.setTextColor(PRIMARY);
        btn.setBackgroundColor(Color.rgb(239, 226, 191));
        return btn;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}