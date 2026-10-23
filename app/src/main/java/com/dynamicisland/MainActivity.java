package com.dynamicisland;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.content.ComponentName;
import android.content.SharedPreferences;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_CODE = 1001;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("dynamic_island_prefs", MODE_PRIVATE);

        // Build UI programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0A0A0A"));
        scrollView.setFillViewport(true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(24), dp(48), dp(24), dp(24));
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Title
        TextView title = new TextView(this);
        title.setText("Dynamic Island");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title, matchWrap());

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Bring iOS-style Dynamic Island to Android");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#888888"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(32));
        mainLayout.addView(subtitle, matchWrap());

        // Island Preview
        View preview = new View(this);
        preview.setBackgroundResource(R.drawable.island_pill);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(200), dp(40));
        previewParams.gravity = Gravity.CENTER;
        previewParams.bottomMargin = dp(32);
        mainLayout.addView(preview, previewParams);

        // Feature cards
        addFeatureCard(mainLayout, "🎵", "Music Controls", "See now playing info, control playback", "music_enabled");
        addFeatureCard(mainLayout, "📞", "Phone Calls", "Incoming & active call display", "calls_enabled");
        addFeatureCard(mainLayout, "⏱️", "Timer & Stopwatch", "Live countdown in the island", "timer_enabled");
        addFeatureCard(mainLayout, "🔋", "Battery & Charging", "Charging animation & status", "battery_enabled");
        addFeatureCard(mainLayout, "🔔", "Notifications", "Preview notifications in the island", "notif_enabled");
        addFeatureCard(mainLayout, "📍", "Live Activities", "GPS, delivery, sports scores", "live_enabled");

        // Spacer
        View spacer = new View(this);
        mainLayout.addView(spacer, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));

        // Enable button
        Button enableBtn = new Button(this);
        enableBtn.setText("⚡ ACTIVATE DYNAMIC ISLAND");
        enableBtn.setTextColor(Color.WHITE);
        enableBtn.setTextSize(16);
        enableBtn.setTypeface(null, Typeface.BOLD);
        enableBtn.setBackgroundResource(R.drawable.btn_activate);
        enableBtn.setPadding(dp(24), dp(16), dp(24), dp(16));
        enableBtn.setOnClickListener(v -> activateIsland());
        LinearLayout.LayoutParams btnParams = matchWrap();
        btnParams.topMargin = dp(8);
        mainLayout.addView(enableBtn, btnParams);

        // Disable button
        Button disableBtn = new Button(this);
        disableBtn.setText("Deactivate");
        disableBtn.setTextColor(Color.parseColor("#FF4444"));
        disableBtn.setTextSize(14);
        disableBtn.setBackgroundColor(Color.TRANSPARENT);
        disableBtn.setOnClickListener(v -> deactivateIsland());
        LinearLayout.LayoutParams disBtnParams = matchWrap();
        disBtnParams.topMargin = dp(8);
        mainLayout.addView(disableBtn, disBtnParams);

        // Notification access button
        Button notifBtn = new Button(this);
        notifBtn.setText("Grant Notification Access");
        notifBtn.setTextColor(Color.parseColor("#4FC3F7"));
        notifBtn.setTextSize(13);
        notifBtn.setBackgroundColor(Color.TRANSPARENT);
        notifBtn.setOnClickListener(v -> {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        });
        LinearLayout.LayoutParams notifBtnParams = matchWrap();
        notifBtnParams.topMargin = dp(4);
        mainLayout.addView(notifBtn, notifBtnParams);

        // Status
        TextView status = new TextView(this);
        status.setText("v1.0 • Dynamic Island for Android");
        status.setTextSize(12);
        status.setTextColor(Color.parseColor("#444444"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, dp(16));
        mainLayout.addView(status, matchWrap());

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        // Make status bar transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#0A0A0A"));
            window.setNavigationBarColor(Color.parseColor("#0A0A0A"));
        }
    }

    private void addFeatureCard(LinearLayout parent, String icon, String name, String desc, String prefKey) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(24);
        card.addView(iconTv, new LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(8);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(Color.WHITE);
        nameTv.setTextSize(15);
        nameTv.setTypeface(null, Typeface.BOLD);
        textLayout.addView(nameTv);

        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextColor(Color.parseColor("#888888"));
        descTv.setTextSize(12);
        textLayout.addView(descTv);

        card.addView(textLayout, textParams);

        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(prefKey, true));
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(prefKey, checked).apply();
        });
        card.addView(toggle, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(8);
        parent.addView(card, cardParams);
    }

    private void activateIsland() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE);
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show();
            return;
        }

        Intent serviceIntent = new Intent(this, DynamicIslandService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Dynamic Island activated! ✨", Toast.LENGTH_SHORT).show();
    }

    private void deactivateIsland() {
        Intent serviceIntent = new Intent(this, DynamicIslandService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "Dynamic Island deactivated", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                activateIsland();
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
