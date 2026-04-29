# AGENTS.md — Operating Guide for AI Agents (Codex CLI, etc.)

> Репозиторий: **Three‑Part Timer for Wear OS (Galaxy Watch 5)**
> Цель файла: дать ИИ‑агентам (Codex CLI и др.) **точные, проверяемые инструкции** по работе с кодовой базой: область задач, ограничения, команды, стиль, критерии приёмки, правила верификации и источники best practices.

---

## 0) TL;DR для агентов

* Что строим: **трёхсекционный таймер** (*Intro → Main → Outro*) с хаптикой на рубежах, **Tile с кнопкой быстрого старта таймера** (свайп вправо), **Foreground Service** для надёжности.
* Основные поверхности: Tile (быстрый старт + статус, переход в активити), уведомление (Pause/Resume/Skip/Stop), Activity (пресеты, таймер).
* Платформа: **Wear OS 5 / Android 14 (API 34)**; minSdk (Wear) ≥ **30** (поддержка Wear OS 3+).
* Запреты: нет зависимости от телефона, сети, аккаунтов, рекламы, телеметрии по умолчанию.
* Качество: надёжность > анимации; вибро > звук; простота > обилие фич.

---

## 1) Область задач и запреты

### 1.1 В scope

* Standalone‑приложение под часы: **пресеты**, **трёхсекционный таймер**, **Tile с кнопкой быстрого старта пресета по умолчанию** (и переходом в Activity), **хаптика на границах**, **пауза/скип/стоп**, **восстановление состояния** после перезапуска/краша/ребута.
* Tile отображает пресет по умолчанию: имя, суммарное время и разбивку Intro/Main/Outro, плюс быструю кнопку старта таймера (открывает активити); обновляется при изменениях состояния через **TileUpdateRequester**.

### 1.2 Out of scope (не делаем)

* Сеть, аккаунты, синхронизация, companion phone, push, покупки, реклама.
* Вечные wakelock’и, фоновая работа без foreground‑уведомления.

---

## 2) Архитектура (высокоуровнево)

```
app/
 ├─ data/           # DataStore (пресеты, последнее состояние)
 ├─ domain/         # Движок таймера, модели, машина состояний
 ├─ service/        # ForegroundService, AlarmManager (если используем)
 ├─ tile/           # TileService + ProtoLayout (Dynamic Time)
 ├─ ui/             # Compose for Wear (экраны: список, редактор, таймер)
 ├─ util/           # вибро‑хелперы, time utils, логирование
 └─ tests/          # unit + instrumentation
```

* Движок: общий обратный отсчёт + **checkpoints** на трёх границах.
* Надёжность: работа внутри **ForegroundService**; границы — через exact‑alarms **или** коррекцию по `elapsedRealtime()`.
* Tile: имя пресета, фаза (I/M/O), кольцевой прогресс; апдейты → TileUpdateRequester.
  Обновления коалесцируются: только на boundary/pause/resume/stop (не каждую секунду).
* Уведомление: постоянно, действия Pause/Resume/Skip/Stop.
* Состояние: `DataStore` хранит `TimerState` + выбранный пресет для восстановления.

---

## 3) Доменная модель

```kotlin
data class Preset(
  val id: String,
  val title: String,
  val introSec: Int,
  val mainSec: Int,
  val outroSec: Int,
  val allowSkip: Boolean = true,
  val soundEnabled: Boolean = false
)

enum class Segment { INTRO, MAIN, OUTRO, DONE }
enum class RunStatus { IDLE, RUNNING, PAUSED, DONE }

data class TimerState(
  val status: RunStatus,
  val segment: Segment,
  val remainingInSegmentSec: Int,
  val elapsedTotalSec: Int,
  val totalSec: Int,
  val startedAtElapsedRealtime: Long?
)
```

**Инварианты:** `total = intro + main + outro`; при 0‑длительности фаза мгновенно пропускается с короткой хаптикой.

---

## 4) UX‑принципы (обязательно)

1. Хаптика **включена по умолчанию**, звук — **выключен** (уважение к DND/зал).
2. На Tile одна кнопка «Start Timer» (или локализованный эквивалент) — запускает таймер пресета по умолчанию и сразу открывает Activity; если таймер уже идёт, кнопка меняет текст и ведёт в Activity без повторного старта.
3. На экране таймера крупно показываем **остаток текущей секции** + явная метка I/M/O.
4. Debounce/идемпотентность на Start/Resume; защита от двойного старта.
5. 0‑секции — мгновенный пропуск, короткий сигнал.
6. Всегда держим ambient‑версию экрана: чёрный фон, статическое содержимое, доступное сразу после пробуждения.

---

## 5) Платформа и зависимости

* target/compile: **API 34**; minSdk (wear): **30**.
* UI: **Jetpack Compose for Wear**; Tiles: **androidx.wear.tiles (ProtoLayout)**.
* Foreground‑канал нотификаций: `"timer"`.
* Разрешение `SCHEDULE_EXACT_ALARM` — объявлено; используется для T−10s обратного отсчёта через `AlarmManager.setExactAndAllowWhileIdle()` (fallback: `setExact` + тик-движок при отсутствии доступа, см. §6 и TIMING_STRATEGY_EVALUATION).
* На Wear OS 4+ требуется runtime‑разрешение `POST_NOTIFICATIONS` для отображения Ongoing Activity‑чипа; запрашиваем его из `MainActivity`, сервис обязан деградировать без него.

---

## 6) **Политика фактов и best practices** (обязательно к исполнению)

1. **Верификация перед выбором техники.** Если есть варианты имплементации (напр. exact‑alarms vs. тики + `elapsedRealtime()`):

   * собрать **минимальный тест** на эмуляторе (и по возможности на устройстве),
   * измерить точность рубежей, энергопрофиль, поведение в Doze,
   * зафиксировать результаты в описании PR (краткая таблица/пункты).
2. **Поиск best practices в интернете:**

   * при ошибках, выборе библиотек, архитектурных приёмах — **обязательно** просмотреть **официальную документацию** (Android/Wear, Jetpack), релизы библиотек, issue‑трекеры, образцы кода из официальных репозиториев;
   * дополнительно — статьи Google Developers/Medium/Stack Overflow **с критической проверкой дат** (не старше 18 месяцев, если технология быстро меняется);
   * в PR приложить 3–5 ссылок на источники, кратко резюмировать, почему выбран подход.
3. **Побеждает официальный источник.** При конфликте — приоритет: официальные доки/исходники > статьи/блоги > ответы на форумах.
4. **Повторяемость.** Любое утверждение о поведении системы подкрепляется шагами воспроизведения (adb‑команды/настройки эмулятора).

---

## 7) Сборка, запуск, тесты (команды)

```bash
# build & install
yarn -s >/dev/null 2>&1 || true  # (если есть фронт-инструменты; иначе игнор)
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# unit-тесты движка
./gradlew :app:testDebug

# instrumentation (smoke на Wear AVD)
./gradlew :app:connectedDebugAndroidTest

# формат/линт (выбрать, что настроено в проекте)
./gradlew spotlessApply detekt
# или
./gradlew ktlintFormat ktlintCheck
```

**AVD:** Wear OS Large Round, **API 34 / Android 14**, Services = **Google Play Store**, образ: *Wear OS 5 ARM64 v8a*.
**Реальные часы:** Developer Options → ADB over Wi‑Fi → `adb connect <ip>:5555`.

### 7.1 Рабочий рецепт подключения Galaxy Watch по Wireless Debugging

Если `adb pair <ip>:<pairing_port> <code>` на стандартном ADB server (`5037`) падает с
`error: protocol fault (couldn't read status message): Undefined error: 0`, но `ping <ip>` и
`nc -vz <ip> <pairing_port>` проходят, используйте отдельный clean ADB server на другом порту.
Это сработало для Galaxy Watch 5 (`SM_R920`) 2026-04-29.

На часах:

* Developer options → включить `ADB debugging`, `Wireless debugging`.
* Включить `Turn off automatic Wi‑Fi`, чтобы часы не сбрасывали Wi‑Fi во время pairing.
* Открыть `Wireless debugging` → `Pair new device` и держать экран pairing открытым.
* Записать **pairing** `ip:port` и 6-значный код. Pairing-port и connection-port разные.

На Mac:

```bash
# Terminal 1: отдельный ADB server, не конфликтующий с 5037
adb -P 5038 nodaemon server

# Terminal 2: pairing через этот же server
adb -P 5038 pair <watch_ip>:<pairing_port> <pairing_code>

# Проверить, какой connection-port объявили часы
adb -P 5038 mdns services
adb -P 5038 devices -l

# Установить уже собранный debug APK
adb -P 5038 install -r app/build/outputs/apk/debug/app-debug.apk
```

Проверенный успешный пример:

```text
adb -P 5038 pair 10.0.0.16:40283 751664
Successfully paired to 10.0.0.16:40283 [guid=adb-RFAT629H4WY-wREsuI]

adb -P 5038 mdns services
adb-RFAT629H4WY-wREsuI  _adb-tls-connect._tcp  10.0.0.16:40415

adb -P 5038 devices -l
adb-RFAT629H4WY-wREsuI._adb-tls-connect._tcp device product:projectxblue model:SM_R920 device:projectxbl

adb -P 5038 install -r app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
```

Если `adb -P 5038 nodaemon server` не стартует из-за занятого порта, выберите другой порт (`5039`,
`5040`) и используйте его во всех командах через `-P`. После работы foreground server можно остановить
`Ctrl-C`; если нужно оставить соединение живым для следующих команд, не закрывайте Terminal 1.

### 7.1.1 Переподключение уже спаренных часов в новой сессии

После одного успешного `adb pair` ключ часов кэшируется в `~/.android/adbkey(.pub)` и **повторно
парить часы не нужно**. В новой сессии достаточно `adb connect`. Подводных камней два: (а) IP/port
часов могут поменяться (DHCP, перезагрузка часов, переключение Wi‑Fi), (б) стандартный ADB server
на `5037` иногда **не публикует** mdns‑сервисы Galaxy Watch (видно пустой `adb mdns services`),
тогда как clean server на отдельном порту видит часы корректно. Эталонный воркфлоу 2026‑04‑29:

На часах включить **`Wireless debugging`** (но НЕ открывать `Pair new device` — pair уже сделан).

На Mac:

```bash
# 1. Поднять отдельный clean ADB server (если 5038 занят — берите 5039 / 5040 / …).
adb -P 5039 nodaemon server &       # Background. Останется живым на сессию.

# 2. mdns обычно даёт два эндпоинта на одни часы — один из них connection-port.
adb -P 5039 mdns services
# Пример вывода:
# adb-RFAT629H4WY-wREsuI       _adb-tls-connect._tcp  10.0.0.16:40415
# adb-RFAT629H4WY-wREsuI (2)   _adb-tls-connect._tcp  10.0.0.16:35571

# 3. Подключиться. Первый объявленный port может вернуть `Connection refused` —
#    тогда пробуйте второй. Это нормально.
adb -P 5039 connect 10.0.0.16:40415        # → "Connection refused" в нашем прогоне
adb -P 5039 connect 10.0.0.16:35571        # → "connected to ..."

# 4. Подтвердить.
adb -P 5039 devices -l
# 10.0.0.16:35571   device  product:projectxblue model:SM_R920 device:projectxbl transport_id:2

# 5. С этого момента все команды — `adb -P 5039 -s 10.0.0.16:<port> ...`
WATCH=10.0.0.16:35571
adb -P 5039 -s $WATCH install -r app/build/outputs/apk/release/app-release-signed.apk
adb -P 5039 -s $WATCH shell dumpsys package com.example.sermontimer | grep -E "versionCode|versionName"
```

Что делать, если `connect` падает с `No route to host`:
- проверить `ping <ip>` — часы и Mac должны быть в одной Wi‑Fi сети;
- убедиться, что на часах `Settings → Connections → Wi‑Fi → ⋮ → Auto switch off` стоит в **Off** (иначе часы засыпают и Wi‑Fi отваливается);
- если IP часов изменился — пересмотреть `adb -P 5039 mdns services`, IP/port в выводе перебивают то, что было раньше.

Что делать, если `connect` возвращает `failed to authenticate`:
- ключ удалён или часы сделали reset → нужно сделать новый pair по §7.1 (полная процедура с 6‑значным кодом и `Pair new device`).

Galaxy Watch 5 = `model:SM_R920`, `product:projectxblue`. Эта строка — самый надёжный признак, что adb достучался до правильного устройства, а не до эмулятора.

### 7.2 Установка release‑APK на часы: `tools/install_watch_release.sh`

В репо есть готовый скрипт `tools/install_watch_release.sh`, который собирает release APK, выравнивает
(`zipalign`), подписывает (по умолчанию debug‑keystore — `~/.android/debug.keystore`, alias
`androiddebugkey`, pass `android`) и ставит на устройство. Один шаг от исходников до часов.

```bash
# Поставить на первое подключённое устройство в state=device
./tools/install_watch_release.sh

# Целевое устройство (например, реальные часы через Wireless Debugging)
./tools/install_watch_release.sh --device adb-RFAT629H4WY-wREsuI._adb-tls-connect._tcp

# Только собрать и подписать, без установки
./tools/install_watch_release.sh --skip-install
```

Полезные env‑override (см. `--help`): `SDK_DIR`, `BUILD_TOOLS_VERSION`, `KEYSTORE_PATH`,
`KEYSTORE_ALIAS`, `KEYSTORE_PASSWORD`, `GRADLEW`. Если на часах уже установлена версия с другой
подписью — `adb uninstall com.example.sermontimer` перед запуском (release‑debug-keystore не совпадёт
с `assembleDebug` сборкой Studio).

Для **debug‑APK** на эмулятор используйте обычный путь: `./gradlew :app:installDebug` или
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Подробный adb‑дебаг‑воркфлоу (скриншоты, тапы, dumpsys, подмена watch face и т.п.) — см.
`.claude/skills/wear-debug/SKILL.md`.

---

## 8) Стандарты кодирования

* Kotlin only; Compose UI; без новых XML‑layout’ов.
* Пакеты — feature‑first (см. структуру выше); избегать god‑objects.
* Именование: `TimerService`, `SermonTileService`, `PresetEditorScreen`, `TimerEngine`.
* Иммутабельность по умолчанию; состояние через Flow/State.
* Ошибки: в debug — fail‑fast; в release — безопасные дефолты (отрицательные длительности ⇒ 0).
* Логи: теги `TIMER`, `TILE`, `SRV`; без PII; в release — минимум шума.
* Ресурсы: строки/цвета централизованы; RU/EN локали по возможности.

---

## 9) Критерии приёмки (для PR)

Изменение **приемлемо**, если выполнено всё:

* ✅ Сборка под **API 34** проходит; установка и запуск на Wear AVD успешны.
* ✅ Unit‑тесты домена «движок таймера» зелёные; instrumentation‑smoke зелёный.
* ✅ Хаптика на всех границах; 0‑секции корректно обрабатываются.
* ✅ Tile показывает верную фазу/прогресс; кнопка «Start Timer» запускает пресет по умолчанию без открытия Activity; обновляется при boundary/pause/resume.
* ✅ Foreground‑уведомление всегда активно во время работы; действия работают.
* ✅ При RUNNING/PAUSED доступен Ongoing Activity‑чип на циферблате/в недавних; тап возвращает в таймер (при наличии разрешения уведомлений).
* ✅ Экран таймера в ambient‑режиме соответствует гайдлайнам Wear (чёрный фон, статический текст, без кнопок).
* ✅ **Применена политика §6**: в PR есть список источников (3–5 ссылок) и краткая проверка подхода.
* ✅ Нет новых permissions без обоснования; звук по умолчанию Off.
* ✅ Обновлены README и/или этот AGENTS.md, если менялось поведение/команды/ограничения.

---

## 10) Машина состояний и управление

```
IDLE
 └─ Start(preset) → RUNNING(INTRO|MAIN|OUTRO)
RUNNING(segment)
 ├─ tick → update remaining
 ├─ boundary → haptic + next segment
 ├─ Pause → PAUSED(segment)
 ├─ Skip  → next segment (если allowSkip)
 └─ Stop  → IDLE
PAUSED(segment)
 ├─ Resume → RUNNING(segment)
 └─ Stop   → IDLE
DONE
 └─ Stop/Reset → IDLE
```

**Хаптика границ:** Intro: короткий двойной; Main: тройной длиннее; Outro/Done: long–short–long.

---

## 11) Tile и уведомление — правила

* Tile в IDLE отображает дугу из трёх цветных сегментов (Intro/Main/Outro) по окружности от −120° до +120°, имя пресета по умолчанию, суммарное время и большую кнопку «Start Timer»; репуш по событиям: изменения состояния таймера.
* Большая кнопка на Tile = «Start Timer» — запускает ForegroundService с пресетом по умолчанию и сразу открывает Activity; при RUNNING/PAUSED текст и действие меняются на «View Progress»/«Resume», всегда ведёт в Activity.
* Уведомление: действия **Pause / Resume / Skip / Stop**; заголовок `"<Preset> • <Phase>"`, текст `"Remaining: mm:ss"` + общий прогресс.
* Foreground‑уведомление регистрирует **Ongoing Activity** (`watch-face chip`) с `touchIntent` -> `TimerActivity`; статус обновляется по фазам/остатку времени, remove при остановке/отсутствии разрешения.

---

## 12) Данные и персистентность

* `DataStore` хранит: список `presets`, `default_preset_id`, сериализованный `last_timer_state`.
* При запуске процесса и `status ∈ {RUNNING, PAUSED}`:

  * пересчитать остаток по `elapsedRealtime()`; восстановить или корректно завершить.

---

## 13) Надёжность и энергия

* Предпочтительно exact‑alarms на границах (если разрешено политикой и реально улучшают точность); иначе — тики 1–2 с + коррекция по `elapsedRealtime()`.
* Обратный отсчёт T−10 с выключенным экраном: `AlarmManager.setExactAndAllowWhileIdle()` с PendingIntent (`USE_EXACT_ALARM` на API 33+, `SCHEDULE_EXACT_ALARM` c `maxSdkVersion=32` для Wear OS 3.x). При отсутствии доступа сервис логирует предупреждение и возвращается к тик-движку (возможна задержка до 1–2 с в Doze).
* Хаптика в фоне: все вибрации сервиса используют `VibrationAttributes` (`USAGE_ALARM` для обратного отсчёта, `USAGE_NOTIFICATION` для фазовых сигналов), иначе система может заглушить импульсы при выключенном экране/Doze.
* Не удерживать экран; не держать ручных wakelock’ов; полагаться на ForegroundService. Countdown‑хаптика воспроизводится без wakelock с `VibrationAttributes.USAGE_ALARM`.

---

## 14) Security & Privacy

* Нет сети/облака; нет внешнего хранилища; нет аккаунтов.
* Логи — только техничские события; без пользовательских данных.
* Звук уважает DND; по умолчанию выключен.

---

## 15) DX‑процесс для агентов

**Перед изменениями:**

1. Прочитать README и AGENTS.md.
2. Проверить версии (API 34, Wear OS 5).
3. Оценить варианты реализации → применить §6 (поиск best practices + мини‑верификация).

**При добавлении фичи:**

* Сначала домен (чистый Kotlin) + unit‑тесты; затем Service/Tile/UI; затем instrumentation‑smoke.
* В PR приложить ссылки на источники и краткий итог выбора.

**При изменении поведения:**

* Сохранить обратную совместимость пресетов; при миграции — мигратор.
* Обновить документацию и критерии в этом файле.

---

## 16) Ветвление, коммиты, PR

* trunk‑based, короткоживущие ветки.
* Conventional Commits: `feat(tile): ...`, `fix(service): ...` и т. п.
* В PR: скриншоты/GIF для UI/Tile, список источников (§6), заметка про энергию/точность, если затронуто.

### 16.1 Версионирование при release

Перед каждым release‑коммитом **обязательно** поднимается обе пары в `app/build.gradle.kts`:

```kotlin
versionCode = 11      // +1 каждый релиз, монотонно
versionName = "1.10"  // semver-ish, видно в presets list и в Settings
```

Правила:
- bug‑fix only → `versionCode +1`, patch suffix (`1.10 → 1.10.1`) или просто minor bump, если patch не использовался
- новая фича без миграции → `versionCode +1`, minor bump (`1.10 → 1.11`)
- breaking (миграция пресетов / settings) → `versionCode +1`, major bump (`1.10 → 2.0`)

Bump делается **в том же коммите**, что и user‑visible изменение, не отдельно. Сборку проверять `./gradlew :app:assembleRelease -x lintVitalRelease`, `aapt dump badging` или `adb shell dumpsys package … | grep version`. Подробнее (включая полный pre‑release чек‑лист) — `.claude/skills/wear-debug/SKILL.md` §15.

### 16.2 Now bar / Promoted Ongoing chip — два слоя

Когда пишешь / правишь поведение нотификации таймера, помни:

1. **Код:** `TimerService.maybeApplyOngoingActivity` + `buildOngoingStatus` — публикует `OngoingActivity` со `Status.TimerPart` (живо тикает без пробуждения процесса). Категория `CATEGORY_STOPWATCH`, `LocusId`, accent‑цвет фазы, `setColorized(true)`.
2. **User‑side:** на Galaxy Watch (One UI Watch 7+) third‑party apps по умолчанию показываются в Now bar **только иконкой**. Чтобы видеть тикающий текст, юзер вручную включает: *Settings → Watch faces → Now bar → Стиль Now bar → Sermon Timer → «Иконка с текстом»*. Это критично: 95% репортов «капсула пустая» не про код, а про эту настройку. Гайд для пользователя — в `readme.md` секции «Капсула на циферблате (Now bar / Promoted Ongoing chip)».

Forensics, отличия от Google codelab, отличия от Android 16 Live Updates API — см. `.claude/skills/wear-debug/SKILL.md` §17.

---

## 17) Матрица тестов (локально для агентов)

* Функционал: старт с Tile; pause/resume/skip/stop; 0‑секций; Done.
* Надёжность: `adb shell am force-stop <pkg>` → восстановление; `adb shell cmd deviceidle force-idle` → точность границ.
* Устройства: Wear AVD API 34 + (при наличии) реальные Galaxy Watch 5 (ADB‑over‑Wi‑Fi).
* Производительность: CPU не «залипает», батарея в норме, UI без рывков.

---

## 18) Guard‑Rails (жёсткие запреты)

* ❌ Добавление зависимости от телефона/сети/аккаунтов.
* ❌ Замена ForegroundService на чистый бэкграунд без уведомления.
* ❌ Попытка «тикать» Tile каждую секунду кастомной перерисовкой.
* ❌ Новые разрешения без явного обоснования и документации.
* ❌ Любая телеметрия/PII/секреты в коде или логах.

---

## 19) Флаги (feature toggles)

* `useExactBoundaryAlarms` (default: false) — перейти на AlarmManager для границ.
* `enableSounds` (default: false) — звуки на границах с уважением DND.
* `smartOutro` (default: false) — сжатие Outro при перерасходе Main.

Документируйте любое изменение флагов в README и здесь.

---

## 20) Глоссарий

Tile — свайп‑право виджет Wear OS;  Complication — слот данных на циферблате;
Dynamic Time — привязка прогресса ко времени без активной перерисовки;
ForegroundService — сервис с постоянным уведомлением;  Doze/Idle — энергосбережение.
