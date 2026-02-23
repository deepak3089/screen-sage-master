# 📱 ScreenSage – AI Screen Assistant for Android

ScreenSage is an Android application that uses **Accessibility Services and AI** to monitor on-screen content and provide intelligent overlays, insights, and chat-based assistance.  
It acts as a smart on-screen assistant that understands user context and enhances productivity.

---

## 🚀 Features

-  **AI-powered assistant overlay**
-  **Real-time screen content detection using Accessibility Service**
-  **Chat history storage & conversation manager**
-  **Local AI model manager**
-  **User preferences & settings management**
-  **Floating overlay UI (chat bubble style)**
-  **Permission handling for accessibility & overlays**
-  **Background service auto-restart worker**

---

## 🛠️ Tech Stack

- **Language:** Kotlin  
- **Platform:** Android (Jetpack Compose)  
- **Architecture:** MVVM / Repository Pattern  
- **AI Integration:** Local AI Client & Repository Layer  
- **Storage:** SharedPreferences / Local Storage  
- **Android Services:** Accessibility Service & Overlay Service  
- **Build System:** Gradle (Kotlin DSL)

---

## 📂 Project Structure

app/src/main/java/com/example/screensage/
│
├── accessibility/ # Accessibility Service logic
├── ai/ # AI client & model manager
├── models/ # Data models
├── preferences/ # User preferences manager
├── service/ # Overlay & background services
├── storage/ # Chat history storage
├── ui/ # UI theme & components
└── utils/ # Permission & helper utilities


---

## 🧩 How It Works

1. User enables **Accessibility Permission**
2. App monitors screen content via Accessibility events  
3. AI processes screen context  
4. Overlay chat assistant provides real-time suggestions  

> Accessibility services run in the background and receive UI events such as screen changes and user interactions. :contentReference[oaicite:0]{index=0}

---

## ⚙️ Installation

### 1️⃣ Clone the Repository
```bash
git clone https://github.com/deepak3089/screen-sage-master.git
Open in Android Studio

Open the project

Sync Gradle

3️⃣ Enable Permissions

Go to:
Settings → Accessibility → ScreenSage
Enable Overlay permission when prompted.

Run the App -----

Use Android Studio Emulator or Physical Device

Click Run ▶️

Grant required permissions


