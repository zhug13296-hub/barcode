package com.example.barcodeoffline;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 首页 — 卡片式导航 + 统计面板
 */
public class MainActivity extends AppCompatActivity {

    private TextView statToday, statTotal, statUnique;
    private ScanDbHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new ScanDbHelper(this);

        statToday = findViewById(R.id.stat_today);
        statTotal = findViewById(R.id.stat_total);
        statUnique = findViewById(R.id.stat_unique);

        findViewById(R.id.card_scan).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class)
                        .putExtra("batchMode", false)));

        findViewById(R.id.card_generate).setOnClickListener(v ->
                startActivity(new Intent(this, GenerateActivity.class)));

        findViewById(R.id.card_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.card_batch).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class)
                        .putExtra("batchMode", true)));

        findViewById(R.id.card_gallery).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class)
                        .putExtra("openGallery", true)));

        findViewById(R.id.card_favorites).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)
                        .putExtra("showFavorites", true)));

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private void refreshStats() {
        statToday.setText(String.valueOf(db.countToday()));
        statTotal.setText(String.valueOf(db.count()));
        statUnique.setText(String.valueOf(db.countDistinct()));
    }
}
