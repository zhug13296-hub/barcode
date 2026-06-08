package com.example.barcodeoffline;

import android.app.AlertDialog;
import androidx.core.content.ContextCompat;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录 — Material Chip筛选、搜索、导出
 */
public class HistoryActivity extends ComponentActivity {

    private ScanDbHelper db;
    private List<ScanDbHelper.Record> currentRecords = new ArrayList<>();
    private List<String> displayItems = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private EditText searchInput;
    private ListView listView;
    private View emptyView;
    private TextView btnCount;
    private ChipGroup filterChips;
    private Handler searchHandler;
    private final Runnable searchRunnable = this::loadRecords;

    private int currentFilter = -1; // -1=全部, 0=扫描, 1=生成

    private static final String[][] FILTER_OPTIONS = {
            {"全部", "-1"}, {"扫描", "0"}, {"生成", "1"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        db = new ScanDbHelper(this);
        searchHandler = new Handler(Looper.getMainLooper());

        searchInput = findViewById(R.id.search_input);
        listView = findViewById(R.id.history_list);
        emptyView = findViewById(R.id.empty_view);
        btnCount = findViewById(R.id.btn_count);
        filterChips = findViewById(R.id.filter_chips);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayItems);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < currentRecords.size()) showRecordActions(currentRecords.get(position));
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < currentRecords.size()) {
                ScanDbHelper.Record record = currentRecords.get(position);
                new AlertDialog.Builder(this)
                        .setTitle("删除记录")
                        .setMessage("确定删除？\n" + record.content)
                        .setPositiveButton("删除", (d, w) -> { db.delete(record.id); loadRecords(); toast("已删除"); })
                        .setNegativeButton("取消", null)
                        .show();
            }
            return true;
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { loadRecords(); return true; }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { scheduleLoadRecords(); }
        });

        initFilterChips();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_export).setOnClickListener(v -> showExportDialog());
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清空历史")
                    .setMessage("确定清空所有记录？不可恢复。")
                    .setPositiveButton("清空", (d, w) -> { db.deleteAll(); loadRecords(); toast("已清空"); })
                    .setNegativeButton("取消", null)
                    .show();
        });

        loadRecords();
    }

    private void scheduleLoadRecords() {
        searchHandler.removeCallbacks(searchRunnable);
        searchHandler.postDelayed(searchRunnable, 250);
    }

    private void initFilterChips() {
        for (String[] opt : FILTER_OPTIONS) {
            final int filterValue = Integer.parseInt(opt[1]);
            Chip chip = new Chip(this);
            chip.setText(opt[0]);
            chip.setCheckable(true);
            chip.setChecked(filterValue == currentFilter);
            chip.setClickable(true);
            chip.setTextSize(13);
            chip.setChipMinHeightResource(R.dimen.btn_height_sm);
            chip.setCheckedIconVisible(false);
            chip.setChipCornerRadiusResource(R.dimen.radius_full);
            chip.setChipStrokeColorResource(R.color.outline_variant);
            chip.setChipStrokeWidth(0.5f);
            chip.setTag(filterValue);

            boolean selected = (filterValue == currentFilter);
            chip.setChipBackgroundColorResource(selected ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_secondary));

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFilter = (int) buttonView.getTag();
                    refreshFilterChips();
                    loadRecords();
                }
            });
            filterChips.addView(chip);
        }
    }

    private void refreshFilterChips() {
        for (int i = 0; i < filterChips.getChildCount(); i++) {
            Chip chip = (Chip) filterChips.getChildAt(i);
            int filterValue = (int) chip.getTag();
            boolean selected = (currentFilter == filterValue);
            chip.setOnCheckedChangeListener(null);
            chip.setChecked(selected);
            chip.setChipBackgroundColorResource(selected ? R.color.primary : R.color.bg_surface_variant);
            chip.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_secondary));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFilter = (int) buttonView.getTag();
                    refreshFilterChips();
                    loadRecords();
                }
            });
        }
    }

    private void loadRecords() {
        String keyword = searchInput.getText().toString().trim();
        if (!keyword.isEmpty()) {
            currentRecords = db.search(keyword);
        } else if (currentFilter == 0) {
            currentRecords = db.queryByMode(ScanDbHelper.MODE_SCAN);
        } else if (currentFilter == 1) {
            currentRecords = db.queryByMode(ScanDbHelper.MODE_GENERATE);
        } else {
            currentRecords = db.queryRecent(500);
        }

        displayItems.clear();
        for (ScanDbHelper.Record r : currentRecords) displayItems.add(r.getDisplayText());
        adapter.notifyDataSetChanged();

        boolean empty = currentRecords.isEmpty();
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        btnCount.setText("共 " + currentRecords.size() + " 条");
    }

    private void showRecordActions(ScanDbHelper.Record record) {
        ScanResultParser.ParsedResult parsed = ScanResultParser.parse(record.content);
        new AlertDialog.Builder(this)
                .setTitle(parsed.displayType)
                .setMessage(record.content)
                .setNeutralButton("复制", (d, w) -> copyText(record.content))
                .setPositiveButton(parsed.actionLabel, (d, w) -> {
                    Intent intent = ScanResultParser.createActionIntent(this, parsed);
                    if (intent != null) {
                        try { startActivity(intent); } catch (Exception e) { copyText(record.content); }
                    } else {
                        copyText(record.content);
                    }
                })
                .setNegativeButton("生成条码", (d, w) -> {
                    startActivity(new Intent(this, GenerateActivity.class).putExtra("initValue", record.content));
                })
                .show();
    }

    private void showExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("导出历史记录")
                .setMessage("选择导出格式")
                .setPositiveButton("CSV", (d, w) -> shareFile(db.exportCsv(), "barcode_history.csv", "text/csv"))
                .setNeutralButton("JSON", (d, w) -> shareFile(db.exportJson(), "barcode_history.json", "application/json"))
                .setNegativeButton("取消", null)
                .show();
    }

    private void shareFile(String content, String filename, String mimeType) {
        try {
            File cacheDir = new File(getCacheDir(), "export");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File file = new File(cacheDir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "导出历史记录"));
        } catch (Exception e) {
            toast("导出失败: " + e.getMessage());
        }
    }

    private void copyText(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("barcode", value));
        toast("已复制");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
