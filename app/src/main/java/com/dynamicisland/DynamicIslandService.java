package com.dynamicisland;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;

public class DynamicIslandService extends Service {

    private static final String CHANNEL_ID = "dynamic_island_channel";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private View islandView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private SharedPreferences prefs;

    // Island state
    private boolean isExpanded = false;
    private boolean isLongExpanded = false;
    private int currentMode = MODE_IDLE;

    private static final int MODE_IDLE = 0;
    private static final int MODE_MUSIC = 1;
    private static final int MODE_CALL = 2;
    private static final int MODE_TIMER = 3;
    private static final int MODE_BATTERY = 4;
    private static final int MODE_NOTIFICATION = 5;
    private static final int MODE_LIVE = 6;

    // Views
    private FrameLayout islandContainer;
    private LinearLayout compactContent;
    private LinearLayout expandedContent;
    private GradientDrawable islandBackground;

    // Music
    private TextView musicTitle;
    private TextView musicArtist;
    private View musicWave1, musicWave2, musicWave3;
    private TextView compactMusicTitle;

    // Timer
    private TextView timerText;
    private TextView compactTimerText;
    private CountDownTimer activeTimer;
    private long timerMillisLeft = 0;
    private boolean timerRunning = false;

    // Battery
    private TextView batteryText;
    private ProgressBar batteryProgress;
    private TextView compactBatteryText;

    // Call
    private TextView callText;
    private TextView callTimer;
    private TextView compactCallText;

    // Notification
    private TextView notifTitle;
    private TextView notifBody;
    private TextView compactNotifText;

    // Dimensions
    private int compactWidth;
    private int compactHeight;
    private int expandedWidth;
    private int expandedHeight;
    private int longExpandedHeight;

    // Touch
    private float touchStartX, touchStartY;
    private long touchStartTime;
    private boolean isDragging = false;

    // Receivers
    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver islandCommandReceiver;

    // Media
    private MediaSessionManager mediaSessionManager;
    private MediaController activeMediaController;
    private MediaController.Callback mediaCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("dynamic_island_prefs", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;

        compactWidth = (int) (160 * density);
        compactHeight = (int) (36 * density);
        expandedWidth = (int) (340 * density);
        expandedHeight = (int) (80 * density);
        longExpandedHeight = (int) (180 * density);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        createIslandView();
        registerReceivers();
        setupMediaListener();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Dynamic Island",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return builder
            .setContentTitle("Dynamic Island Active")
            .setContentText("Tap to configure")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void createIslandView() {
        // Main container
        islandContainer = new FrameLayout(this);

        // Background drawable
        islandBackground = new GradientDrawable();
        islandBackground.setShape(GradientDrawable.RECTANGLE);
        islandBackground.setColor(Color.parseColor("#1A1A1A"));
        islandBackground.setCornerRadius(compactHeight / 2f);
        islandBackground.setStroke(1, Color.parseColor("#333333"));
        islandContainer.setBackground(islandBackground);
        islandContainer.setElevation(dp(8));
        islandContainer.setClipToOutline(true);

        // Compact content
        compactContent = new LinearLayout(this);
        compactContent.setOrientation(LinearLayout.HORIZONTAL);
        compactContent.setGravity(Gravity.CENTER);
        compactContent.setPadding(dp(12), dp(4), dp(12), dp(4));

        // Left indicator dot (animated)
        View leftDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#34C759"));
        leftDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(6);
        compactContent.addView(leftDot, dotParams);

        // Compact text area
        compactMusicTitle = new TextView(this);
        compactMusicTitle.setTextColor(Color.WHITE);
        compactMusicTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactMusicTitle.setSingleLine(true);
        compactMusicTitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams ctParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        compactContent.addView(compactMusicTitle, ctParams);

        compactTimerText = new TextView(this);
        compactTimerText.setTextColor(Color.parseColor("#FF9F0A"));
        compactTimerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactTimerText.setVisibility(View.GONE);
        compactContent.addView(compactTimerText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        compactBatteryText = new TextView(this);
        compactBatteryText.setTextColor(Color.parseColor("#30D158"));
        compactBatteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactBatteryText.setVisibility(View.GONE);
        compactContent.addView(compactBatteryText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        compactCallText = new TextView(this);
        compactCallText.setTextColor(Color.parseColor("#30D158"));
        compactCallText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactCallText.setVisibility(View.GONE);
        compactContent.addView(compactCallText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        compactNotifText = new TextView(this);
        compactNotifText.setTextColor(Color.WHITE);
        compactNotifText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        compactNotifText.setSingleLine(true);
        compactNotifText.setVisibility(View.GONE);
        compactContent.addView(compactNotifText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Music wave visualization (right side)
        LinearLayout waveContainer = new LinearLayout(this);
        waveContainer.setOrientation(LinearLayout.HORIZONTAL);
        waveContainer.setGravity(Gravity.CENTER_VERTICAL);
        waveContainer.setPadding(dp(6), 0, 0, 0);

        musicWave1 = createWaveBar();
        musicWave2 = createWaveBar();
        musicWave3 = createWaveBar();
        waveContainer.addView(musicWave1, waveBarParams());
        waveContainer.addView(musicWave2, waveBarParams());
        waveContainer.addView(musicWave3, waveBarParams());
        waveContainer.setVisibility(View.GONE);
        waveContainer.setTag("waveContainer");
        compactContent.addView(waveContainer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        islandContainer.addView(compactContent, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Expanded content
        expandedContent = new LinearLayout(this);
        expandedContent.setOrientation(LinearLayout.VERTICAL);
        expandedContent.setPadding(dp(16), dp(12), dp(16), dp(12));
        expandedContent.setVisibility(View.GONE);
        expandedContent.setAlpha(0f);

        // Music expanded section
        LinearLayout musicSection = createMusicExpandedSection();
        expandedContent.addView(musicSection, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Timer expanded section
        LinearLayout timerSection = createTimerExpandedSection();
        expandedContent.addView(timerSection, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Battery expanded section
        LinearLayout batterySection = createBatteryExpandedSection();
        expandedContent.addView(batterySection, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Call expanded section
        LinearLayout callSection = createCallExpandedSection();
        expandedContent.addView(callSection, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Notification expanded section
        LinearLayout notifSection = createNotifExpandedSection();
        expandedContent.addView(notifSection, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        islandContainer.addView(expandedContent, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Touch handling
        setupTouchListeners();

        // Window params
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            compactWidth,
            compactHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(8);

        windowManager.addView(islandContainer, params);

        // Entrance animation
        islandContainer.setScaleX(0f);
        islandContainer.setScaleY(0f);
        islandContainer.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();

        // Start idle pulse animation
        startIdlePulse();
    }

    private View createWaveBar() {
        View bar = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#FF2D55"));
        bg.setCornerRadius(dp(2));
        bar.setBackground(bg);
        return bar;
    }

    private LinearLayout.LayoutParams waveBarParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(3), dp(12));
        p.rightMargin = dp(2);
        return p;
    }

    private LinearLayout createMusicExpandedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("music_section");
        section.setVisibility(View.GONE);

        musicTitle = new TextView(this);
        musicTitle.setTextColor(Color.WHITE);
        musicTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        musicTitle.setText("Not Playing");
        musicTitle.setSingleLine(true);
        section.addView(musicTitle);

        musicArtist = new TextView(this);
        musicArtist.setTextColor(Color.parseColor("#AAAAAA"));
        musicArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        musicArtist.setText("");
        musicArtist.setSingleLine(true);
        section.addView(musicArtist);

        // Controls row
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, 0);

        TextView prevBtn = createControlButton("⏮");
        prevBtn.setOnClickListener(v -> sendMediaCommand("prev"));
        controls.addView(prevBtn);

        TextView playBtn = createControlButton("⏯");
        playBtn.setOnClickListener(v -> sendMediaCommand("toggle"));
        controls.addView(playBtn);

        TextView nextBtn = createControlButton("⏭");
        nextBtn.setOnClickListener(v -> sendMediaCommand("next"));
        controls.addView(nextBtn);

        section.addView(controls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return section;
    }

    private LinearLayout createTimerExpandedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setTag("timer_section");
        section.setVisibility(View.GONE);

        timerText = new TextView(this);
        timerText.setTextColor(Color.parseColor("#FF9F0A"));
        timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        timerText.setText("00:00");
        timerText.setGravity(Gravity.CENTER);
        section.addView(timerText);

        LinearLayout timerControls = new LinearLayout(this);
        timerControls.setOrientation(LinearLayout.HORIZONTAL);
        timerControls.setGravity(Gravity.CENTER);
        timerControls.setPadding(0, dp(8), 0, 0);

        // Preset buttons
        String[] presets = {"1m", "3m", "5m", "10m"};
        int[] presetSecs = {60, 180, 300, 600};
        for (int i = 0; i < presets.length; i++) {
            final int seconds = presetSecs[i];
            TextView presetBtn = new TextView(this);
            presetBtn.setText(presets[i]);
            presetBtn.setTextColor(Color.WHITE);
            presetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            presetBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable presetBg = new GradientDrawable();
            presetBg.setShape(GradientDrawable.RECTANGLE);
            presetBg.setColor(Color.parseColor("#333333"));
            presetBg.setCornerRadius(dp(12));
            presetBtn.setBackground(presetBg);
            presetBtn.setGravity(Gravity.CENTER);
            presetBtn.setOnClickListener(v -> startTimer(seconds * 1000L));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.setMargins(dp(4), 0, dp(4), 0);
            timerControls.addView(presetBtn, pp);
        }

        // Stop button
        TextView stopBtn = new TextView(this);
        stopBtn.setText("✕");
        stopBtn.setTextColor(Color.parseColor("#FF453A"));
        stopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        stopBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setShape(GradientDrawable.RECTANGLE);
        stopBg.setColor(Color.parseColor("#3A1515"));
        stopBg.setCornerRadius(dp(12));
        stopBtn.setBackground(stopBg);
        stopBtn.setOnClickListener(v -> stopTimer());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(8);
        timerControls.addView(stopBtn, sp);

        section.addView(timerControls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return section;
    }

    private LinearLayout createBatteryExpandedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setTag("battery_section");
        section.setVisibility(View.GONE);

        batteryText = new TextView(this);
        batteryText.setTextColor(Color.parseColor("#30D158"));
        batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        batteryText.setText("⚡ 100%");
        batteryText.setGravity(Gravity.CENTER);
        section.addView(batteryText);

        batteryProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        batteryProgress.setMax(100);
        batteryProgress.setProgress(100);
        LinearLayout.LayoutParams bpParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        bpParams.topMargin = dp(8);
        section.addView(batteryProgress, bpParams);

        return section;
    }

    private LinearLayout createCallExpandedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setTag("call_section");
        section.setVisibility(View.GONE);

        callText = new TextView(this);
        callText.setTextColor(Color.parseColor("#30D158"));
        callText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        callText.setText("📞 Incoming Call");
        callText.setGravity(Gravity.CENTER);
        section.addView(callText);

        callTimer = new TextView(this);
        callTimer.setTextColor(Color.parseColor("#AAAAAA"));
        callTimer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        callTimer.setText("00:00");
        callTimer.setGravity(Gravity.CENTER);
        section.addView(callTimer);

        return section;
    }

    private LinearLayout createNotifExpandedSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("notif_section");
        section.setVisibility(View.GONE);

        notifTitle = new TextView(this);
        notifTitle.setTextColor(Color.WHITE);
        notifTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        notifTitle.setText("Notification");
        notifTitle.setSingleLine(true);
        section.addView(notifTitle);

        notifBody = new TextView(this);
        notifBody.setTextColor(Color.parseColor("#AAAAAA"));
        notifBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        notifBody.setText("");
        notifBody.setMaxLines(2);
        section.addView(notifBody);

        return section;
    }

    private TextView createControlButton(String text) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        btn.setPadding(dp(16), dp(4), dp(16), dp(4));
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private void setupTouchListeners() {
        islandContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    touchStartTime = System.currentTimeMillis();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touchStartX;
                    float dy = event.getRawY() - touchStartY;
                    if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) {
                        isDragging = true;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - touchStartTime;
                    if (!isDragging) {
                        if (duration < 300) {
                            // Short tap
                            onIslandTap();
                        } else if (duration < 1000) {
                            // Long press
                            onIslandLongPress();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void onIslandTap() {
        hapticFeedback();
        if (isLongExpanded) {
            collapseIsland();
        } else if (isExpanded) {
            if (currentMode == MODE_TIMER) {
                // Expand to long view for timer
                expandIslandLong();
            } else {
                collapseIsland();
            }
        } else {
            expandIsland();
        }
    }

    private void onIslandLongPress() {
        hapticFeedback();
        if (!isLongExpanded) {
            expandIslandLong();
        } else {
            collapseIsland();
        }
    }

    private void expandIsland() {
        if (isExpanded) return;
        isExpanded = true;

        showExpandedContentForMode();

        ValueAnimator widthAnim = ValueAnimator.ofInt(params.width, expandedWidth);
        ValueAnimator heightAnim = ValueAnimator.ofInt(params.height, expandedHeight);

        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });
        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(widthAnim, heightAnim);
        set.setDuration(350);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();

        // Update corner radius
        ValueAnimator cornerAnim = ValueAnimator.ofFloat(compactHeight / 2f, dp(24));
        cornerAnim.addUpdateListener(a -> {
            islandBackground.setCornerRadius((float) a.getAnimatedValue());
        });
        cornerAnim.setDuration(350);
        cornerAnim.start();

        // Fade content
        compactContent.animate().alpha(0f).setDuration(150).start();
        expandedContent.setVisibility(View.VISIBLE);
        expandedContent.animate().alpha(1f).setDuration(350).setStartDelay(100).start();

        // Auto-collapse after 5 seconds
        handler.removeCallbacksAndMessages("autoCollapse");
        handler.postDelayed(this::collapseIsland, 5000);
    }

    private void expandIslandLong() {
        isExpanded = true;
        isLongExpanded = true;

        showExpandedContentForMode();

        // Make it focusable for interaction
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        ValueAnimator widthAnim = ValueAnimator.ofInt(params.width, expandedWidth);
        ValueAnimator heightAnim = ValueAnimator.ofInt(params.height, longExpandedHeight);

        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });
        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(widthAnim, heightAnim);
        set.setDuration(400);
        set.setInterpolator(new OvershootInterpolator(0.8f));
        set.start();

        ValueAnimator cornerAnim = ValueAnimator.ofFloat(
            islandBackground.getCornerRadius(), dp(28));
        cornerAnim.addUpdateListener(a -> {
            islandBackground.setCornerRadius((float) a.getAnimatedValue());
        });
        cornerAnim.setDuration(400);
        cornerAnim.start();

        compactContent.animate().alpha(0f).setDuration(150).start();
        expandedContent.setVisibility(View.VISIBLE);
        expandedContent.animate().alpha(1f).setDuration(400).setStartDelay(100).start();

        // Auto-collapse after 10 seconds for long expansion
        handler.removeCallbacksAndMessages("autoCollapse");
        handler.postDelayed(this::collapseIsland, 10000);
    }

    private void collapseIsland() {
        if (!isExpanded && !isLongExpanded) return;

        handler.removeCallbacksAndMessages("autoCollapse");

        // Make non-focusable again
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        ValueAnimator widthAnim = ValueAnimator.ofInt(params.width, compactWidth);
        ValueAnimator heightAnim = ValueAnimator.ofInt(params.height, compactHeight);

        widthAnim.addUpdateListener(a -> {
            params.width = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });
        heightAnim.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            try { windowManager.updateViewLayout(islandContainer, params); } catch (Exception e) {}
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(widthAnim, heightAnim);
        set.setDuration(300);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();

        ValueAnimator cornerAnim = ValueAnimator.ofFloat(
            islandBackground.getCornerRadius(), compactHeight / 2f);
        cornerAnim.addUpdateListener(a -> {
            islandBackground.setCornerRadius((float) a.getAnimatedValue());
        });
        cornerAnim.setDuration(300);
        cornerAnim.start();

        expandedContent.animate().alpha(0f).setDuration(150)
            .withEndAction(() -> expandedContent.setVisibility(View.GONE))
            .start();
        compactContent.animate().alpha(1f).setDuration(300).setStartDelay(150).start();

        isExpanded = false;
        isLongExpanded = false;
    }

    private void showExpandedContentForMode() {
        // Hide all sections
        expandedContent.findViewWithTag("music_section").setVisibility(View.GONE);
        expandedContent.findViewWithTag("timer_section").setVisibility(View.GONE);
        expandedContent.findViewWithTag("battery_section").setVisibility(View.GONE);
        expandedContent.findViewWithTag("call_section").setVisibility(View.GONE);
        expandedContent.findViewWithTag("notif_section").setVisibility(View.GONE);

        // Show active section
        switch (currentMode) {
            case MODE_MUSIC:
                expandedContent.findViewWithTag("music_section").setVisibility(View.VISIBLE);
                break;
            case MODE_TIMER:
                expandedContent.findViewWithTag("timer_section").setVisibility(View.VISIBLE);
                break;
            case MODE_BATTERY:
                expandedContent.findViewWithTag("battery_section").setVisibility(View.VISIBLE);
                break;
            case MODE_CALL:
                expandedContent.findViewWithTag("call_section").setVisibility(View.VISIBLE);
                break;
            case MODE_NOTIFICATION:
                expandedContent.findViewWithTag("notif_section").setVisibility(View.VISIBLE);
                break;
            default:
                // Show a default info
                expandedContent.findViewWithTag("timer_section").setVisibility(View.VISIBLE);
                break;
        }
    }

    // ===== MUSIC =====

    private void setupMediaListener() {
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                // Try to get active sessions (requires notification listener)
                try {
                    android.content.ComponentName cn = new android.content.ComponentName(this, IslandNotificationListener.class);
                    List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                    if (!controllers.isEmpty()) {
                        setupMediaController(controllers.get(0));
                    }

                    mediaSessionManager.addOnActiveSessionsChangedListener(controllers2 -> {
                        if (controllers2 != null && !controllers2.isEmpty()) {
                            setupMediaController(controllers2.get(0));
                        }
                    }, cn);
                } catch (SecurityException e) {
                    // Notification listener not enabled
                }
            }
        } catch (Exception e) {
            // Media listener not available
        }
    }

    private void setupMediaController(MediaController controller) {
        activeMediaController = controller;
        mediaCallback = new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                handler.post(() -> updateMusicInfo(metadata));
            }

            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                handler.post(() -> {
                    if (state != null) {
                        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
                        if (playing && prefs.getBoolean("music_enabled", true)) {
                            setMode(MODE_MUSIC);
                            animateWaves(true);
                            showCompactMusic(true);
                        } else if (state.getState() == PlaybackState.STATE_PAUSED
                                || state.getState() == PlaybackState.STATE_STOPPED) {
                            animateWaves(false);
                            if (currentMode == MODE_MUSIC) {
                                handler.postDelayed(() -> {
                                    if (currentMode == MODE_MUSIC) {
                                        setMode(MODE_IDLE);
                                        showCompactMusic(false);
                                    }
                                }, 3000);
                            }
                        }
                    }
                });
            }
        };
        controller.registerCallback(mediaCallback);

        // Check current state
        if (controller.getMetadata() != null) {
            updateMusicInfo(controller.getMetadata());
        }
        PlaybackState state = controller.getPlaybackState();
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            setMode(MODE_MUSIC);
            animateWaves(true);
            showCompactMusic(true);
        }
    }

    private void updateMusicInfo(MediaMetadata metadata) {
        if (metadata == null) return;
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);

        if (title != null) {
            musicTitle.setText(title);
            compactMusicTitle.setText(title);
        }
        if (artist != null) {
            musicArtist.setText(artist);
        }
    }

    private void showCompactMusic(boolean show) {
        compactMusicTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        View waveContainer = compactContent.findViewWithTag("waveContainer");
        if (waveContainer != null) {
            waveContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void animateWaves(boolean animate) {
        if (animate) {
            animateWave(musicWave1, 300, dp(6), dp(14));
            animateWave(musicWave2, 400, dp(4), dp(16));
            animateWave(musicWave3, 350, dp(8), dp(12));
        } else {
            musicWave1.animate().cancel();
            musicWave2.animate().cancel();
            musicWave3.animate().cancel();
        }
    }

    private void animateWave(View wave, long duration, int minH, int maxH) {
        ValueAnimator anim = ValueAnimator.ofInt(minH, maxH);
        anim.setDuration(duration);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = wave.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            wave.setLayoutParams(lp);
        });
        anim.start();
    }

    private void sendMediaCommand(String command) {
        if (activeMediaController == null) return;
        switch (command) {
            case "prev":
                activeMediaController.getTransportControls().skipToPrevious();
                break;
            case "toggle":
                PlaybackState state = activeMediaController.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    activeMediaController.getTransportControls().pause();
                } else {
                    activeMediaController.getTransportControls().play();
                }
                break;
            case "next":
                activeMediaController.getTransportControls().skipToNext();
                break;
        }
    }

    // ===== TIMER =====

    private void startTimer(long millis) {
        stopTimer();
        timerMillisLeft = millis;
        timerRunning = true;
        setMode(MODE_TIMER);

        activeTimer = new CountDownTimer(millis, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerMillisLeft = millisUntilFinished;
                long secs = millisUntilFinished / 1000;
                long mins = secs / 60;
                secs = secs % 60;
                String timeStr = String.format("%02d:%02d", mins, secs);
                timerText.setText(timeStr);
                compactTimerText.setText(timeStr);
                compactTimerText.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinish() {
                timerText.setText("Done!");
                compactTimerText.setText("Done!");
                timerRunning = false;
                hapticFeedback();

                // Flash animation
                ObjectAnimator flash = ObjectAnimator.ofInt(islandBackground, "color",
                    Color.parseColor("#FF9F0A"), Color.parseColor("#1A1A1A"));
                flash.setDuration(500);
                flash.setRepeatCount(5);
                flash.setRepeatMode(ValueAnimator.REVERSE);
                flash.setEvaluator(new android.animation.ArgbEvaluator());
                flash.start();

                expandIsland();

                handler.postDelayed(() -> {
                    setMode(MODE_IDLE);
                    compactTimerText.setVisibility(View.GONE);
                }, 5000);
            }
        };
        activeTimer.start();
    }

    private void stopTimer() {
        if (activeTimer != null) {
            activeTimer.cancel();
            activeTimer = null;
        }
        timerRunning = false;
        timerText.setText("00:00");
        compactTimerText.setVisibility(View.GONE);
        if (currentMode == MODE_TIMER) {
            setMode(MODE_IDLE);
        }
    }

    // ===== BATTERY =====

    private void registerReceivers() {
        // Battery
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!prefs.getBoolean("battery_enabled", true)) return;

                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

                if (level >= 0 && scale > 0) {
                    int pct = (level * 100) / scale;
                    batteryText.setText((isCharging ? "⚡ " : "🔋 ") + pct + "%");
                    batteryProgress.setProgress(pct);
                    compactBatteryText.setText(pct + "%");

                    // Color based on level
                    int color;
                    if (pct <= 20) {
                        color = Color.parseColor("#FF453A");
                    } else if (pct <= 50) {
                        color = Color.parseColor("#FF9F0A");
                    } else {
                        color = Color.parseColor("#30D158");
                    }
                    batteryText.setTextColor(color);
                    compactBatteryText.setTextColor(color);
                }

                if (isCharging && currentMode == MODE_IDLE) {
                    setMode(MODE_BATTERY);
                    compactBatteryText.setVisibility(View.VISIBLE);
                    expandIsland();
                    handler.postDelayed(() -> {
                        collapseIsland();
                    }, 3000);
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Island command receiver (from notification listener)
        islandCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!prefs.getBoolean("notif_enabled", true)) return;

                String title = intent.getStringExtra("title");
                String body = intent.getStringExtra("body");

                if (title != null) {
                    notifTitle.setText(title);
                    compactNotifText.setText(title);
                    notifBody.setText(body != null ? body : "");

                    setMode(MODE_NOTIFICATION);
                    compactNotifText.setVisibility(View.VISIBLE);

                    // Brief expand
                    expandIsland();

                    handler.postDelayed(() -> {
                        collapseIsland();
                        handler.postDelayed(() -> {
                            if (currentMode == MODE_NOTIFICATION) {
                                setMode(MODE_IDLE);
                                compactNotifText.setVisibility(View.GONE);
                            }
                        }, 3000);
                    }, 3000);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(islandCommandReceiver, new IntentFilter("com.dynamicisland.NOTIFICATION"),
                0x4 /* RECEIVER_NOT_EXPORTED */);
        } else {
            registerReceiver(islandCommandReceiver, new IntentFilter("com.dynamicisland.NOTIFICATION"));
        }
    }

    // ===== PHONE CALLS =====
    private void setupPhoneListener() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                tm.listen(new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String phoneNumber) {
                        if (!prefs.getBoolean("calls_enabled", true)) return;

                        switch (state) {
                            case TelephonyManager.CALL_STATE_RINGING:
                                setMode(MODE_CALL);
                                callText.setText("📞 Incoming Call");
                                compactCallText.setText("📞 Ringing");
                                compactCallText.setVisibility(View.VISIBLE);
                                expandIsland();
                                break;
                            case TelephonyManager.CALL_STATE_OFFHOOK:
                                setMode(MODE_CALL);
                                callText.setText("📞 On Call");
                                compactCallText.setText("📞 Active");
                                compactCallText.setVisibility(View.VISIBLE);
                                break;
                            case TelephonyManager.CALL_STATE_IDLE:
                                if (currentMode == MODE_CALL) {
                                    setMode(MODE_IDLE);
                                    compactCallText.setVisibility(View.GONE);
                                    collapseIsland();
                                }
                                break;
                        }
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE);
            }
        } catch (Exception e) {
            // Permission not granted
        }
    }

    // ===== UTILITIES =====

    private void setMode(int mode) {
        currentMode = mode;
        updateIslandAppearance();
    }

    private void updateIslandAppearance() {
        // Update border glow color based on mode
        int glowColor;
        switch (currentMode) {
            case MODE_MUSIC:
                glowColor = Color.parseColor("#FF2D55");
                break;
            case MODE_CALL:
                glowColor = Color.parseColor("#30D158");
                break;
            case MODE_TIMER:
                glowColor = Color.parseColor("#FF9F0A");
                break;
            case MODE_BATTERY:
                glowColor = Color.parseColor("#30D158");
                break;
            case MODE_NOTIFICATION:
                glowColor = Color.parseColor("#5E5CE6");
                break;
            default:
                glowColor = Color.parseColor("#333333");
                break;
        }

        ObjectAnimator borderAnim = ObjectAnimator.ofInt(islandBackground, "color",
            islandBackground.getColor().getDefaultColor(), Color.parseColor("#1A1A1A"));
        // Just update the stroke
        islandBackground.setStroke(currentMode == MODE_IDLE ? 1 : 2, glowColor);
    }

    private void startIdlePulse() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isExpanded && currentMode == MODE_IDLE) {
                    // Subtle scale pulse
                    islandContainer.animate()
                        .scaleX(1.02f).scaleY(1.02f)
                        .setDuration(1000)
                        .withEndAction(() -> {
                            islandContainer.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(1000)
                                .start();
                        })
                        .start();
                }
                handler.postDelayed(this, 3000);
            }
        }, 2000);
    }

    private void hapticFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(30);
                }
            }
        } catch (Exception e) {}
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // ===== PUBLIC API (for notification listener) =====

    public static void showNotification(Context context, String title, String body) {
        Intent intent = new Intent("com.dynamicisland.NOTIFICATION");
        intent.setPackage(context.getPackageName());
        intent.putExtra("title", title);
        intent.putExtra("body", body);
        context.sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);

        if (islandContainer != null) {
            try {
                windowManager.removeView(islandContainer);
            } catch (Exception e) {}
        }

        if (batteryReceiver != null) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception e) {}
        }
        if (islandCommandReceiver != null) {
            try { unregisterReceiver(islandCommandReceiver); } catch (Exception e) {}
        }
        if (activeMediaController != null && mediaCallback != null) {
            try { activeMediaController.unregisterCallback(mediaCallback); } catch (Exception e) {}
        }
        if (activeTimer != null) {
            activeTimer.cancel();
        }
    }
}
