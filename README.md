# 📱 Android FTP & WebDAV Server

A powerful Android application that turns your phone into a file server, allowing seamless file transfer and direct file access from your PC over WiFi.

![Android](https://img.shields.io/badge/Android-7.0+-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## ✨ Features

- **Dual Protocol Support**
  - 📁 **FTP Server** (Port 2121) - Fast file transfers
  - 🌐 **WebDAV Server** (Port 8080) - Open files directly from Windows

- **Easy Connection**
  - 📷 QR Code for quick URL scanning
  - 📋 Tap to copy server URLs
  - 🔔 Persistent notification with server status

- **Flexible Storage Access**
  - 📂 Access internal storage or SD card
  - 🔄 Switch root directory on the fly
  - 📊 Full read/write support

- **User-Friendly**
  - 🌙 Modern dark theme UI
  - ⚙️ Configurable ports
  - 🔐 Optional password protection

## 📸 Screenshots

*Screenshots coming soon*

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 34

### Building the App

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/ftp-server-android.git
   cd ftp-server-android
   ```

2. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on your device**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Running the App

1. Open the app on your Android device
2. Grant storage permissions when prompted
3. Tap **Start Server**
4. Connect from your PC using the displayed URLs

## 💻 Connecting from PC

### Method 1: WebDAV (Recommended for opening files)

**Map as Network Drive in Windows:**
1. Open File Explorer
2. Right-click "This PC" → "Map network drive"
3. Enter: `http://YOUR_PHONE_IP:8080`
4. Click Finish

Now your phone appears as a local drive! Double-click files to open them directly.

### Method 2: FTP (For file transfers)

**Windows Explorer:**
1. Open File Explorer
2. Type in address bar: `ftp://YOUR_PHONE_IP:2121`
3. Press Enter

**FileZilla/WinSCP:**
- Host: `YOUR_PHONE_IP`
- Port: `2121`
- Protocol: FTP
- Username: `anonymous` (or your configured username)

### Method 3: Web Browser

Open in any browser: `http://YOUR_PHONE_IP:8080`

## ⚙️ Configuration

Access settings by tapping the gear icon:

| Setting | Default | Description |
|---------|---------|-------------|
| FTP Port | 2121 | Port for FTP connections |
| WebDAV Port | 8080 | Port for WebDAV connections |
| Root Directory | Internal Storage | Folder to share |
| Anonymous Access | Enabled | Allow access without password |
| Username | user | Username for authentication |
| Password | - | Password for authentication |

## 🏗️ Project Structure

```
app/
├── src/main/
│   ├── java/com/ftpserver/app/
│   │   ├── MainActivity.kt          # Main UI
│   │   ├── SettingsActivity.kt       # Settings screen
│   │   ├── FtpServerService.kt       # Background service
│   │   ├── ftp/
│   │   │   ├── AndroidFileSystemFactory.kt
│   │   │   ├── AndroidFileSystemView.kt
│   │   │   └── AndroidFtpFile.kt
│   │   ├── webdav/
│   │   │   └── WebDavServer.kt       # WebDAV implementation
│   │   └── utils/
│   │       ├── NetworkUtils.kt
│   │       ├── QrCodeUtils.kt
│   │       └── StorageUtils.kt
│   ├── res/
│   │   ├── layout/                   # UI layouts
│   │   ├── values/                   # Strings, colors, themes
│   │   ├── drawable/                 # Icons and shapes
│   │   └── xml/                      # Preferences, network config
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## 📦 Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Apache FtpServer | 1.2.0 | FTP protocol implementation |
| Apache MINA | 2.2.3 | Network I/O framework |
| NanoHTTPD | 2.3.1 | WebDAV server |
| ZXing | 3.5.2 | QR code generation |
| Material Design 3 | 1.11.0 | UI components |

## 🔒 Permissions

The app requires the following permissions:

- `INTERNET` - Network communication
- `ACCESS_WIFI_STATE` - Detect WiFi connection
- `ACCESS_NETWORK_STATE` - Check network status
- `FOREGROUND_SERVICE` - Run server in background
- `READ/WRITE_EXTERNAL_STORAGE` - Access files
- `MANAGE_EXTERNAL_STORAGE` - Full storage access (Android 11+)
- `POST_NOTIFICATIONS` - Show server status (Android 13+)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Apache FtpServer](https://mina.apache.org/ftpserver-project/) - FTP implementation
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) - Lightweight HTTP server
- [ZXing](https://github.com/zxing/zxing) - QR code library

## 📧 Contact

For questions or support, please open an issue on GitHub.

---

Made with ❤️ for seamless file sharing
