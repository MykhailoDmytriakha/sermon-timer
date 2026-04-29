---
name: wear-debug
description: Debug the Sermon Timer Wear OS app on a connected emulator or Galaxy Watch via adb — install APK, take screenshots, simulate taps/swipes, read logcat, dump notifications and OngoingActivity state, control watch faces. Use whenever you need to verify timer/tile/notification/complication behavior end-to-end without Android Studio. Trigger this skill any time the user asks you to "проверить на эмуляторе", "запустить на часах", "посмотреть логи", "снять скриншот", "тапнуть на кнопку", "проверить нотификацию", "проверить капсулу/Now bar/Ongoing Activity", or anything similar that needs runtime verification on a Wear OS device.
---

# Wear OS Debug Loop

Goal: a fast, repeatable round-trip of **build → install → drive UI → observe → diff** for `com.example.sermontimer` running on a connected Wear OS surface (AVD or real Galaxy Watch).

This skill does not replace Android Studio. It replaces the *parts* of Android Studio you would otherwise click — Logcat, Run, Layout Inspector, screenshot — with `adb` commands you can chain into a script.

---

## 0) Pre-flight

```bash
adb devices                              # confirm one of: emulator-5554 or adb-RFAT...:tls-connect
adb -s emulator-5554 shell getprop ro.product.model       # sdk_gwear_arm64 = AVD; SM_R920 = Galaxy Watch 5
adb -s emulator-5554 shell getprop ro.build.version.sdk   # API level (34 = Wear OS 5)
adb -s emulator-5554 shell wm size                        # screen size; 454x454 for Large Round
```

For a Galaxy Watch over Wi-Fi: **first‑time pairing** — `AGENTS.md §7.1` (uses a clean `adb -P 5038/5039 nodaemon server` and a 6‑digit code). **Reconnecting an already‑paired watch in a new session** — `AGENTS.md §7.1.1` (no code needed; `adb -P 5039 mdns services` → pick the right `_adb-tls-connect._tcp` endpoint → `adb -P 5039 connect <ip>:<port>`). Galaxy Watch 5 advertises `model:SM_R920 product:projectxblue` — use that to confirm you reached the watch, not the emulator.

**Wireless port changes when the watch sleeps.** The dynamic adb port (e.g. `41987`) is reissued every time the watch reconnects to Wi-Fi after going idle. If `adb -P 5039 devices -l` shows the watch missing or `connect` returns "Connection refused", the watch slept; ask the user to wake it (tap face / press power) and *then* re-run `adb -P 5039 mdns services` to find the new port. Don't loop reconnecting on the old port — it won't come back.

For everything below, assume `-s emulator-5554`. Substitute the device serial (`10.0.0.16:<port>` for the watch) and add `-P 5039` if you're going through the dedicated server.

**Set the device serial once** so commands stay short:

```bash
export DEV=emulator-5554        # or "adb-RFAT...:tls-connect"
alias a="adb -s $DEV"
```

The rest of this skill uses `$DEV` literally — replace with your serial or use the alias.

---

## 1) Build & install

### Debug build (default for emulator iteration)

```bash
./gradlew :app:assembleDebug                                              # builds in /app/build/outputs/apk/debug/
adb -s $DEV install -r -d app/build/outputs/apk/debug/app-debug.apk       # -r reinstall, -d allow downgrade
```

For an iterative loop, build is usually fast (UP-TO-DATE → ~2 s); the install dominates. Don't over-clean.

### Release build to a real Galaxy Watch (R8, signed)

The repo ships `tools/install_watch_release.sh` which assembles release, `zipalign`s, signs with the
debug keystore, and installs in one shot. Use this when you need to test the **release** flavour
(R8/resource-shrinking on, see `PERF_NOTES.md`) on the watch:

```bash
./tools/install_watch_release.sh                              # installs on the first ready device
./tools/install_watch_release.sh --device <serial>            # explicit target
./tools/install_watch_release.sh --skip-install               # build & sign only
```

See `AGENTS.md §7.2` for the full env-var override list.

**Caveat: the install_watch_release.sh script uses the default adb server (port 5037)**. If your watch is connected through `adb -P 5039`, the script will fail at the install step with `device 'X.X.X.X:port' not found`. Either:

1. Use `--skip-install` and install manually:
   ```bash
   ./tools/install_watch_release.sh --skip-install
   adb -P 5039 -s 10.0.0.16:<port> install -r app/build/outputs/apk/release/app-release-signed.apk
   ```
2. Or sign manually with build-tools and install on the dedicated server:
   ```bash
   SDK="$HOME/Library/Android/sdk"
   BTOOLS=$(ls -1d "$SDK"/build-tools/* | sort -V | tail -1)
   "$BTOOLS/zipalign" -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
   "$BTOOLS/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
       --key-pass pass:android --ks-key-alias androiddebugkey \
       --out /tmp/signed.apk /tmp/aligned.apk
   adb -P 5039 -s 10.0.0.16:<port> install -r /tmp/signed.apk
   ```

**Pre-existing lint baseline:** `app/build.gradle.kts` disables `InvalidFragmentVersionForActivityResult` (Wear OS doesn't ship `androidx.fragment`; the lint rule is a false positive on this surface). If a release build starts failing on `lintVitalRelease` for a different rule, weigh whether to `disable` it or fix the underlying issue.

### When install fails

If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch, common after switching
between Studio's debug build and the release script), uninstall first:

```bash
adb -s $DEV uninstall com.example.sermontimer
```

---

## 2) Permissions you almost always need

`POST_NOTIFICATIONS` is required on Wear OS 4+ for the foreground notification (and therefore the Now bar chip). Reset by `pm clear`, so re-grant after every wipe:

```bash
adb -s $DEV shell pm grant com.example.sermontimer android.permission.POST_NOTIFICATIONS
```

If the `dumpsys notification` block shows `importance=NONE userSet=false` for our package — permission is missing.

---

## 3) Launching the app and the timer

The TimerService is `android:exported="false"`, so `am start-foreground-service` from the shell will fail with `Requires permission not exported`. There are three reliable entry points:

```bash
# A) Open the main UI
adb -s $DEV shell am start -n com.example.sermontimer/.presentation.MainActivity

# B) Headless start of the timer with the default preset (preferred for automation)
adb -s $DEV shell am start -n com.example.sermontimer/.tile.TileActionActivity \
    --es "com.example.sermontimer.tile.EXTRA_TILE_ACTION" "start" \
    --es "com.example.sermontimer.tile.EXTRA_PRESET_ID" "sermon-7-20-8"

# C) Tap the on-screen Play button (after MainActivity opens)
adb -s $DEV shell input tap 241 317
```

Other Tile actions: `pause`, `resume`, `view_progress`, `open_app`. Default preset id: `sermon-7-20-8`. Second seeded preset: `small-group-15-20-15`.

**Caveat:** the very first cold start after `pm clear` doesn't have presets seeded yet. `MainActivity` triggers `PresetInitializer`. If you need presets *before* you launch the Tile path, open `MainActivity` once and `sleep 4` first.

### Reset to a clean state

```bash
adb -s $DEV shell am force-stop com.example.sermontimer
adb -s $DEV shell pm clear com.example.sermontimer
adb -s $DEV shell pm grant com.example.sermontimer android.permission.POST_NOTIFICATIONS
adb -s $DEV shell am start -n com.example.sermontimer/.presentation.MainActivity   # seed presets
sleep 5
```

---

## 4) Driving the UI from the shell

### Find element coordinates with uiautomator

```bash
adb -s $DEV shell uiautomator dump /sdcard/ui.xml
adb -s $DEV pull /sdcard/ui.xml /tmp/ui.xml
# Find a button by content-desc:
grep -oE 'content-desc="Play"[^/]*bounds="[^"]*"' /tmp/ui.xml
# Output: content-desc="Play" ... bounds="[209,285][273,349]"
# Center: x = (209+273)/2 = 241, y = (285+349)/2 = 317
```

Compose UI emits content-desc only for nodes with explicit `Modifier.semantics { contentDescription = ... }` — most of our buttons (Play, Pause, Skip, Edit, Default) have it. Text nodes (`text="06:19"`) are also exposed.

### Input commands

```bash
# Tap (single point)
adb -s $DEV shell input tap <x> <y>

# Swipe (x1 y1 x2 y2 duration_ms)
adb -s $DEV shell input swipe 227 440 227 100 300        # bottom-up: open notification stream
adb -s $DEV shell input swipe 350 227 100 227 200        # right-to-left: next watch face in picker

# Long-press (same coords, long duration)
adb -s $DEV shell input swipe 227 227 227 227 800        # 800ms long-press = open watch-face picker

# Hardware keys
adb -s $DEV shell input keyevent KEYCODE_HOME            # exit to watch face
adb -s $DEV shell input keyevent KEYCODE_BACK
adb -s $DEV shell input keyevent KEYCODE_POWER           # screen on/off
adb -s $DEV shell input text "hello"                     # IME text input
```

Wear-OS-specific gestures:
| What | How |
|---|---|
| App drawer / launcher | `keyevent KEYCODE_POWER` (single press) |
| Notification stream | swipe up from bottom: `input swipe 227 440 227 100 300` |
| Quick-settings shade | swipe down from top: `input swipe 227 5 227 350 300` |
| Tiles strip | swipe right-to-left from left edge: `input swipe 0 227 350 227 200` |
| Watch-face picker | long-press centre: `input swipe 227 227 227 227 800` |

---

## 5) Screenshots

```bash
adb -s $DEV shell screencap -p /sdcard/s.png && adb -s $DEV pull /sdcard/s.png /tmp/s.png
```

Then `Read /tmp/s.png` to view the image. Wear OS round-screen output is a 454×454 PNG with the corners as transparent / black; that's the launcher area beyond the bezel.

For a quick burst (e.g. to verify ticking text on a chip), take 3 shots over 10 s:

```bash
for i in 1 2 3; do
  adb -s $DEV shell screencap -p /sdcard/s.png
  adb -s $DEV pull /sdcard/s.png /tmp/s$i.png
  sleep 4
done
```

---

## 6) Logcat — what to grep for

The codebase uses three log tags: **`TIMER`** (engine/state), **`SRV`** (TimerService), **`TILE`** (TileActionActivity, SermonTileService). Always clear before a test, then filter:

```bash
adb -s $DEV logcat -c                               # clear ring buffer
# … trigger the action …
adb -s $DEV logcat -d -s TIMER SRV TILE | tail -30  # -d = dump and exit; -s = whitelist tags
```

Useful event lines you should see during a healthy run:
- `D TIMER : EVENT: PrerollStarted`
- `D TIMER : EVENT: PrerollEnded`
- `D TIMER : COUNTDOWN: scheduling at t=… for boundary=…`
- `D TIMER : EVENT: BoundaryReached - nextSegment=MAIN`
- `D TIMER : EVENT: TimerStopped`
- `I TILE  : TileActionActivity handling action=start preset=…`
- `I TILE  : Tile snapshot → status='RUNNING' …`

If you don't see `EVENT: PrerollStarted` after starting the timer — the service started but never loaded the preset (DataStore not seeded, or `startTimerWithPreset` blew up early). Read full logs:

```bash
adb -s $DEV logcat -d --pid $(adb -s $DEV shell pidof com.example.sermontimer) | tail -100
```

For a live tail while you reproduce a bug, use the `Monitor` tool with `tail -f`-style capture; otherwise `logcat -d` (snapshot) is enough.

---

## 7) Inspecting notifications and the chip

This is the meat of any "why doesn't the chip show?" / "why doesn't the chip background colour?" investigation.

```bash
adb -s $DEV shell dumpsys notification --noredact > /tmp/notif.txt
awk '/NotificationRecord.*com\.example\.sermontimer/,/^[[:space:]]*$/' /tmp/notif.txt | head -50
```

The Sermon Timer notification we ship targets Galaxy Watch's **rich Now bar path** (`ConvertingNowBarData` → `WFOverlayNowBarCardView`). The contract was reverse-engineered from the stock Samsung Timer (`com.samsung.android.watch.timer` / `TimerWatch.apk`) by dumping its live notification and diff-ing against ours. Healthy fields look like:

| Field | What it means | Healthy value |
|---|---|---|
| `flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE` | foreground service contract | all three set |
| `category=alarm` | drives Now bar slotting on Galaxy Watch (NOT `stopwatch`) | `alarm` |
| `vis=PUBLIC` or `PRIVATE` | visibility | either OK; stock Timer uses PRIVATE |
| `actions=N` | Pause / Skip / Stop wired through | 2-3 |
| `customDisplayBundle=Bundle (...)` | the rich-path payload — **must be present** | dataSize ≈ 5 KB |
| `forceAutoResume=Boolean (true)` | sibling of `customDisplayBundle` on outer extras | true |
| `ambientImmediateExpire=Boolean (true)` | sibling of `customDisplayBundle` on outer extras | true |
| `android.title`, `android.text`, `android.subText` | content title/text | **all `null`** — populated values poison routing |
| `android.showChronometer` | system chronometer | **`false`** — chronometer lives inside `customDisplayBundle.nowBarData.cardChronometerRemoteView` instead |
| `android.template` (anything `androidx.core.ongoing.*`) | OngoingActivity / Wear extender payload | **MUST be absent** — present routes us to legacy `OANowBarController` (grey chip) |

Crosscheck against the Wear-side view (this is what `WearServices` ingested):

```bash
adb -s $DEV logcat -d | grep -E "isOngoingActivityStyle|sermontimer.*StreamItemData" | tail -5
```

Look for `isOngoingActivityStyle=false` (we are *not* an OngoingActivity-styled stream item — we ride the rich path instead). If you see `true`, something in the build chain is injecting `androidx.core.ongoing.*` extras and you've lost the rich path.

**Foreground service notification + `notificationManager.cancel(id)` is forbidden on Android 14+.** Our `updateNotification` uses bare `notify()` for the same reason: cancelling the FGS notification tears down the foreground state and the next `notify()` arrives as a regular notification (sysui drops it from Now bar). The stock Samsung Timer does `cancel; notify` — they get away with it because they're a privileged system app. We can't.

---

## 8) Inspecting the foreground service

```bash
adb -s $DEV shell dumpsys activity services com.example.sermontimer | head -40
```

Key fields:
- `isForeground=true` — service is actually FGS, not just started.
- `types=40000000` — `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (Android 14+).
- `foregroundNoti=Notification(channel=timer … category=stopwatch actions=N …)` — the live notification, mirrors the dump above.

If `isForeground=false` and `foregroundNoti=null` after starting via the Tile path — `startForeground()` failed (usually missing `POST_NOTIFICATIONS`). Re-grant and retry.

---

## 9) Watch faces — picking one that exposes the chip

The default chronograph face on the Wear AVD does NOT render an elongated Promoted Ongoing chip — only an icon-only fallback at 6 o'clock. The **Classic** face (and most faces with explicit complication slots) renders the elongated capsule in a slot.

To swap face from the shell:

```bash
adb -s $DEV shell input keyevent KEYCODE_HOME
adb -s $DEV shell input swipe 227 227 227 227 800   # long-press centre → opens picker
adb -s $DEV shell input swipe 350 227 100 227 200   # browse next face (repeat to cycle)
adb -s $DEV shell input tap 227 200                 # tap the previewed face to apply
```

Faces that worked for verifying our chip on the AVD: **Classic**. Faces that did NOT: **Concentric** (chronograph). Real Galaxy Watch behaviour differs — Samsung Now bar renders independently of the watch face on One UI Watch 7+.

---

## 10) Tiles

Tiles live separately from notifications. To force a Tile re-render:

```bash
adb -s $DEV shell am start -n com.example.sermontimer/.tile.TileActionActivity \
    --es "com.example.sermontimer.tile.EXTRA_TILE_ACTION" "open_app"
```

Look for `I TILE : Tile snapshot → status='IDLE' …` in logcat — it's emitted on every Tile build pass. To view the Tile UI on the AVD, swipe right-to-left from the left edge of the watch face (gesture in §4).

---

## 11) Complications

The complication service is `.complication.TimerComplicationService`. To see whether it's wired up and to force a refresh:

```bash
adb -s $DEV shell dumpsys package com.example.sermontimer | grep -A 2 "BIND_COMPLICATION_PROVIDER"
```

There is no public adb to "drop" a complication on a face — the user has to add it from the watch-face customization UI manually. To verify the service responds, check the complication framework logs:

```bash
adb -s $DEV logcat -d 2>&1 | grep -i "complication.*sermon" | tail -10
```

---

## 12) Snapshot recipe — full baseline of the running timer

Use this exact sequence whenever you need a "what does the system see right now?" capture:

```bash
mkdir -p /tmp/sermon-snap
adb -s $DEV shell dumpsys notification --noredact > /tmp/sermon-snap/notif.txt
adb -s $DEV shell dumpsys activity services com.example.sermontimer > /tmp/sermon-snap/service.txt
adb -s $DEV logcat -d -s TIMER SRV TILE > /tmp/sermon-snap/tags.log
adb -s $DEV logcat -d 2>&1 | grep -E "isOngoingActivityStyle|sermontimer" | tail -50 > /tmp/sermon-snap/wear.log
adb -s $DEV shell screencap -p /sdcard/s.png
adb -s $DEV pull /sdcard/s.png /tmp/sermon-snap/screen.png
ls -la /tmp/sermon-snap/
```

Then `Read /tmp/sermon-snap/screen.png` and `cat`/`Read` the txt files. This is enough context to diagnose any chip / notification / state issue.

---

## 13) Common failure modes (and what to do)

| Symptom | Most likely cause | Fix |
|---|---|---|
| Now bar chip is grey, not phase-coloured | Notification routed via legacy `OANowBarController` instead of rich `ConvertingNowBarData` — usually because `androidx.core.ongoing.*` extras are present | `logcat -d \| grep -E "OANowBar\|ConvertingNowBar"` to confirm path. If on legacy: search the publish chain for `OngoingActivity.Builder`, `MessagingStyle`, `setRequestPromotedOngoing`, etc. and remove. See section 14b. |
| Now bar chip is coloured but text is frozen / blank | sysui couldn't `findViewWithTag("aod_chronometer")` on our RemoteViews | `logcat -d \| grep "Could not fetch the chronometer"` to confirm. Add `android:tag="aod_chronometer"` to the `<Chronometer>` in `res/layout/nowbar_chronometer.xml`. |
| Foreground notification disappears after a state change | `notificationManager.cancel(NOTIFICATION_ID)` called on the FGS notification (Android 14+ tears down FGS state) | Use bare `notify()` for FGS updates. Stock Samsung Timer can `cancel; notify` because they're a privileged system app — we can't. |
| Now bar chip is icon-only (no text, no colour) on Pixel Watch / generic Wear OS face | The customDisplayBundle path is Galaxy-Watch-only; Pixel Watch's launcher reads vanilla notification fields | Cross-platform fallback would need legacy OngoingActivity *or* a generic NotificationStyle. Currently we ship customDisplayBundle-only and rely on Galaxy Watch routing. |
| `dumpsys notification` shows `importance=NONE userSet=false` | `POST_NOTIFICATIONS` missing | `pm grant ... POST_NOTIFICATIONS` |
| `am start-foreground-service` returns `Requires permission not exported` | Service is `exported="false"` (intentional) | Use TileActionActivity intent path instead (§3 option B) |
| No `EVENT: PrerollStarted` after start | Preset not in DataStore | Open MainActivity once (`am start ...MainActivity`) before triggering Tile path |
| Foreground service notification still on stream after timer Stop | Service did not call `stopForeground(STOP_FOREGROUND_REMOVE)` before `stopSelf()` | Add to the IDLE branch of `observeTimerState`; we already do this in `TimerService` |
| Build-modify-test loop is slow | You're running the full Gradle pipeline | Trust the incremental build — `./gradlew :app:assembleDebug` is ~2 s for a one-line change |

---

## 14a) Pixel‑verify screenshots (don't trust your eyes)

When fixing visual bugs, **always verify chip / capsule colours by pixel‑sampling** the captured PNG, not by looking at it. A small screenshot with a coloured icon next to a grey background visually bleeds — your brain will see "the whole thing is green" when only the icon is green. Pixel sample is honest.

### Fast path: Pillow (preferred when available)

```bash
adb -s $DEV shell screencap -p /sdcard/s.png
adb -s $DEV pull /sdcard/s.png /tmp/s.png

# One-time install on macOS Homebrew Python:
pip3 install --break-system-packages Pillow

python3 - <<'PY'
from PIL import Image
im = Image.open('/tmp/s.png').convert('RGBA')
print('size', im.size)
# Now bar capsule is centred horizontally, ~y=400 on a 450px round face.
# Sample left → right to confirm gradient from cardColorStart to cardColorEnd.
for y in (395, 400, 405, 410, 415):
    row = [im.getpixel((x, y)) for x in (130, 160, 200, 250, 300, 340)]
    print(f'y={y}:', row)
PY
```

Healthy output for the green INTRO phase looks like:

```
y=400: [(0,0,0,255), (94,172,98,255), (87,158,90,255), (82,150,84,255), (78,142,80,255), (75,135,77,255)]
                       ^^^^^^^^^^^^                                                            ^^^^^^^^^
                       cardColorStart (≈ 0xFF66BB6A — our INTRO green)               cardColorEnd (darkened)
```

If every pixel inside the chip body is grayscale (`max(rgb) - min(rgb) < 12`), the colour is **not** being honoured — sysui dropped our `cardColorStart/End` and is rendering the system grey gradient. See section 14b for routing diagnostics.

### Slow path: pure stdlib (no install needed)

Useful in clean environments where you can't `pip3 install`:

```bash
python3 - <<'PY'
from pathlib import Path
import struct, zlib
def read_png(p):
    data = Path(p).read_bytes()
    pos = 8; chunks = {}
    while pos < len(data):
        L = struct.unpack('>I', data[pos:pos+4])[0]
        T = data[pos+4:pos+8]; D = data[pos+8:pos+8+L]
        chunks.setdefault(T, []).append(D); pos += 12 + L
    W,H,d,c = struct.unpack('>IIBB', chunks[b'IHDR'][0][:10])
    raw = zlib.decompress(b''.join(chunks[b'IDAT']))
    ch = {0:1,2:3,3:1,4:2,6:4}[c]; bpp = ch*(d//8); st = W*bpp
    rows=[]; prev=bytes(st); pp=0
    for _ in range(H):
        f=raw[pp]; pp+=1; line=bytearray(raw[pp:pp+st]); pp+=st
        if f==1:
            for i in range(st):
                left = line[i-bpp] if i>=bpp else 0
                line[i] = (line[i]+left) & 0xFF
        elif f==2:
            for i in range(st):
                line[i] = (line[i]+prev[i]) & 0xFF
        elif f==3:
            for i in range(st):
                left = line[i-bpp] if i>=bpp else 0
                line[i] = (line[i]+(left+prev[i])//2) & 0xFF
        elif f==4:
            for i in range(st):
                a = line[i-bpp] if i>=bpp else 0
                b = prev[i]; cc = prev[i-bpp] if i>=bpp else 0
                p_=a+b-cc; pa,pb,pc=abs(p_-a),abs(p_-b),abs(p_-cc)
                pred = a if pa<=pb and pa<=pc else (b if pb<=pc else cc)
                line[i] = (line[i]+pred) & 0xFF
        rows.append(bytes(line)); prev=bytes(line)
    return W,H,ch,b''.join(rows)
W,H,ch,px = read_png('/tmp/s.png')
def pixel(x,y):
    i = (y*W+x)*ch; return px[i],px[i+1],px[i+2]
# probe horizontal across the chip area at y=410 (Wear OS round 454x454)
for x in range(120, 340, 20):
    r,g,b = pixel(x, 410)
    if max(r,g,b)-min(r,g,b) >= 12:
        tag = "GREEN" if g>r and g>b else "BLUE" if b>r and b>g else "RED/AMBER" if r>g and r>b else "MIX"
    else: tag = "GRAYSCALE"
    print(f"  ({x},410) RGB=({r},{g},{b}) [{tag}]")
PY
```

If the chip body samples come back `GRAYSCALE` even when an icon at the left edge is `GREEN`, the icon is the only coloured part — the system chip background was NOT honoured. This catches "the fix didn't actually fix it" cases that eyeballing a 450px screenshot can't.

## 14b) Watch‑side NowBar diagnostics (Galaxy Watch / One UI 8)

Specific to Samsung Galaxy Watch's Now bar surface. Read four things together:

```bash
# 1. Our notification extras as the framework sees it
adb -s $DEV shell dumpsys notification --noredact > /tmp/our.txt
awk '/NotificationRecord.*com\.example\.sermontimer/,/^[[:space:]]*$/' /tmp/our.txt | head -50

# 2. The stock Samsung Timer's live notification (golden baseline — see section 18)
adb -s $DEV shell am start -n com.samsung.android.watch.timer/.activity.TimerHomeActivity
# (drive UI to start the stock timer, then:)
adb -s $DEV shell dumpsys notification --noredact > /tmp/stock.txt
awk '/NotificationRecord.*com\.samsung\.android\.watch\.timer/,/^[[:space:]]*$/' /tmp/stock.txt | head -50

# 3. Diff the extras keysets — the symmetric difference is where routing decisions live
diff <(grep -E '^[[:space:]]+(android\.|customDisplayBundle|forceAutoResume|ambient)' /tmp/our.txt | sort -u) \
     <(grep -E '^[[:space:]]+(android\.|customDisplayBundle|forceAutoResume|ambient)' /tmp/stock.txt | sort -u)

# 4. Live routing signal — which sysui code path consumed our notification
adb -s $DEV logcat -d | grep -iE "NowBar|OANowBar|ConvertingNowBar|setBackgroundGradient|Could not fetch" | tail -30
```

### Routing logs — which path is our notification on?

| Log line | What it means | Verdict |
|---|---|---|
| `[ConvertingNowBarData] convert(...) > request 2 job(s)` | rich `customDisplayBundle` path — sysui is reading our `nowBarData` Bundle | ✅ this is where we want to be |
| `[WFOverlayNowBarCardView] setBackgroundGradient(...) > card BG startColor: <int> card BG endColor: <int>` | sysui successfully read our `cardColorStart` / `cardColorEnd` and is painting the gradient | ✅ chip will render coloured |
| `[WFOverlayNowBarCardView] setData(...) > Could not fetch the chronometer` | sysui inflated our `cardChronometerRemoteView` but `findViewWithTag("aod_chronometer")` returned null | ⚠️ chip background fine, but ticking text dropped — see "chronometer-tag landmine" below |
| `[OANowBarController] notifyNowBarOfOngoingActivityItem(...)` | **legacy** OngoingActivity path — our notification carried `androidx.core.ongoing.*` extras and was routed away from the rich path | ❌ chip will be grey; root cause is `OngoingActivity.Builder` somewhere in the publish chain |
| `[WearSdkAlertingProcessor] FILTERED - ONGOING_ACTIVITY_TYPE` | confirms the legacy routing | ❌ |

Decode the gradient colour ints printed by sysui to confirm they match your `phaseAccent`:

```bash
python3 -c "v=-10044566; print(hex(v & 0xFFFFFFFF))"   # → 0xff66bb6a (our INTRO green)
python3 -c "v=-19712;    print(hex(v & 0xFFFFFFFF))"   # → 0xffffb300 (our PREROLL amber)
```

### The contract for the rich path (verified working)

Set on outer `notification.extras`:

```
customDisplayBundle: Bundle
    enableNowBar: Boolean = true                        # gate flag
    nowBarData: Bundle
        type: int = 1                                   # standard card type
        cardIconLeft: Icon                              # phase-coloured circle bitmap
        queIcon: Icon                                   # mirror of cardIconLeft
        cardContents: String                            # fallback static text (mm:ss)
        cardChronometerRemoteView: RemoteViews          # holds <Chronometer android:tag="aod_chronometer"/>
        expandViewIcon: Icon
        expandChronometerRemoteView: RemoteViews
        expandChronometerPosition: int = 1
        cardColorStart: int                             # ARGB — chip background gradient start
        cardColorEnd: int                               # ARGB — chip background gradient end
        expandViewColorStart: int                       # NOT "expandColorStart"!
        expandViewColorEnd: int                         # NOT "expandColorEnd"!
forceAutoResume: Boolean = true                         # sibling of customDisplayBundle
ambientImmediateExpire: Boolean = true                  # sibling of customDisplayBundle
```

What MUST be absent (otherwise routing falls back to legacy):
- any `androidx.core.ongoing.*` extras (no `OngoingActivity.Builder`!)
- `MessagingStyle`, `ProgressStyle`, `setRequestPromotedOngoing`
- non-null `android.title` / `android.text` / `android.subText` (stock Timer uses null)
- system chronometer (`setUsesChronometer(true)`)

### The chronometer-tag landmine

Sysui's `WFOverlayNowBarCardView.setData` does:

```java
remoteViews.apply(context, cardChronometerViewContainer);  // inflate our RV
View child = cardChronometerViewContainer.findViewWithTag("aod_chronometer");
this.chronometer = (Chronometer) child;
if (this.chronometer == null) {
    LogUtil.logE("NowBar", "Could not fetch the chronometer");
    // chip background renders fine, but the ticking text is dropped
}
```

So our `res/layout/nowbar_chronometer.xml` MUST be:

```xml
<Chronometer xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/nowbar_chronometer"
    android:tag="aod_chronometer"   <!-- THE critical attribute -->
    .../>
```

(Found in `SecClockworkSysUi.apk` DEX — search for `"Could not fetch the chronometer"` and walk back to the `findViewWithTag` call.)

### `cardContents` is not enough on its own

If you only set `cardContents` (a static String) without `cardChronometerRemoteView`, the chip displays the static value at the moment of `notify()` — frozen. Sysui only replaces `cardContents` with the live ticking number when it successfully fetches a Chronometer view tagged `aod_chronometer`. So the RemoteView is mandatory if you want a ticking chip without per-second republishes.

### Why we used to think the rich path was signature-gated

**It isn't.** It's *shape-gated*. Every variant we tried with `OngoingActivity.Builder` rendered grey, and the working stock Timer lives in `/system/priv-app/`, so the path of least cognitive resistance was "must be signature-gated." The actual gate, found by capturing the stock Timer's live notification with `dumpsys`, was the *absence* of `androidx.core.ongoing.*` extras: stock Timer doesn't use `OngoingActivity` at all. The moment we dropped `OngoingActivity.Builder` from our publish chain and matched the stock Timer's exact extras shape, the chip turned green.

Lesson reusable elsewhere: **routing decisions branch on Bundle shape, not on API choice.** Any helper that auto-injects extras (OngoingActivity, MessagingStyle, vendor-specific) can silently change which renderer takes your notification. Live-RE the working baseline, diff keysets, and subtract before you add. See section 18 for the full live-RE workflow.

## 14) Cleanup

When done with a debugging session, leave the AVD running but stop our app:

```bash
adb -s $DEV shell am force-stop com.example.sermontimer
```

Don't `pm clear` unless you specifically want to test cold start — it wipes presets, settings, last-known timer state, and POST_NOTIFICATIONS.

---

## 15) Bumping the app version before release

**Always bump both fields together** in `app/build.gradle.kts` before producing a release APK:

```kotlin
defaultConfig {
    versionCode = 11      // ← integer, monotonically increasing, +1 every release
    versionName = "1.10"  // ← user-visible semver-ish string; bump major/minor/patch as appropriate
}
```

| Change scope | versionCode | versionName |
|---|---|---|
| New feature, schema-compatible | +1 | minor bump (`1.10 → 1.11`) |
| Breaking change (preset format / settings migration) | +1 | major bump (`1.10 → 2.0`) |
| Bug fix only | +1 | patch suffix (`1.10 → 1.10.1`) — or just bump minor if the project hasn't used patch suffixes before |

`versionName` shows in the title bar of the presets list (`v1.10`) and in the Settings screen — keep it short. `versionCode` must always go up; Play Store / sideload installers reject downgrades.

**Workflow before tagging a release:**

```bash
# 1. Bump in app/build.gradle.kts (manual edit)
# 2. Verify — quick sanity build:
./gradlew :app:assembleRelease -x lintVitalRelease

# 3. Confirm the new version is baked into the APK:
$ANDROID_HOME/build-tools/*/aapt dump badging app/build/outputs/apk/release/app-release-unsigned.apk | grep -E "versionCode|versionName"
# Or after install:
adb -s $DEV shell dumpsys package com.example.sermontimer | grep -E "versionCode|versionName"

# 4. Commit with a Conventional Commits release line:
git commit -m "chore(release): v1.11"

# 5. Run the install script to ship to a watch:
./tools/install_watch_release.sh
```

If you forget to bump and try to install: `adb install -r` will succeed (same `versionCode`), but the user sees no change — and any release-flagged QA matrix is invalidated. Set a habit: **bump on the same commit as the user-visible change.**

---

## 16) Galaxy Watch user-side opt-in (Now bar)

Samsung One UI Watch 7+ exposes a per-app Now bar style toggle. With v1.21+ (the rich `customDisplayBundle` path) the chip renders correctly under the default "Icon with text" mode out of the box — no special setup required. Document the setting only because users sometimes flip it to "Icon only" during a previous troubleshooting session and then ask why text disappeared:

**On the Galaxy Watch:** *Settings → Watch faces → Now bar → Now bar style → Sermon Timer → "Icon with text"*.

(In Russian/Spanish UI: *Настройки → Циферблаты → Now bar → Стиль Now bar → Sermon Timer → "Иконка с текстом"* / *Estilo de Now bar → Cronómetro → "Icono con texto"*.)

When triaging a "capsule shows no text" report from a Galaxy Watch user, ask them to check this setting **after** verifying via `dumpsys notification` that our `customDisplayBundle.nowBarData` is published correctly. On Wear OS without One UI (Pixel Watch, generic Wear OS faces) the customDisplayBundle path doesn't exist at all — the chip will fall back to whatever the launcher renders from a plain foreground notification (icon + content text, depending on slot).

---

## 17) Forensic notes on the Now bar chip (Galaxy Watch v1.21+)

Documented after solving the "grey chip" problem in v1.21. Keep this section for "why does the architecture look like this" and "what NOT to bring back."

### What our notification ships (the working contract)

| Requirement | Where it's set | Why |
|---|---|---|
| `setOngoing(true)` + foreground service | `TimerService.createNotification` + `ServiceCompat.startForeground` | `isOngoing=false` in WearServices stream → never enters the Now bar candidate set. |
| `setCategory(CATEGORY_ALARM)` | `TimerService.createNotification` | Stock Samsung Timer uses `alarm`. `stopwatch` works on Wear OS but Galaxy Watch's Now bar slotting prefers `alarm` for timer-style chips. |
| Channel `IMPORTANCE_DEFAULT` (sound/vibration disabled at channel level) | `TimerService.createNotificationChannel` | `IMPORTANCE_LOW` makes Now bar treat us as a stream-only item; `DEFAULT` is required for chip rendering. Channel-level mute keeps the UX silent. |
| `customDisplayBundle.nowBarData.cardColorStart` / `cardColorEnd` (ARGB) | `TimerService.applySamsungNowBarExtras` | Chip background gradient. Sysui reads these on the rich `ConvertingNowBarData` path. |
| `customDisplayBundle.nowBarData.expandViewColorStart` / `expandViewColorEnd` | same | Expanded view gradient. Note: NOT `expandColorStart/End` — that typo silently no-ops. |
| `customDisplayBundle.nowBarData.cardChronometerRemoteView` (RemoteViews wrapping `<Chronometer android:tag="aod_chronometer">`) | `TimerService.buildChronometerRemoteView` + `res/layout/nowbar_chronometer.xml` | Live-ticking text. Sysui's `findViewWithTag("aod_chronometer")` is the gate. |
| `customDisplayBundle.nowBarData.cardIconLeft` + `queIcon` (mirror) | `TimerService.applySamsungNowBarExtras` | Phase-coloured circle bitmap. Both keys must be set; sysui reads one or the other depending on surface. |
| `customDisplayBundle.enableNowBar = true` | same | Gate flag inside the customDisplayBundle. |
| `forceAutoResume = true`, `ambientImmediateExpire = true` | same — siblings of `customDisplayBundle` on outer extras | Stock Timer ships these; safer to mirror. |
| `POST_NOTIFICATIONS` granted | runtime, requested by `MainActivity` | On Wear OS 4+ no permission ⇒ no chip. |

### What our notification does NOT ship (and why bringing it back breaks things)

| Anti-requirement | What it would do |
|---|---|
| `OngoingActivity.Builder` — anything that injects `androidx.core.ongoing.*` extras | Routes the notification through legacy `OANowBarController` (grey chip, icon-only). The whole point of v1.21 was removing this. |
| `setUsesChronometer(true)` / `setChronometerCountDown(true)` | System chronometer is the legacy ticking mechanism. The new path ticks via the Chronometer view inside our RemoteViews; system chronometer is redundant and adds extras that may bias routing. |
| `NotificationCompat.ProgressStyle` / `setRequestPromotedOngoing(true)` / `setShortCriticalText(...)` | Android 16 Live Updates API. Adds yet more extras and routes via Promoted Ongoing path; not required for Galaxy Watch's customDisplayBundle path. Also: API gates on SDK 36, was extra surface area. |
| `setContentTitle` / `setContentText` / `setSubText` | Stock Samsung Timer leaves all three null. Populating them shifts our extras keyset away from the working baseline. |
| `setLocusId(LocusIdCompat(...))` | Was load-bearing on the legacy OngoingActivity path. Not needed on the customDisplayBundle path; sysui doesn't read it. |
| `setColor(...)` + `setColorized(true)` | Drove the legacy path's tinted-icon variant. The new path takes its colour from `cardColorStart/End`; `setColor` is ignored. |
| `notificationManager.cancel(NOTIFICATION_ID)` followed by `notify(NOTIFICATION_ID, n)` | Stock Samsung Timer does this, but they're a privileged system app. On a third-party app, cancelling the FGS notification on Android 14+ tears down the foreground state and the next `notify()` arrives as a regular notification — sysui drops it from Now bar. **Use bare `notify()` for FGS updates.** |
| `manifest <meta-data android:name="com.samsung.android.support.ongoing_activity">` | Was a guess from the One UI 7 phone-side reverse-engineering. Watch sysui doesn't read it. |

### Why the chip turned green (the breakthrough)

The grey chip pre-v1.21 was *not* signature-gated, it was *shape-gated*. Sysui's notification router:

```
if (extras has "androidx.core.ongoing.NAMESPACE") → OANowBarController (legacy, grey chip)
else if (extras has "customDisplayBundle")        → ConvertingNowBarData (rich path, coloured chip)
else                                              → default
```

We had been adding **both** `OngoingActivity.Builder` *and* `customDisplayBundle`, hoping at least one would render. The first one branched the router away from the second. Subtracting `OngoingActivity.Builder` (and dragging out everything OngoingActivity-adjacent: chronometer, ProgressStyle, locusId, setColor) was the fix. Verified by `dumpsys notification --noredact` diff against the stock Samsung Timer's live notification — the stock app uses *only* `customDisplayBundle`, no OngoingActivity at all.

Confirm a healthy v1.21+ on a fresh device:
1. `dumpsys notification --noredact | awk '/sermontimer/,/^$/'` → no `androidx.core.ongoing.*`, no `android.template`, `customDisplayBundle=Bundle(... dataSize≈5028)`.
2. `logcat -d | grep ConvertingNowBar` → `setBackgroundGradient(... card BG startColor: -10044566 ...)` lines (decode the int via `python3 -c "print(hex(-10044566 & 0xFFFFFFFF))"` — should match `phaseAccent` for the current phase).
3. No `Could not fetch the chronometer` errors in logcat.
4. Pixel sample at `(160, 400)` — should be the phase accent ARGB ±5 per channel.

### Sources used (snapshot)

- **Stock Samsung Timer (`com.samsung.android.watch.timer` / `TimerWatch.apk`)** — primary baseline. Pull from `/system/priv-app/TimerWatch/TimerWatch.apk`. The contract was reverse-engineered from this, not from any documentation. See section 18 for the live-RE recipe.
- **Galaxy Watch sysui (`com.samsung.android.wearable.sysui`)** — pulled from `/system/system_ext/priv-app/SecClockworkSysUi/`. `dexdump -d classes.dex` then grep `cardChronometerRemoteView`, `findViewWithTag`, `setBackgroundGradient`, `Could not fetch` to find the consumer-side contract.
- [Display ongoing activities — Wear OS](https://developer.android.com/training/wearables/notifications/ongoing-activity) — mostly aspirational for our use case. We don't follow this anymore but referencing it explains *why* we used to.
- [Live Notifications and Now Bar in Samsung One UI 7 (Akexorcist, 2025-07)](https://akexorcist.dev/live-notifications-and-now-bar-in-samsung-one-ui-7-as-developer-en/) — phone-side reverse-engineering. Note: phone-side `android.ongoingActivityNoti.*` extras are NOT what watch sysui reads. Useful as historical context, not as a guide.
- [Progress-centric notifications — Android 16](https://developer.android.com/about/versions/16/features/progress-centric-notifications) — for the day Wear OS adopts ProgressStyle as the canonical chip API. Until then, customDisplayBundle is what Galaxy Watch actually reads.

---

## 18) Live-RE recipe — copy a working stock app's notification

The single highest-leverage debugging move when chip rendering doesn't match expectations: capture the **live** notification of a working stock app via `dumpsys`, diff against ours. Documentation lies; the live `extras` Bundle does not.

```bash
# 1. Identify the working baseline package
adb -s $DEV shell pm list packages | grep -i timer    # find the stock Timer / Stopwatch / Calendar / etc.

# 2. Drive it to publish its notification
adb -s $DEV shell am start -n com.samsung.android.watch.timer/.activity.TimerHomeActivity
adb -s $DEV shell uiautomator dump /sdcard/ui.xml      # find the Start button
adb -s $DEV shell input tap <x> <y>                    # tap Start
sleep 3                                                 # let the notification post

# 3. Snapshot the live notification (NOT a static decompile — that misses runtime decisions)
adb -s $DEV shell "cmd notification list"              # confirm the key, e.g. 0|com.samsung.android.watch.timer|1602|null|10046
adb -s $DEV shell dumpsys notification --noredact > /tmp/stock.txt
awk '/NotificationRecord.*com\.samsung\.android\.watch\.timer/,/^[[:space:]]*$/' /tmp/stock.txt > /tmp/stock-rec.txt

# 4. Snapshot ours under the same conditions
adb -s $DEV shell am start -n com.example.sermontimer/.presentation.MainActivity
# (start our timer the same way)
adb -s $DEV shell dumpsys notification --noredact > /tmp/our.txt
awk '/NotificationRecord.*com\.example\.sermontimer/,/^[[:space:]]*$/' /tmp/our.txt > /tmp/our-rec.txt

# 5. Diff the extras keysets
diff <(grep -oE '^[[:space:]]+[a-zA-Z][a-zA-Z0-9.]+=' /tmp/stock-rec.txt | sort -u) \
     <(grep -oE '^[[:space:]]+[a-zA-Z][a-zA-Z0-9.]+=' /tmp/our-rec.txt | sort -u)
```

What to read out of the diff:

- **Keys present in ours but not in the stock baseline.** These are extras *we're* adding that the working app doesn't ship. Each one is a routing-suspect: it may be poisoning the Bundle and pushing us onto a different sysui code path. Subtract them one at a time and re-test.
- **Keys present in the baseline but not in ours.** These are extras the working app considers required. Most often this is `customDisplayBundle` (and friends), but sometimes you'll find vendor-specific extras like `forceAutoResume` that have no public documentation.
- **Bundle dataSizes that differ by an order of magnitude.** A 5 KB `customDisplayBundle` vs your 1 KB version typically means you're missing Parcelables (icons, RemoteViews) that fill out the working baseline.
- **`flags=` differences.** `ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE` vs `ONLY_ALERT_ONCE` may indicate different lifecycle expectations.
- **`category=` differences.** Stock apps choose categories deliberately for slotting. If yours differs, mirror theirs first.

After matching the shape, install + verify with `dumpsys notification` again to confirm your dump now looks like the baseline. Then verify behaviour visually (screenshot + pixel sample). If the dump matches the baseline but the visual still doesn't, the producer-side contract is right and the bug is somewhere else (e.g. layout view tag — see section 14b's `aod_chronometer` landmine).

### Pulling and decompiling the stock APK (when the diff isn't enough)

```bash
adb -s $DEV shell pm path com.samsung.android.watch.timer
# → package:/system/priv-app/TimerWatch/TimerWatch.apk
adb -s $DEV pull /system/priv-app/TimerWatch/TimerWatch.apk /tmp/stock-timer/

cd /tmp/stock-timer
unzip -q -o TimerWatch.apk -d unpacked
$ANDROID_HOME/build-tools/*/dexdump -d unpacked/classes.dex > timer.dexdump

# Find every notification-extras key the stock app sets:
grep -E 'const-string v[0-9]+, "[a-zA-Z]' timer.dexdump | sort -u | grep -iE 'card|chrono|nowbar|expand|enable|force|ambient' | head -40
```

This is how we discovered the full key list (`type`, `cardIconLeft`, `queIcon`, `cardContents`, `cardChronometerRemoteView`, `expandViewIcon`, `expandChronometerRemoteView`, `expandChronometerPosition`, `cardColorStart/End`, `expandViewColorStart/End`) and the ordering inside `applySamsungNowBarExtras`.
