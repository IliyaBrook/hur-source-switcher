# TODO — HUR Source Switcher

## Нерешённые проблемы

### 1. Кнопки NEXT/PRE — нестабильная работа
**Статус:** Частично работает, требует доработки

**Как реализовано сейчас:**
- `KeyReceiver.java` слушает broadcast `com.coagent.intent.action.KEY_CHANGED` от MCU
- Проблема: MCU отправляет broadcast через `sendBroadcastAsUser(intent, UserHandle.ALL)`, который не всегда доходит до стороннего приложения на Android 4.4
- При нажатии NEXT/PRE записывается keycode в файл `/data/local/tmp/keyrelay_cmd`
- Фоновый shell-скрипт `keyrelay` (запускается от root) читает файл и выполняет `input keyevent <code>` от имени root (обычное приложение не может inject events — нужен `INJECT_EVENTS` permission)
- KEYCODE_MEDIA_NEXT = 87, KEYCODE_MEDIA_PREVIOUS = 88

**Известные проблемы:**
- После переключения SOURCE туда-обратно кнопки иногда перестают работать
- Broadcast `KEY_CHANGED` от MCU не всегда доставляется нашему receiver (sendBroadcastAsUser ограничение)
- `keyrelay` daemon должен быть запущен (стартует при BOOT_COMPLETED и при открытии MainActivity), но может умереть
- Polling файла с `sleep 1` добавляет задержку до 1 секунды

**Что можно попробовать:**
- Заменить broadcast receiver на мониторинг logcat (KeyMonitorService.java уже есть, но не тестировался полноценно) — logcat ловит все нажатия надёжно
- Уменьшить sleep в keyrelay до 0.2-0.3 сек для меньшей задержки
- Проверить, не убивает ли система keyrelay daemon
- Рассмотреть использование `inotifywait` вместо polling

### 2. Кнопка VR (голосовой ассистент) — не работает
**Статус:** Невозможно перехватить текущим методом

**Проблема:**
- Кнопка VR на руле обрабатывается MCU **аппаратно** как mute/unmute toggle
- MCU не отправляет broadcast `KEY_CHANGED` для VR — Android её вообще не видит
- Нет возможности перехватить через broadcast receiver или logcat

**Возможные решения:**
- Перехват через UART-протокол (нужно реверсить протокол MCU ↔ Android)
- Патч прошивки MCU (рискованно)
- Использовать другую физическую кнопку на руле (если есть свободная)
- Смириться и вызывать ассистент через экран Android Auto

### 3. Телефонные звонки — звук не идёт через динамики
**Статус:** Ограничение архитектуры Android Auto

**Проблема:**
- Android Auto требует Bluetooth для аудио звонков, даже при USB-подключении
- HUR issue #409 (OPEN) — разработчик знает о проблеме

**Возможные решения:**
- Ждать фикса от разработчика HUR
- Использовать Bluetooth HFP параллельно с USB для звонков

### 4. Мелькание AUX при переключении APP → TUNER
**Статус:** Косметический баг, ограничение broadcast-подхода

**Проблема:**
- При переключении с APP на следующий source на долю секунды мелькает иконка AUX
- Причина: система сначала переключает на F_AUX (дефолт для неизвестного source), потом наш receiver перехватывает и переключает на TUNER

**Возможные решения:**
- Патч CoagentSettings.apk — добавить APP в `SRCSourceManager.getConnectedDevice()` напрямую (правильный baksmali/smali, не dex2jar)
- Xposed/LSPosed хук (но нужен Xposed Framework для Android 4.4)

## Технические заметки

### Keyrelay daemon
```bash
# Запуск вручную (от root через ADB):
echo > /data/local/tmp/keyrelay_cmd && chmod 666 /data/local/tmp/keyrelay_cmd
nohup sh -c 'while true; do line=$(cat /data/local/tmp/keyrelay_cmd 2>/dev/null); case "$line" in [0-9]*) echo > /data/local/tmp/keyrelay_cmd; input keyevent $line 2>/dev/null;; esac; sleep 1; done' > /dev/null 2>&1 &
```

### Почему нужен keyrelay?
- `input keyevent` требует root или `INJECT_EVENTS` permission
- Наше приложение запускается от обычного пользователя (u0_a37)
- Установка в `/system/app/` не даёт `INJECT_EVENTS` без подписи платформенным ключом
- Keyrelay запускается от root (через BOOT_COMPLETED broadcast → shell exec) и читает команды из файла

### MCU source IDs
| Source | Byte | Описание |
|--------|------|----------|
| TUNER | 0 | FM/AM радио |
| USB | 10 | USB-флешка |
| F_AUX | 12 | Передний AUX |
| APP | 17 | Android-приложения |
| BTAUDIO | 25 | Bluetooth A2DP |

### Полезные ADB-команды
```bash
service call coagent.source 1 s16 APP i32 0   # переключить на APP
service call coagent.source 3                   # текущий source
service call coagent.settings 11 i32 0          # unmute
service call coagent.settings 11 i32 1          # mute
input keyevent 87                               # MEDIA_NEXT
input keyevent 88                               # MEDIA_PREVIOUS
```
