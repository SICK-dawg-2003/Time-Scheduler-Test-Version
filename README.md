# Time Scheduling App (UniSync)

An interactive, color-coded Time Management and Daily Scheduler Android application, supporting **drag-and-drop scheduling**, **automatic conflict discrepancy resolution**, **AI Camera & Document OCR Schedule Scanning**, and **Intents/Notifications**.

---

## Key Features / Implementations

- **Color-Coded Calendar & Daily Timeline**:
  - **Color-coded Categories**: **Work** (Blue), **Personal** (Green), **Health** (Pink), **Study** (Purple), **Urgent** (Orange), and **Other** (Teal).
  - **12-Hour AM/PM Timeline**: Vertical hour grid (`12:00 AM` through `11:00 PM`) with formatted time strings, task completion toggles, and sync badges.
  - **Date Strip & Category Filter**: Interactive week day selector strip and category filter pills.
- **Drag-and-Drop Scheduling**:
  - Long-press and drag task cards vertically along time slots to reschedule start times dynamically.
- **Schedule Discrepancy & Conflict Resolver**:
  - Automatically detects overlapping task time slots.
  - Warning banner with an **Auto-Resolve** button to recalculate time buffers and shift subsequent tasks forward.
- **AI Camera OCR Schedule Scanner & ML Kit Document Scanner**:
  - **Google ML Kit Document Scanner API**: Full-screen pre-built UI (`Doc Scanner AI`) featuring automatic edge detection, 3D perspective correction, shadow removal, and contrast enhancement.
  - **High-Precision CameraX Shutter Capture**: Live camera preview with reticle overlay and camera shutter button.
  - **Gallery Image Upload**: Import schedule images and screenshots directly from device storage (ideal for Android Studio emulator testing).
  - **Time-Anchor Spatial Table Parser**: Spatial hierarchy parsing (`TextBlock` ➡️ `Line` ➡️ `Element`) that maps 3-column schedule tables (Time, Activity Title Name, Notes/Description) into structured tasks.
  - **Chronological AM/PM Progression & OCR Sanitization**: Automatically infers PM hours for afternoon/evening times (`12:00 - 1:00` ➡️ `12:00 PM – 1:00 PM`) and cleans OCR digit/colon typos.
  - **Scanned Candidate Task Preview & Editing**: Inspect and edit recognized Task Title Names, Categories, or Notes/Descriptions via `TaskDialog` before batch-importing into the calendar.
- **Intents & User Communication**:
  - **Implicit Intent**: Share daily schedule summaries with external apps (email, messaging, notes) using `Intent.ACTION_SEND`.
  - **Toast Notifications**: Real-time non-intrusive feedback when creating, editing, scanning, or sharing tasks.
  - **Push Notifications & Service**: Task reminder notifications built with `NotificationCompat`, Notification Channels, and a background `ScheduleReminderService`.
- **Responsive Layout for Phones & Tablets**:
  - **Compact Phone Layout**: Bottom Navigation Bar.
  - **Expanded Tablet/Foldable Layout**: Side Navigation Rail.

---

## Libraries & Technologies

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material3)
- **Architecture**: MVVM with `StateFlow` and Repository pattern
- **Camera & Machine Learning**:
  - CameraX (`androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`)
  - Google ML Kit Text Recognition v2 (`com.google.mlkit:text-recognition`)
  - Google ML Kit Document Scanner API (`com.google.android.gms:play-services-mlkit-document-scanner`)
- **System Services & Notifications**:
  - `NotificationManager`, `NotificationCompat`, `ScheduleReminderService`
  - System Permissions: `CAMERA`, `POST_NOTIFICATIONS`
- **Navigation & Layout**:
  - Navigation Compose (`androidx.navigation:navigation-compose`)
  - Material3 Window Size Class for responsive tablet/phone support
- **Build System**: Gradle with Kotlin DSL and Version Catalog (`libs.versions.toml`)

---

## Getting Started

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/SICK-dawg-2003/Time-Scheduler-Test-Version.git
   ```
2. **Open in Android Studio**:
   - Open Android Studio Ladybug (or newer).
   - Select **Open an existing project** and select the cloned directory.
3. **Build & Run**:
   - Select your connected Android device or emulator.
   - Click **Run `app`** (`Shift + F10`).
