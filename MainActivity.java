package com.lifecalendar;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    SharedPreferences prefs;
    TextView tvStartDate, tvEndDate, tvDaysDone, tvDaysLeft, tvPercent, tvMilestone, tvMode;
    Button btnPickStart, btnPickEnd, btnSetWallpaper;
    TabLayout tabLayout;
    long startMillis = 0, endMillis = 0;
    String currentMode = "year";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("LifeCalPrefs", MODE_PRIVATE);
        tvStartDate   = findViewById(R.id.tvStartDate);
        tvEndDate     = findViewById(R.id.tvEndDate);
        tvDaysDone    = findViewById(R.id.tvDaysDone);
        tvDaysLeft    = findViewById(R.id.tvDaysLeft);
        tvPercent     = findViewById(R.id.tvPercent);
        tvMilestone   = findViewById(R.id.tvMilestone);
        tvMode        = findViewById(R.id.tvMode);
        btnPickStart  = findViewById(R.id.btnPickStart);
        btnPickEnd    = findViewById(R.id.btnPickEnd);
        btnSetWallpaper = findViewById(R.id.btnSetWallpaper);
        tabLayout     = findViewById(R.id.tabLayout);

        startMillis = prefs.getLong("startMillis", 0);
        endMillis   = prefs.getLong("endMillis", 0);
        currentMode = prefs.getString("mode", "year");

        updateUI();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentMode = "year"; break;
                    case 1: currentMode = "love"; break;
                    case 2: currentMode = "goal"; break;
                }
                prefs.edit().putString("mode", currentMode).apply();
                updateUI();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        int idx = currentMode.equals("year") ? 0 : currentMode.equals("love") ? 1 : 2;
        if (tabLayout.getTabAt(idx) != null) tabLayout.getTabAt(idx).select();

        btnPickStart.setOnClickListener(v -> pickDate(true));
        btnPickEnd.setOnClickListener(v -> pickDate(false));
        btnSetWallpaper.setOnClickListener(v -> setWallpaper());
    }

    void pickDate(boolean isStart) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(isStart ? "Pick Start Date" : "Pick End Date")
            .setSelection(isStart
                ? (startMillis > 0 ? startMillis : MaterialDatePicker.todayInUtcMilliseconds())
                : (endMillis   > 0 ? endMillis   : MaterialDatePicker.todayInUtcMilliseconds()))
            .build();
        picker.addOnPositiveButtonClickListener(sel -> {
            if (isStart) { startMillis = sel; prefs.edit().putLong("startMillis", sel).apply(); }
            else          { endMillis   = sel; prefs.edit().putLong("endMillis",   sel).apply(); }
            updateUI();
        });
        picker.show(getSupportFragmentManager(), "DP");
    }

    void updateUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        boolean showDates = !currentMode.equals("year");
        btnPickStart.setVisibility(showDates ? View.VISIBLE : View.GONE);
        btnPickEnd.setVisibility(showDates ? View.VISIBLE : View.GONE);
        tvStartDate.setVisibility(showDates ? View.VISIBLE : View.GONE);
        tvEndDate.setVisibility(showDates ? View.VISIBLE : View.GONE);

        if (currentMode.equals("year")) {
            tvMode.setText("Tracking current year");
        } else {
            tvMode.setText(currentMode.equals("love") ? "Love countdown" : "Goal tracker");
            tvStartDate.setText(startMillis > 0 ? "From: " + sdf.format(new Date(startMillis)) : "From: not set");
            tvEndDate.setText(endMillis > 0 ? "To:     " + sdf.format(new Date(endMillis)) : "To:     not set");
        }

        // Calculate stats
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);      today.set(Calendar.MILLISECOND, 0);

        int total, done;
        if (currentMode.equals("year")) {
            total = today.getActualMaximum(Calendar.DAY_OF_YEAR);
            done  = today.get(Calendar.DAY_OF_YEAR) - 1;
        } else if (startMillis == 0) {
            tvDaysDone.setText("—"); tvDaysLeft.setText("—");
            tvPercent.setText("0%"); tvMilestone.setText("Set your dates above");
            return;
        } else {
            Calendar s = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            s.setTimeInMillis(startMillis);
            Calendar e = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            e.setTimeInMillis(endMillis > 0 ? endMillis : startMillis + 365L * 86400000L);
            total = (int)((e.getTimeInMillis() - s.getTimeInMillis()) / 86400000L) + 1;
            done  = (int)((today.getTimeInMillis() - s.getTimeInMillis()) / 86400000L);
            done  = Math.max(0, Math.min(done, total));
        }

        int left = total - done;
        int pct  = total > 0 ? (int)(done * 100.0 / total) : 0;
        tvDaysDone.setText(String.valueOf(done));
        tvDaysLeft.setText(String.valueOf(left));
        tvPercent.setText(pct + "%");

        if (currentMode.equals("love") && startMillis > 0) {
            int[] ms = {100, 200, 365, 500, 1000, 2000, 5000};
            String m = "Forever & beyond";
            for (int milestone : ms) {
                if (milestone > done) { m = "Next milestone: " + milestone + " days (" + (milestone - done) + " to go)"; break; }
            }
            tvMilestone.setText(m);
        } else {
            tvMilestone.setText(left + " days remaining");
        }
    }

    void setWallpaper() {
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, LifeCalendarWallpaperService.class));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this,
                "Long press home screen > Wallpapers > Live Wallpapers > Life Calendar",
                Toast.LENGTH_LONG).show();
        }
    }
}
