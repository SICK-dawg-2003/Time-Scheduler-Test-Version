# Time Scheduling App (UniSync) 📅📱

An interactive, color-coded Time Management and Daily Scheduler Android application built with **Jetpack Compose**, supporting **drag-and-drop scheduling**, **conflict discrepancy auto-resolution**, **Google & Outlook Calendar sync configuration**, **Profile Settings**, and **Camera OCR Schedule Scanning** powered by **CameraX** & **ML Kit Text Recognition**.

---

## 🌟 Key Features

- **Interactive Color-Coded Calendar & Daily Timeline**:
  - Color-coded categories: **Work** (Blue), **Personal** (Green), **Health** (Pink), **Study** (Purple), **Urgent** (Orange), and **Other** (Teal).
  - 24-hour hour grid timeline with formatted time strings, completion state, and sync badges.
  - Date strip week selector and category filter pills.
- **Drag-and-Drop Scheduling**:
  - Long-press and drag task cards vertically along time slots to reschedule start times dynamically.
- **Schedule Discrepancy & Conflict Resolver**:
  - Automatically detects overlapping task time slots.
  - Warning banner with **Auto-Resolve** button to recalculate time buffers and shift subsequent tasks forward.
- **Profile & Settings**:
  - **Account**: User profile name, avatar, email, and subscription tier.
  - **Activity Progress**: Daily completion progress bar, task metrics, streak count, and productivity hours.
  - **Notifications**: Reminders toggle, notification sound, quiet hours, and lead time settings.
  - **Google Calendar & Outlook Sync**: Account connections, auto-sync toggles, manual sync triggers, and live sync status indicators.
  - **About**: App version `1.0.0 (Build 102)` and app feature overview.
- **Camera OCR Schedule Scanner**:
  - CameraX live preview with scanning reticle overlay.
  - ML Kit Text Recognition pipeline extracting time slots and titles from photos, flyers, or timetables.
  - Batch import candidate tasks directly into the calendar.
- **Responsive Layout for Phones & Tablets**:
  - Bottom Navigation Bar on compact phone screens.
  - Side Navigation Rail with optional side-by-side split view on tablet/foldable screens.

---

## 🛠 Tech Stack & Libraries

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **Architecture**: MVVM with `StateFlow` and Repository pattern
- **Camera & Machine Learning**:
  - CameraX (`androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`)
  - Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`)
- **Navigation & Layout**:
  - Navigation Compose (`androidx.navigation:navigation-compose`)
  - Material3 Window Size Class for responsive tablet/phone support
- **Build System**: Gradle with Kotlin DSL and Version Catalog (`libs.versions.toml`)

---

## 🚀 Getting Started

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/SICK-dawg-2003/Time-Scheduling-App-UniSync.git
   ```
2. **Open in Android Studio**:
   - Open Android Studio Ladybug (or newer).
   - Select **Open an existing project** and select the cloned directory.
3. **Build & Run**:
   - Select your connected Android device or emulator.
   - Click **Run `app`** (`Shift + F10`).

---

## 📄 License

Distributed under the MIT License.
