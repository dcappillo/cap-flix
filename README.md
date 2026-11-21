# Cap Flix

Derek's own Netflix for the movies and shows on his PC. The same setup as Cap
Tunes: **Jellyfin** on the PC plays the part Navidrome plays for music,
**Tailscale** keeps it private, and this Android app is the front end, with
**libVLC** (VLC's engine) doing the playback.

```
F:\Movies, F:\TV  ──>  Jellyfin on "derek" (port 8096)  ──Tailscale──>  Cap Flix on the phone
                       matching, artwork, progress,                    browse, play anything VLC plays,
                       4K -> phone-size conversion on the RTX 3070     resume, next episode, PiP
```

---

## Step 1: Clean up the library (once)

Use `Documents\CapFlix\organizer`. Its README has the steps. Run `1_PLAN.bat`, read
the report, then run `2_APPLY.bat`. Do this **before** adding the folders to Jellyfin,
so Jellyfin only ever sees the clean names.

## Step 2: Install Jellyfin on the PC

1. Download the Windows installer from **jellyfin.org/downloads/windows** (the stable
   release, not "unstable"). If Windows Smart App Control blocks it, make sure the file
   came from jellyfin.org or GitHub, then allow it.
2. Choose **Basic Install**. It starts with Windows, like Navidrome does.
3. Open **http://localhost:8096**. The setup wizard runs:
   - Language: English.
   - User: `derek` with a **strong password**. This is what the app signs in with.
   - **Add Media Library**
     - Content type **Movies**, display name `Movies`, folder `F:\Movies`.
       Under *Movie options*, turn on **Automatically add to collection**.
       This gives you the Alien, Star Wars, and Fast & Furious collection rows.
     - Content type **Shows**, display name `TV Shows`, folder `F:\TV`.
     - Language **English**, country **United States**, for both.
   - Remote access: keep **Allow remote connections** on (Tailscale counts as remote).
     Leave automatic port mapping **off**. You never open a port on the router.
4. The first scan takes a while with a library this size. Let it run.

### Use the graphics card (RTX 3070)

**Dashboard → Playback → Transcoding**
- Hardware acceleration: **NVIDIA NVENC**
- Enable hardware decoding for: H264, HEVC, MPEG2, VC1, VP8, VP9, **HEVC 10bit**,
  **VP9 10bit**, **AV1**. The 30-series decodes all of these.
- Turn on **Enable hardware encoding**.
- Turn on **Allow encoding in HEVC format**. Phones play HEVC, and it needs about half
  the data of H.264.
- Turn on **Enable tone mapping**. This is what turns HDR and Dolby Vision into normal
  colors when a file has to be converted.
- Save. Jellyfin uses one GPU, so the 3070 alone is plenty. Reinstalling the 3080 is
  optional.

### Let the phone in (Windows Firewall)

Open PowerShell **as administrator** and run:

```powershell
New-NetFirewallRule -DisplayName "Jellyfin (Tailscale + home)" -Direction Inbound -Protocol TCP -LocalPort 8096 -RemoteAddress 100.64.0.0/10,LocalSubnet -Action Allow
```

This rule only lets in your Tailscale devices and your home network.

### Optional: Skip Intro

Dashboard → Plugins → **Repositories** → add `https://intro-skipper.org/manifest.json`.
Then go to **Catalog**, install **Intro Skipper**, and restart Jellyfin. It scans your
episodes in the background. When it's done, Cap Flix shows a **Skip Intro** button,
and the "Up next" card waits until the credits actually start.

### Optional: thumbnails while scrubbing
Library settings → **Trickplay** → enable image extraction. Not used by Cap Flix 1.0 yet.

## Step 3: Build the app

1. In Android Studio: **File → Open** and pick `Documents\CapFlix\CapFlixApp`.
2. Let Gradle sync. It uses the same versions as Cap Tunes (AGP 8.11.1, Kotlin 2.2.0,
   Gradle JDK 21), so most of it is already downloaded. If Studio offers to upgrade AGP
   or Kotlin, saying yes is fine.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, then copy
   `app\build\outputs\apk\debug\app-debug.apk` to the phone and install it.
   Or plug the phone in and press **Run**.
4. The APK only includes 64-bit ARM, which every phone from the last several years uses.

If the build shows red errors, copy the text from the Build panel and send it to
Claude. This version was written without a compiler.

## Step 4: Sign in

- Turn Tailscale on.
- Server: `http://derek:8096`, or the PC's `100.x.x.x` Tailscale address with `:8096`.
- Username and password: the Jellyfin account from Step 2.

---

## What's in 1.0

**Browsing**
- Home with a billboard (logo art, Play, My List, Info), Continue Watching with progress
  bars, Next Up, Recently Added, My List, "Because You Watched…", Top Rated Not Yet
  Seen, Collections, and rotating genre rows.
- Home / Movies / TV Shows switch at the top. See-all grids with sorting (recently added,
  A–Z, newest, top rated, surprise me).
- Title pages: backdrop, logo, rating, year, runtime, and badges (4K, HDR, Dolby Vision,
  5.1, Atmos). Also overview, cast, director, My List, Watched, and More Like This.
- Shows: season picker, episode list with stills, progress, and watched checks. Play
  starts the right episode (next up or resume).
- Movies kept in two versions (for example Alien's Director's Cut and Theatrical) get a
  **Version** picker.
- Search as you type, plus a genre directory.

**Playback (libVLC, the same engine as VLC)**
- Plays MKV, MP4, AVI, WMV, HEVC 10-bit, AV1, DTS, TrueHD/Atmos, and ASS, PGS and SRT
  subtitles: everything VLC plays.
- **Auto quality.** On Wi-Fi, the original file streams byte for byte. On mobile data,
  the RTX card shrinks it to 8 Mbps HEVC. You can change it per video or in Settings
  (Original, 40, 20, 10, 8, 4, or 2 Mbps).
- **Dolby Vision fix.** Some 4K files (Ahsoka, for one) are Dolby Vision Profile 5. Those
  show purple and green on phones without Dolby Vision, under any player. Cap Flix
  detects this and has the server convert them to normal color.
- If the original file won't stream, the app falls back to a converted stream.
- **Audio and Subtitles** panel. English audio is chosen automatically, which fixes the
  ITA/ENG releases that default to Italian. Subtitles default to "foreign-language parts
  only". Japanese audio for anime with English subtitles is one setting. Picture-based
  subtitles (PGS) can be burned in when converting.
- Resume where you left off. Progress syncs to Jellyfin every 10 seconds, so the PC and
  the phone stay in step.
- Skip Intro (with the plugin), an **Up Next** card with a 10-second countdown over the
  credits, and "Watch Credits".
- Double-tap the left or right side to jump 10 seconds back or forward. Drag up or down
  on the left for brightness and on the right for volume.
- Fit / Fill / Crop zoom, speeds from 0.75x to 2x, and picture-in-picture (press Home
  while a video plays).

## Ideas for later
- TV app (Google TV / Fire TV) with a remote-friendly interface.
- Profiles ("Who's watching?"). The same idea works in Cap Tunes.
- Downloads for offline viewing, trickplay thumbnails, Chromecast.
- A "Cap Flix Wrapped" year in review, built from Jellyfin's play history.

## Files
- `data/` talks to Jellyfin (`Jellyfin.kt`), the data models, and the saved sign-in and
  settings (`Session.kt`).
- `player/` contains `PlayerController` (direct vs converted playback, tracks, reporting,
  next episode), `PlayerScreen` (the controls), and `PlayerActivity` (full screen and PiP).
- `ui/` holds the screens: home, detail, browse, search, settings, and login.
- The Bungee wordmark font is bundled under the SIL Open Font License, the same as in
  Cap Tunes.
