# Samsung Universal Remote

Android-Fernbedienung für Samsung Smart TVs (Tizen) mit lokaler WLAN/LAN-Steuerung und Wake-on-LAN.

## Aktueller Stand

- Samsung TV per IP verbinden / koppeln
- Pairing-Token lokal speichern
- Navigation, Home, Zurück, Menü, Quelle
- Lautstärke, Mute und Sender
- Ziffern- und Media-Tasten
- TV-Suche im lokalen Netzwerk via SSDP
- Einschalten per Wake-on-LAN (MAC-Adresse erforderlich)
- Android 17 / API 37 Local-Network-Berechtigung

## APK-Build

GitHub Actions baut bei Änderungen auf `main` automatisch eine installierbare Debug-APK. Der Build verwendet JDK 17, Gradle 9.5.0 und das Android-17-SDK.

Die APK wird als Actions-Artefakt `SamsungRemote-Android` bereitgestellt.
