package com.example.barcodeoffline;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private static final int[] PRESET_COLORS = {
            Color.BLACK,
            Color.WHITE,
            Color.parseColor("#7B5B3A"), // 棕色
            Color.parseColor("#5088B8"), // 蓝色
            Color.parseColor("#5E9C42"), // 绿色
            Color.parseColor("#C25550"), // 红色
            Color.parseColor("#8B6DAF"), // 紫色
            Color.parseColor("#D4952B"), // 橙色
    };

    private static final String[] PRESET_COLOR_NAMES = {
            "黑色", "白色", "棕色", "蓝色", "绿色", "红色", "紫色", "橙色"
    };

    private SwitchMaterial switchVibration, switchSound, switchAutoScan;
    private TextInputEditText editPrefix, editSuffix;
    private ChipGroup chipGroupFg, chipGroupBg;
    private int selectedFgColor = Color.BLACK;
    private int selectedBgColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadSettings();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchVibration = findViewById(R.id.switch_vibration);
        switchSound = findViewById(R.id.switch_sound);
        switchAutoScan = findViewById(R.id.switch_auto_scan);
        editPrefix = findViewById(R.id.edit_prefix);
        editSuffix = findViewById(R.id.edit_suffix);
        chipGroupFg = findViewById(R.id.chip_group_fg);
        chipGroupBg = findViewById(R.id.chip_group_bg);

        setupColorChips(chipGroupFg, true);
        setupColorChips(chipGroupBg, false);

        MaterialButton btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void setupColorChips(ChipGroup chipGroup, boolean isForeground) {
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int color = PRESET_COLORS[i];
            String name = PRESET_COLOR_NAMES[i];

            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setChipBackgroundColorResource(R.color.bg_surface_variant);
            chip.setChipStrokeColorResource(R.color.outline_variant);
            chip.setChipStrokeWidthResource(R.dimen.elevation_low);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            circle.setSize(dp(20), dp(20));
            chip.setChipIcon(circle);
            chip.setChipIconVisible(true);
            chip.setChipIconSize(dp(20));

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (isForeground) {
                        selectedFgColor = color;
                    } else {
                        selectedBgColor = color;
                    }
                }
            });

            chipGroup.addView(chip);
        }
    }

    private void loadSettings() {
        switchVibration.setChecked(ScanPreferences.getVibrationEnabled(this));
        switchSound.setChecked(ScanPreferences.getSoundEnabled(this));
        switchAutoScan.setChecked(ScanPreferences.getAutoScanEnabled(this));

        String prefix = ScanPreferences.getCustomPrefix(this);
        String suffix = ScanPreferences.getCustomSuffix(this);
        if (prefix != null) editPrefix.setText(prefix);
        if (suffix != null) editSuffix.setText(suffix);

        selectedFgColor = ScanPreferences.getQrForegroundColor(this);
        selectedBgColor = ScanPreferences.getQrBackgroundColor(this);

        selectChipByColor(chipGroupFg, selectedFgColor);
        selectChipByColor(chipGroupBg, selectedBgColor);
    }

    private void selectChipByColor(ChipGroup chipGroup, int color) {
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            if (PRESET_COLORS[i] == color) {
                Chip chip = (Chip) chipGroup.getChildAt(i);
                if (chip != null) chip.setChecked(true);
                return;
            }
        }
    }

    private void saveSettings() {
        ScanPreferences.setVibrationEnabled(this, switchVibration.isChecked());
        ScanPreferences.setSoundEnabled(this, switchSound.isChecked());
        ScanPreferences.setAutoScanEnabled(this, switchAutoScan.isChecked());

        String prefix = editPrefix.getText() != null ? editPrefix.getText().toString() : "";
        String suffix = editSuffix.getText() != null ? editSuffix.getText().toString() : "";
        ScanPreferences.setCustomPrefix(this, prefix);
        ScanPreferences.setCustomSuffix(this, suffix);

        ScanPreferences.setQrForegroundColor(this, selectedFgColor);
        ScanPreferences.setQrBackgroundColor(this, selectedBgColor);

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
