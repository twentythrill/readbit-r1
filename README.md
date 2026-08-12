<div align="center">

<img src="app/src/main/res/drawable-nodpi/readbit_logo.png" alt="Readbit Logo" width="128" height="128" />

# Readbit R1

**High-Performance Offline Speed Reader (RSVP) for Rabbit R1 & Custom Android Devices**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-purple.svg)](https://kotlinlang.org)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-orange.svg)](https://developer.android.com/about/versions/15)

</div>

> [!CAUTION]
> **IMPORTANT DISCLAIMER & HARDWARE REQUIREMENT**
> In order to install and run Readbit R1 on a Rabbit R1, your device **must be rooted**. 
> 
> The author does **NOT** recommend rooting your Rabbit R1 device and assumes **NO responsibility or liability** for any broken, bricked, or non-functional devices, data loss, or voided warranties. Proceed entirely at your own risk!

---

### 🎬 Video Demo

<div align="center">
  <video src="https://raw.githubusercontent.com/twentythrill/readbit-r1/main/video-demo.mp4" controls="controls" muted="muted" style="max-width: 100%; height: auto; border-radius: 10px;"></video>
  
  <p><i>If the inline video player does not load in your browser, click below to play or download:</i><br>
  <b><a href="https://raw.githubusercontent.com/twentythrill/readbit-r1/main/video-demo.mp4">▶️ Watch / Download Demo Video (video-demo.mp4)</a></b></p>
</div>

---

## 📖 Overview & Core Features

**Readbit R1** is an open-source, ultra-fast, offline RSVP (Rapid Serial Visual Presentation) eBook and document reader engineered specifically for compact Android hardware, including the **Rabbit R1**.

By displaying words sequentially at a fixed focal point and calculating the **Optimal Recognition Point (ORP)** for every word, Readbit enables comfortable reading speeds from **100 to 1000+ Words Per Minute (WPM)** without eye strain or scanning fatigue.

### 🌟 Key Highlights

- ⚡ **Dynamic ORP Focus Alignment**: Automatically calculates the focal center of gravity for each word and highlights the pivot character in high-contrast accent red.
- ⏱️ **Intelligent Pacing**: Automatically pauses at punctuation (periods, question marks, exclamation marks) and smoothly ramps speed when starting/resuming.
- 📱 **Physical Hardware Navigation**: Full support for physical volume keys (**Volume Up**: Play/Pause; **Volume Down**: Pause & step backward/forward).
- 📑 **Instant Text Preview**: Double-tap anywhere in RSVP mode to seamlessly switch back to a scrollable document preview.
- 🔍 **In-Book Search & Bookmarks**: Full-text phrase search and one-tap word bookmarking with persistent storage.
- 📚 **Multi-Format Support**: Reads EPUBs (`.epub`), PDFs (`.pdf`), Plain Text (`.txt`), Markdown (`.md`), and HTML/XML web pages (`.html`, `.xml`).
- 🔒 **100% Offline & Private**: Zero network permissions, zero telemetry, and zero tracking.

---

## 📄 Supported Document Formats

| Format | File Extensions | Notes / Requirements |
| :--- | :--- | :--- |
| **EPUB Books** | `.epub` | Parsed natively via HTML/XML zip extraction. |
| **PDF Documents** | `.pdf` | Native text extraction *(requires Android 15 / API 35+)*. |
| **Plain Text** | `.txt` | Auto-detects UTF-8, UTF-16, and ISO-8859-1 encodings. |
| **Markdown** | `.md`, `.markdown` | Tokenized into clean reading blocks. |
| **Web Pages** | `.html`, `.htm`, `.xhtml`, `.xml` | HTML tags automatically stripped to plain text. |

---

## 🎮 Hardware Controls & Gestures

Designed specifically for single-handed hardware interaction on the Rabbit R1:

| Trigger | Context | Action |
| :--- | :--- | :--- |
| **Volume Up** | RSVP Mode | **Play / Pause** reading playback. |
| **Volume Up** | Text Preview | Step forward to **Next Line / Chunk**. |
| **Volume Down** | RSVP Mode | **Pause / Step Backward** 1 word. |
| **Volume Down** | Text Preview | Step backward to **Previous Line / Chunk**. |
| **Double Tap** | RSVP Mode | Exit RSVP screen & return to **Full Text Preview**. |

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin 2.0
- **Build Tool**: Gradle 8.10 (Kotlin DSL)
- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35 (Android 15)
- **Device Requirement**: Rooted Rabbit R1 or compatible Android device.
- **Architecture**: Single-Activity, Custom Canvas Views (`OrpWordView`), RecyclerView Chunk Adapter (`PreviewAdapter`), Asynchronous Background Processing (`Executors`).

---

## 📁 Project Structure

```text
readbit-r1/
├── video-demo.mp4                       # Project Demonstration Video
├── app/
│   ├── src/main/
│   │   ├── java/com/readbit/r1/
│   │   │   ├── MainActivity.kt           # Main UI controller & lifecycle coordinator
│   │   │   ├── OrpLayout.kt              # Optimal Recognition Point algorithm engine
│   │   │   ├── OrpWordView.kt            # Custom Canvas view for focused RSVP rendering
│   │   │   ├── PreviewDocumentView.kt    # Full document chunk preview renderer
│   │   │   ├── PreviewBlockView.kt       # Interactive block text preview view
│   │   │   ├── PreviewLineMetrics.kt     # Layout and font density metric utilities
│   │   │   └── PreviewLineTextFactory.kt # Text span and StaticLayout factory
│   │   ├── res/                          # Vector drawables, themes, and Space Grotesk font
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── .gitignore
├── LICENSE                               # Apache License 2.0
└── README.md                             # Repository Documentation
```

---

## 🚀 Building & Installation

### Prerequisites
- JDK 17 or higher.
- Android SDK with API level 35 installed.
- Rooted Rabbit R1 device with ADB enabled.

### 1. Clone the Repository
```bash
git clone https://github.com/twentythrill/readbit-r1.git
cd readbit-r1
```

### 2. Build Debug APK
```bash
./gradlew assembleDebug
```
*The compiled APK will be output to: `app/build/outputs/apk/debug/app-debug.apk`*

### 3. Install on Device via ADB
Connect your rooted Rabbit R1 via USB with ADB enabled:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 License & Attributions

- **Source Code**: Released under the **[Apache License 2.0](LICENSE)**.
- **Typography**: Features the **Space Grotesk** font family by Florian Karsten, licensed under the **[SIL Open Font License 1.1](https://scripts.sil.org/OFL)**.
- **Dependencies**: Built with official Google `androidx` and `material` components under Apache 2.0.

> [!IMPORTANT]
> **Trademark Disclaimer**: *Rabbit R1 and Rabbit are trademarks of Rabbit Inc. Readbit R1 is an independent open-source software project developed by the community and is not affiliated with, endorsed by, or sponsored by Rabbit Inc.*
