# HUR Source Switcher

Приложение для магнитолы Coagent V8AUTO-MX6Q (Android 4.4.2), которое добавляет источник APP (Head Unit Revived) в цикл переключения SOURCE на руле.

## Проблема

Магнитола V8AUTO имеет кнопку SOURCE/MODE на руле, которая переключает между источниками звука: TUNER (радио), USB, BTAUDIO, F_AUX. Приложение HUR (Android Auto) не входит в этот цикл, и после переключения на другой источник вернуться на HUR кнопкой руля невозможно.

## Решение

Приложение `com.hur.sourceswitcher` слушает broadcast `com.coagent.intent.action.SOURCE_CHANGED` и перехватывает два перехода:

1. **F_AUX → TUNER**: вместо TUNER переключает на APP и запускает HUR
2. **APP → F_AUX**: вместо F_AUX переключает на TUNER (завершает цикл)

### Результат — цикл SOURCE на руле:
```
TUNER → [USB если подключён] → [BTAUDIO если BT] → F_AUX → APP (HUR) → TUNER → ...
```

## Как работает звук через HUR

Магнитола использует MCU (микроконтроллер) для маршрутизации аудио. Чтобы звук из Android-приложений шёл на динамики, нужно переключить MCU source на APP:

```bash
# Переключить source на APP (звук из Android → динамики)
adb shell "service call coagent.source 1 s16 APP i32 0"

# Unmute MCU
adb shell "service call coagent.settings 11 i32 0"
```

Приложение делает это автоматически при переключении на APP.

## Важные особенности

### Mute при переключении
При переключении source приложение сначала глушит звук (mute), переключает source, затем включает обратно (unmute). Это предотвращает наложение двух источников звука (радио + APP), которое вызывает хрипы.

### Cooldown 3 секунды
После каждого перехвата есть пауза 3 секунды, чтобы предотвратить зацикливание (наше переключение тоже генерирует broadcast).

### Активация после установки
На Android 4.4 приложение в состоянии "stopped" (только установленное, не запускавшееся) не получает broadcast. После установки APK нужно один раз запустить MainActivity:
```bash
adb shell "am start -n com.hur.sourceswitcher/.MainActivity"
```

## Установка

```bash
# Подключиться к магнитоле по WiFi ADB
adb connect <IP магнитолы>:5555

# Установить APK
adb install hur-source-switcher.apk

# Запустить один раз для активации
adb shell "am start -n com.hur.sourceswitcher/.MainActivity"
```

## Сборка из исходников (Termux)

Необходимые пакеты: `ecj`, `dx`, `aapt2`, `apksigner`

```bash
# 1. Скачать framework-res.apk с магнитолы (нужен для aapt2)
adb pull /system/framework/framework-res.apk

# 2. Скачать framework.jar и конвертировать в jar с .class файлами
adb pull /system/framework/framework.jar
unzip framework.jar classes.dex
d2j-dex2jar classes.dex -o framework-classes.jar

# 3. Компиляция
ecj -source 1.7 -target 1.7 -classpath framework-classes.jar -d build/classes src/*.java

# 4. Конвертация в DEX
dx --dex --output=build/classes.dex build/classes/

# 5. Сборка APK
aapt2 link --manifest AndroidManifest.xml -o build/base.apk -I framework-res.apk \
  --min-sdk-version 19 --target-sdk-version 19 --version-code 1 --version-name 1.0
cp build/base.apk build/app.apk
zip -j build/app.apk build/classes.dex

# 6. Подпись
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=US"
zipalign -f 4 build/app.apk build/app-aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey build/app-aligned.apk
```

## Управление MCU — справочник команд

```bash
# Source
service call coagent.source 1 s16 APP i32 0    # переключить на APP
service call coagent.source 1 s16 TUNER i32 0  # переключить на радио
service call coagent.source 3                    # получить текущий source

# Звук
service call coagent.settings 11 i32 0   # unmute
service call coagent.settings 11 i32 1   # mute
service call coagent.settings 13         # get mute status
service call coagent.settings 6          # volume up
service call coagent.settings 7          # volume down
service call coagent.settings 22         # get volume

# Диагностика
dumpsys media.audio_flinger | grep -A5 "active tracks"
dumpsys audio
dumpsys media.audio_policy
```

## Известные проблемы

1. **Мелькание AUX при APP→TUNER**: При переключении с APP на следующий source (TUNER) на долю секунды мелькает иконка AUX. Это ограничение broadcast-подхода — система сначала переключает на F_AUX (дефолт), затем наш receiver перехватывает и переключает на TUNER.

2. **Хрип после многократных переключений**: Если быстро переключать source много раз, MCU может войти в сбойное состояние и звук начнёт хрипеть. Лечится полным выключением зажигания на 30 секунд (обычный reboot Android не сбрасывает MCU).

3. **HUR audio sink качество**: HUR выдаёт аудио на 48000 Hz, а микшер магнитолы работает на 44100 Hz. Ресемплинг на слабом i.MX6 может вызывать артефакты.

## Source IDs магнитолы (SourceConstantsDef.SourceID)

| Source | Byte | Описание |
|--------|------|----------|
| TUNER | 0 | FM/AM радио |
| USB | 10 | USB-флешка |
| F_AUX | 12 | Передний AUX |
| APP | 17 | Android-приложения (DAC) |
| AVOFF | 18 | Экран выключен |
| BTAUDIO | 25 | Bluetooth A2DP |

## Файлы

- `hur-source-switcher.apk` — готовый подписанный APK
- `src/AndroidManifest.xml` — манифест
- `src/MainActivity.java` — активити для активации приложения
- `src/SourceReceiver.java` — BroadcastReceiver, перехватывает SOURCE_CHANGED
- `src/SourceService.java` — сервис, переключает source через shell
