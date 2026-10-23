# 🏝️ Dynamic Island / Origin Island for Android

A feature-complete Dynamic Island / Origin Island style overlay for Android inspired by the latest OS versions.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)
![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)

---

## ✨ Features

- **🎵 Live Media & Now Playing:**
  - Real-time track title & artist metadata
  - Live animated audio visualizer waves
  - Quick playback controls: Previous, Play/Pause, Next

- **⏱️ Interactive Timer & Stopwatch:**
  - Compact countdown display in the pill
  - Expanded timer control with presets (1m, 3m, 5m, 10m)
  - Color-coded finishing pulses & haptic feedback

- **🔋 Battery & Charging State:**
  - Dynamic charging animations
  - Real-time percentage & battery health color tinting (Green / Orange / Red)

- **📞 Incoming & Ongoing Call Pill:**
  - Dynamic call status banner (Ringing, Active call timer)
  - Seamless expansion for call actions

- **🔔 Smart Notification Heads-Up:**
  - Non-intrusive preview of notifications directly inside the pill
  - Smooth expansion and auto-collapse

- **✨ Modern Fluid Physics:**
  - Compact pill idle state with gentle breathing pulse
  - Smooth size transitions using overshoot and decelerate interpolators
  - Touch gesture support: tap to expand, long press for rich controls

---

## 🚀 How to Compile on GitHub (GitHub Actions)

This repository includes a turnkey GitHub Actions workflow (`.github/workflows/build.yml`) that compiles the APK automatically on GitHub's cloud runners:

1. **Create a new repository on GitHub:**
   ```bash
   # From your device/terminal:
   cd ~/DynamicIsland
   git init
   git add .
   git commit -m "feat: Dynamic Island Android app with CI"
   git remote add origin https://github.com/<your-username>/DynamicIsland.git
   git branch -M main
   git push -u origin main
   ```
2. **Automatic Compilation:**
   - GitHub Actions will automatically start building the APK upon pushing.
   - Go to the **Actions** tab on your GitHub repository.
   - Click on the latest workflow run.
3. **Download the APK:**
   - Scroll down to the **Artifacts** section at the bottom of the summary page.
   - Download `DynamicIsland-Debug-APK` and unzip it to get your `.apk`!

---

## 🛠️ Local Compilation (Offline on Android / Termux)

You can also compile it directly on your device:

```bash
cd ~/DynamicIsland
bash build.sh
```

The resulting signed APK will be saved directly to `~/DynamicIsland.apk`.

### Installing:
```bash
# Direct install:
pm install ~/DynamicIsland.apk

# Or copy to internal storage:
cp ~/DynamicIsland.apk /sdcard/Download/
```
Then tap the APK in your file manager to install.

---

## 📋 Required Permissions

When launching the app for the first time:
1. **Appear on Top (Overlay Permission):** Allows the Dynamic Island pill to float over other apps.
2. **Notification Access:** Allows showing media playback and incoming notification previews.
