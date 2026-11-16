# MASTER PROMPT: Build "Cube Editor" – a 3D Kinetic Typography Lyrics Video Editor for Android

You are a senior mobile engineer, 3D/graphics engineer, audio/video engineer and product designer in one. Build the complete Android app described below, end to end, from zero to a working release APK. Read the whole brief before you write any code. Where the brief is ambiguous, choose the most reasonable option, state your choice in one line, and continue. Do not ask me questions unless something is truly blocking.

---

## 1. PRODUCT VISION

**Cube Editor** is a mobile video editor (CapCut-style) specialised in one signature style: **3D kinetic typography lyric videos**. The user builds one or more 3D boxes (cubes). Each face of a cube carries colourful, differently-sized words. Synced to a song, the cube rotates, tumbles, zooms and tilts on the beat of each lyric line, so the next face (with the next lyric text) comes to the front. The result is a vertical (9:16) video with a black or custom background, motion blur on fast movement, music, and a credit/watermark in the corner. The user exports it as an MP4 and posts it on Instagram Reels, TikTok or YouTube Shorts.

The reference video style (what the output must be able to reproduce):
- Black background, one 3D cube centred in a 9:16 frame.
- Each face is packed with stacked words of different colour (white, blue, green, magenta, yellow, red, orange, cyan), size and font weight, tightly arranged so the face looks like one solid typographic block.
- At every lyric line the cube rotates (left, right, up, down, diagonal) with an ease-in-out curve, so a new face lands in front. During rotation it also scales up slightly, tilts on the Z axis and shows motion blur.
- Text is physically part of the cube faces: it rotates, foreshortens and shows perspective with the cube. It is never a flat overlay.
- A small credit (icon + handle, e.g. "@name") sits in a corner; at the end it can scale to the centre as an outro.
- Audio is the song; rotations are tightly synced to the audio clock.

The app must let a non-technical user recreate this style, fully offline, on a mid-range Android phone.

---

## 2. TARGET PLATFORM AND TECH STACK

- **Platform:** Android only for now (minSdk 24, targetSdk 34 or higher). Keep the code portable so iOS can be added later.
- **Framework:** Flutter (stable channel), Dart 3.x, Material 3, null-safety.
- **State management:** Riverpod (or Bloc – pick one and use it everywhere). Immutable state classes, no business logic inside widgets.
- **Persistence:** JSON files in the app documents directory via `path_provider` (project files), plus `shared_preferences` for small settings. Optionally Drift/SQLite for the project index.
- **Audio:** `just_audio` for playback; read the player position stream as the **master clock**. Use `on_audio_query` (or an equivalent maintained package) to list on-device songs.
- **Waveforms:** extract peaks once per song in an isolate (e.g. with `just_waveform` or an ffmpeg-based extraction), cache the result to disk per song.
- **3D rendering:** Build a custom, lightweight 3D engine on Flutter `CustomPainter`/`Canvas.drawVertices` or Flutter GPU/Impeller fragment shaders. The preview must NOT depend on heavy game engines. Required: perspective camera, per-face 4x4 matrix transforms, back-face culling, depth sorting (painter's algorithm per face, sorted by camera-space Z), texture-mapped faces (each face is rendered to an offscreen `ui.Image` and mapped as a textured quad using `drawVertices` with a perspective-correct approach, or via Matrix4 on a `Transform` widget with `Matrix4.setEntry(3,2,perspective)`).
- **Export:** Offline frame rendering → encode with FFmpeg. Because `ffmpeg_kit_flutter` was retired, use a maintained community fork or a prebuilt FFmpeg binary wrapped through a platform channel; document exactly which package/version you chose and why. As a fallback, use Android `MediaCodec` + `MediaMuxer` through a Kotlin platform channel (frames in → H.264 MP4 out, audio muxed in). 
- **Fonts:** bundle at least 12 free (OFL) display fonts in `assets/fonts/` – condensed bold sans, heavy grotesque, serif display, handwriting, mono, a Devanagari-capable font and an Arabic/Urdu-capable font (e.g. Noto family) so Hindi/Urdu/Hinglish lyrics render correctly.
- **Build/CI:** GitHub Actions workflow that (1) sets up Java 17 and Flutter stable, (2) generates the `android/` folder only if missing, (3) patches `AndroidManifest.xml` with needed permissions, (4) patches Gradle `minSdkVersion` if required, (5) runs `flutter pub get`, `flutter analyze` and `flutter build apk --release`, (6) uploads the APK as an artifact. The build must pass with zero manual edits to native folders.
- **No network dependency:** the app works fully offline. No analytics, no ads, no account.

---

## 3. NON-NEGOTIABLE ENGINEERING RULES

1. The code must compile and `flutter build apk --release` must succeed. Pin every dependency version in `pubspec.yaml`. Do not use deprecated or discontinued packages without stating the replacement.
2. **One master clock.** The audio player position is the single source of time. The timeline playhead, cube animation, lyric highlight and karaoke text are all pure functions of `timeMs`. Rendering at time T must be deterministic: `renderFrame(project, T)` always gives the same image. This is what makes seek, scrub and export correct.
3. **Animation = data, not controllers.** Do not drive the cube with `AnimationController`s. Compute the cube pose from the keyframe list for any `timeMs` (interpolation), so seeking works instantly.
4. All project data is serialisable to JSON with a `schemaVersion` field and migration hooks.
5. Heavy work (waveform extraction, thumbnail generation, export) runs in isolates or native threads; the UI thread stays at 60 fps.
6. Never crash on permission denial, missing files, corrupt projects, or low memory. Show friendly messages and recovery options.
7. Output **complete files**, never fragments or "rest is the same". Every file path must be stated above its code block.
8. Write clean, documented code with unit tests for the animation/interpolation maths, the project (de)serialisation, and the text-fit algorithm.
9. Accessibility: touch targets ≥ 48 dp, sufficient contrast, scalable text in UI chrome (not in the video canvas).

---

## 4. APP STRUCTURE (SCREENS AND NAVIGATION)

### 4.1 Splash / Onboarding
- Short splash with logo. First launch only: 3-card onboarding (Build the cube → Add lyrics + music → Export), skippable.

### 4.2 Home screen
- App bar with logo, Settings icon.
- Big primary button **"New Project"**.
- Below it **"Recent Drafts"**: grid or list of project cards, newest first. Each card: thumbnail (rendered poster frame of the cube), project name, last-edited date, duration, small music icon if a song is attached.
- Long-press (or ⋮ menu) on a card: Rename, Duplicate, Delete (with confirm), Export again.
- Empty state with an illustration and a short hint.
- Optional **Templates** row: 3–5 built-in starter projects (e.g. "4-face lyric cube", "Tumble intro", "Beat pulse") that the user can open and replace the text in.

### 4.3 New Project dialog
- Project name, aspect ratio (9:16 default, 1:1, 16:9, 4:5), resolution (720p / 1080p), frame rate (30 / 60), background (black default, solid colour, gradient, image, video).

### 4.4 Editor screen (the core). Vertical layout from top to bottom:
1. **Top bar:** Back (auto-saves), project name, Undo, Redo, Export button.
2. **Preview canvas:** the 9:16 stage showing background + cubes + overlay text + credit. Safe-area guides toggle. Pinch to zoom the camera, one-finger drag to rotate the selected cube group, two-finger twist to rotate Z.
3. **Transport bar:** Play/Pause, current time / total time, loop toggle, jump to previous/next lyric, jump to previous/next keyframe, playback speed (0.5×, 1×).
4. **Timeline** (multi-track, see §8).
5. **Bottom toolbar** (horizontally scrollable): Music, Text, Fonts, Color, Add Box, Lyrics, Keyframe, Motion, Background, Credit, Export-settings. Selecting a tool opens a **bottom sheet** (never a full-screen page) with that tool's controls.

### 4.4.1 Music picker screen
- Requests the audio permission (Android 13+: `READ_MEDIA_AUDIO`; Android ≤12: `READ_EXTERNAL_STORAGE`). If denied, show an explanation and a button to retry or open system settings.
- Lists all on-device songs: art, title, artist, duration; search box; sort by date added / title / artist; folders/albums tabs.
- Tapping a song plays a 20–30 s preview with Play/Pause and a seek bar; **"Use this song"** adds it to the project.
- Also **"Import from files"** (file picker) for audio not in the media library, and optionally "Extract audio from a video".
- The project stores the file URI/path, title, artist, duration, and a content hash so it can warn if the file moves.

---

## 5. THE 3D CUBE ENGINE (core feature)

### 5.1 Scene model
- A **scene** contains N cubes arranged as a tree (parent → children). Each cube has: `id`, `name`, `size`, local position, local rotation (quaternion or Euler X/Y/Z), local scale, six faces (front, right, back, left, top, bottom), `parentId?`, `parentFace?`, `visible`, `opacity`.
- The default new cube shows four side faces first; top/bottom faces are also supported and hidden behind a "Show top & bottom" toggle for simple users.
- **Camera:** perspective, configurable field of view and distance; camera zoom/offset are also keyframe-able.

### 5.2 Faces
Each face has:
- `backgroundColor` (default black or solid colour; also supports transparent so only text shows, like the reference video), optional gradient, optional border/outline.
- Four **corner anchors**: top-left, top-right, bottom-left, bottom-right, plus a "free layout" mode (see 5.3).
- A list of **text items** (unlimited).
- Per-face default font and text colour.

### 5.3 Text on faces (many words per face, per corner)
- User picks a face, taps **Text**, types a word or phrase, chooses the starting **corner** with a small 4-corner picker. Then arrow buttons (← ↑ → ↓) let the user move the text to another corner. Any number of texts can live in the same corner; they stack in order and can be reordered by drag.
- Each text item: content, font family, weight, size, colour, letter spacing, line height, horizontal squeeze/stretch (condensed look), skew, uppercase toggle, outline/stroke, shadow, rotation (90° steps and free), and opacity.
- **Free layout mode:** drag, pinch-resize and rotate individual words directly on the face to build the packed typographic block seen in the reference.
- **Auto Fit:** one button that takes all words on a face and automatically scales and arranges them (rows of different sizes, justified to the face edges, with small consistent gaps) so they fill the face like a poster. Provide at least three auto-layout styles (stacked, grid, random-poster). Implement with a deterministic algorithm (e.g. greedy line-breaking plus per-line scale-to-width) and unit-test it.
- **Per-word colour palette:** one-tap "Randomise colours from palette" using a user-selectable palette (white, blue, green, magenta, yellow, red, orange, cyan by default).
- **Text is glued to the face:** it is rendered into the face's texture, so any cube move (rotate, tilt, scale, parent motion) moves text with it, with correct perspective. The text must never appear mirrored or upside down when its face is front-facing in the default orientation.
- Tapping text on the preview selects it; handles appear for move/scale/rotate; a context mini-bar offers Edit, Duplicate, Delete, Bring to front.

### 5.4 Face colours
- **Color tool:** choose the target (this face / selected faces / all four sides / entire cube / all cubes) and then pick a colour (swatches, HSV wheel, hex input, eyedropper from preview, recent colours, saved palette). Each side can have a different colour or all can share one.
- Also text colour per word, and a "tint all text" option.

### 5.5 Add Box (multiple cubes, linked together)
- **Add Box** is always visible. It is highlighted when all four corners of the current face are filled, as a hint, but it works any time.
- Flow: tap Add Box → choose which face of the **selected cube** the new cube attaches to (interactive mini cube picker: Front / Right / Back / Left / Top / Bottom) → the new empty cube snaps flush against that face.
- **Locking (parent–child):** the new cube becomes a child of the selected cube (`parentId`, `parentFace`). The child's world matrix = parent world matrix × attachment offset × child local matrix. When the parent rotates, moves or scales, every child follows rigidly, like Lego bricks. Chains (A → B → C) and trees (one parent with several children on different faces) are supported.
- Optional **Hinge mode** for a child: instead of rigid lock, the child may rotate around the shared edge by its own keyframes (for folding-box effects).
- **Unlink** (detach and keep world position), **Re-attach** to another face/cube, **Delete** (ask whether to delete children too), **Duplicate** (with or without children).
- A **cube list** chip row (Cube 1, Cube 2 …) lets users select cubes without tapping them. Selected cube gets an outline glow. Show/hide and lock/unlock per cube.
- Prevent attach cycles and overlapping attach faces (a face already used by another child shows as occupied).
- Rendering handles many cubes: sort faces globally by camera-space depth; cull back faces; keep ≥ 30 fps preview with at least 6 cubes of 4–6 faces each. Offer a "Preview quality" setting (Low = lower texture resolution, no blur).

### 5.6 Touch control of the cube
- One-finger drag: rotate the selected cube (and its children) around X/Y (trackball-like, with optional axis lock chips: Free / X / Y / Z).
- Two-finger pinch: scale; two-finger twist: Z rotation; two-finger drag: move.
- Double-tap a face: snap-rotate so that face is front, with an easing animation.
- Optional gyroscope preview (tilt the phone to look around), off by default.
- Haptic tick when a face snaps exactly to front.

### 5.7 Presets for cube motion
Provide one-tap **Motion Presets** that generate keyframes automatically for a selected time range or lyric: Spin Right, Spin Left, Flip Up, Flip Down, Diagonal Tumble, Barrel Roll, Bounce-In, Punch Zoom, Shake, Whip Pan, Slow Drift, Random Tumble. Each preset has duration and intensity sliders.

---

## 6. LYRICS SYSTEM (rotate the cube on each lyric)

### 6.1 Entering lyrics
- **Type** lines manually; **Paste** a full lyric (each line becomes a `LyricLine`); **Import .lrc / .srt** (parse timestamps such as `[01:23.45]`, including enhanced word-level LRC if present); optional **Import from .txt**.
- Hinglish, Hindi (Devanagari) and Urdu (RTL) must render correctly, including shaping and proper line direction.

### 6.2 Timing
- **Tap-to-Sync mode:** song plays, upcoming line shown large; user taps a big button exactly when the line starts; the tap time becomes `startMs`. Support "tap on word" for word-level timing as an advanced option. Allow slowing the song to 0.75× while syncing.
- Timeline lyric bars can be dragged to move, edge-dragged to resize, split at the playhead, merged, and nudged ±10/50/100 ms with buttons.
- **Auto-align (optional, later phase):** if feasible, note it as a future item (needs speech alignment); do not fake it.
- **Beat detection:** analyse the song once (energy-based onset detection in an isolate) and show beat markers on the music track; a **Snap to beat** toggle makes lyric starts and keyframes snap to the nearest beat. Provide "Auto-generate lyric timing from beats" as a rough helper only.

### 6.3 Per-lyric cube control
Selecting a lyric opens a sheet with:
- **Which cube** (chips) and **which face must be front** when this lyric is on screen.
- **Rotation direction:** Left, Right, Up, Down, Diagonal, Auto-shortest-path, Random.
- **Rotation amount:** 90°, 180°, 270°, 360°+ extra spin (e.g. 90° + 360° spin).
- **Timing:** rotation start offset (relative to the lyric start, e.g. −100 ms to anticipate the beat), rotation duration (100 ms – 2000 ms), hold behaviour.
- **Easing:** linear, ease-in, ease-out, ease-in-out, cubic-bezier editor, back/overshoot, bounce, elastic.
- **Extra motion during rotation:** zoom (scale peak), Z-tilt, camera shake, motion blur amount.
- **Show lyric text where:** (a) already on the cube face (face text is the lyric), (b) generate the text automatically on the target face using Auto Fit, (c) karaoke caption overlay at the bottom (non-3D), or none.
- **Apply to all lines:** copy this setting to all lyrics or to a selection.
- **Auto Rotate:** one button assigns the next face to each successive lyric in order (Front → Right → Back → Left → repeat) and can alternate direction/axis for variety; every assignment stays editable.
- A lyric's settings compile into keyframes (see §7). Manual keyframes override generated ones when they overlap, and the UI marks overridden regions.

### 6.4 Karaoke overlay (optional layer)
- Show the current lyric (and optionally the next) as a 2D caption with word-by-word highlight. Style options: font, size, colours, position, background pill. Toggleable per project.

---

## 7. KEYFRAME AND ANIMATION ENGINE

- **Keyframe tool:** with the playhead at time T, user poses the cube (rotate/scale/move/camera) then taps **Keyframe** to record a diamond ◆ at T on the Cube track. Between keyframes the engine interpolates with the chosen easing.
- Animatable properties per cube: rotation (use quaternion slerp to avoid gimbal lock; Euler is just a UI view), position X/Y/Z, uniform scale, opacity, face colours (optional), plus **camera** properties (zoom, FOV, offset, roll) and **global** properties (background colour/zoom, blur amount, shake).
- Keyframe editing: tap to select, drag in time, multi-select with box or long-press, copy/paste at playhead, delete, change easing per segment, value editor with numeric fields, and **hold** (stepped) interpolation.
- **Graph editor (advanced, later phase):** curves for each property with Bézier handles.
- Interpolation function signature must be pure: `CubePose poseAt(List<Keyframe>, int timeMs)`. Unit-test it for edge cases (before first key, after last key, equal timestamps, shortest-path rotation, multi-turn spins).
- Loop and mirror helpers: "Repeat this motion every N beats".
- **Motion blur parameters** are keyframe-able (shutter angle).

---

## 8. TIMELINE (CapCut-style)

- Horizontally scrollable, with the **playhead fixed at the centre** and the content moving under it; tap the ruler to seek; drag to scrub (audio scrubbing optional).
- Time ruler with adaptive tick spacing; **pinch to zoom** (from ~10 ms precision to a full song view); a "fit to project" button.
- Tracks (each collapsible, with lock/mute/visibility toggles):
  1. **Video/Background track** (colour, image or video clip) – later phase for video.
  2. **Cube track(s):** one per cube – keyframe diamonds and generated-motion segments.
  3. **Lyrics track:** coloured bars with text preview.
  4. **Text/Overlay track:** credits, captions, stickers.
  5. **Music track:** waveform, beat markers, trim handles, fade handles, volume line.
- Clip operations: select, drag to move, edge-drag to trim, **split** at playhead, duplicate, delete, snap to playhead/beat/clip edges with magnetic feedback and haptics.
- Volume per clip and master; fade in/out; the music track also supports a second audio clip (voice-over or SFX) in a later phase.
- Undo/Redo covers every timeline operation (command pattern, at least 100 steps).
- Project duration = max(music end, last lyric end, last keyframe) with an editable manual end-time.

---

## 9. VISUAL EFFECTS

- **Motion blur:** physically motivated – blur strength follows the angular/linear velocity of the cube between frames (shutter angle slider 0–360°, default 180°). Preview: cheap approximation (directional blur shader or short trail, optional). **Export: true accumulation** – render N sub-frames per output frame (N = 8 default, 16 high-quality) at evenly spaced sub-times and average them, so the result has no ghosting bands.
- Glow, chromatic aberration (RGB split on beats), vignette, film grain, and a screen-shake effect, each with intensity and beat-sync toggles (all optional, off by default).
- **Beat pulse:** subtle scale pulse on every detected beat.
- **Intro/Outro:** "Outro credit" animation where the corner credit scales to the centre during the last seconds. Fade-in/out of the whole video.
- **Background:** black (default), solid, gradient, image, or looping video; optional blur/dim for readability.
- **Credit/Watermark layer:** an icon (preset social icons + custom PNG) and a handle text such as "@name", with a second smaller line for artists/credits (e.g. "Artist1, Artist2 • Song"). Position (any corner or custom), size, opacity, and an optional outro scale-to-centre animation.

---

## 10. EXPORT PIPELINE

- **Export sheet:** resolution (720×1280, 1080×1920, 1:1, 16:9), fps (30/60), quality (Draft/Standard/High), motion-blur quality (Off/8/16 sub-frames), include audio, audio bitrate, range (full project or selected region), and filename.
- Pipeline: for each output frame `i` at time `t = i / fps`, evaluate the project deterministically, render sub-frames offscreen (`ui.PictureRecorder` → `toImage` → raw RGBA), accumulate/blur, and stream frames to the encoder. Use a bounded queue so memory stays flat. Run in the background with a foreground service/notification so it survives the screen turning off.
- Audio: trim/offset/fade/volume applied via FFmpeg filters (`atrim`, `adelay`, `afade`, `volume`) or native MediaCodec; mux into the MP4 (H.264 + AAC).
- Progress UI with percentage, ETA, cancel; on success: Share sheet (Instagram, WhatsApp, YouTube…), Save to gallery (`MediaStore`, Movies/CubeEditor), and "Open".
- Handle failure cleanly (low storage, encoder unavailable): message + retry with lower settings.
- Add a **Fast preview export** (low-res, no blur) for quick checking.

---

## 11. PROJECT FILE FORMAT AND STORAGE

- Folder per project: `project.json` (schemaVersion, settings, cubes tree, faces, text items, lyrics, keyframes, music clip refs, background, credit, export settings), `thumbnail.png`, and a `cache/` folder (waveform peaks, beat markers, face textures).
- **Auto-save** with a 1 s debounce after any change, plus save on app pause. Atomic writes (write temp file then rename) to avoid corruption. Keep the last 3 backups.
- **Import/Export project** as a `.cubeproj` zip (project.json + embedded fonts/images, not the copyrighted song unless the user opts in).
- Missing media (song moved): detect on open and offer "Relink".

---

## 12. SETTINGS SCREEN

Theme (dark default, AMOLED black option), language (English / Hindi / Hinglish UI), default export settings, preview quality, haptics on/off, auto-save interval, storage usage with "Clear cache", "About", "Privacy" (state clearly: all processing is on-device, no data is uploaded), and a "Report a problem" that copies debug info to the clipboard.

---

## 13. UI / UX DESIGN SYSTEM

- Dark, premium, minimal. Background `#0B0B12`, surfaces `#15151F` / `#1E1E2B`, accent violet `#6C5CE7`, secondary cyan `#00D2FF`, success `#00B894`, danger `#D63031`. Define all tokens in one `AppTheme` and use them everywhere.
- Typography for the UI chrome: one clean sans (e.g. Inter/Poppins); canvas typography is separate.
- Iconography: consistent rounded icon set; toolbar buttons = icon + 11–12 sp label.
- Motion: sheets slide up with 200–250 ms ease-out; button presses give a subtle scale + haptic; no gratuitous animation inside the editor.
- Layout rules: preview gets the largest share of the screen; timeline height adjustable by dragging a handle; tools open as draggable bottom sheets (peek / half / full); one-handed reachability (primary actions in the bottom 40%).
- States for everything: loading skeletons, empty states with guidance, error banners with retry, disabled states with tooltips.
- First-use coach marks on the editor (dismissible, shown once).
- Landscape support is not required; lock to portrait in the editor.

---

## 14. PERFORMANCE AND QUALITY TARGETS

- Preview: ≥ 30 fps on a mid-range phone (e.g. Snapdragon 6-series, 4 GB RAM) with 4 cubes; auto-degrade quality when frames drop.
- Cold start < 2.5 s; opening a project < 1 s; timeline scroll/zoom at 60 fps.
- Audio–visual sync error < 1 frame (< 33 ms) in preview and exact in export.
- Memory: face textures cached by hash and re-rendered only when text/colour changes; release GPU images on dispose.
- Export 30 s @ 1080×1920, 30 fps, 8 sub-frames in reasonable time on a mid-range phone; show an honest ETA.
- APK size target < 60 MB (fonts subsetted where possible).

---

## 15. TESTING AND VERIFICATION

- Unit tests: keyframe interpolation, quaternion slerp/shortest path, parent–child matrix composition, depth sorting, text Auto Fit, LRC parsing (including Devanagari), JSON round-trip with migrations, beat snapping.
- Golden/widget tests for the cube painter at fixed times (front-facing face must be upright and readable; text must not mirror).
- An integration test that creates a project, adds 4 lyrics, applies Auto Rotate and verifies the pose at each lyric start has the expected face in front.
- Include a manual QA checklist at the end of every phase.

---

## 16. DELIVERY PLAN (build in phases; stop after each phase and wait for my test result)

**Phase 1 – Foundation:** project structure, theme, navigation, data models (full schema from §11), JSON persistence, Home screen with real drafts, New Project dialog, auto-save. 
**Phase 2 – Cube engine:** 3D renderer, camera, one cube with 4 faces, drag-rotate, snap-to-face, face colours, depth sorting, face-texture caching. 
**Phase 3 – Text on faces:** corner anchors, arrows to move corners, multiple texts per corner, Auto Fit, free layout, fonts (including Devanagari/Urdu), per-word colours. 
**Phase 4 – Music + timeline:** music picker with permission flow, real playback, master clock, waveform, timeline (zoom, playhead, trim, move, volume, fades), transport bar. 
**Phase 5 – Add Box:** parent–child cubes, face attach picker, rigid locking, chains/trees, unlink/re-attach/delete/duplicate, cube list. 
**Phase 6 – Keyframes + lyrics:** keyframe recording/editing, interpolation engine, lyrics entry (type/paste/LRC), tap-to-sync, per-lyric cube control, Auto Rotate, motion presets, karaoke overlay, beat detection + snapping. 
**Phase 7 – Effects + credit:** motion-blur parameters, beat pulse, glow/shake, background options, credit/watermark with outro animation. 
**Phase 8 – Export:** deterministic offline renderer, sub-frame motion blur, encoder, audio mux, progress/cancel, share/save to gallery. 
**Phase 9 – Polish:** undo/redo everywhere, templates, onboarding, settings, performance tuning, error handling, accessibility, release signing instructions, README.

---

## 17. HOW TO RESPOND

1. Start with a short architecture summary: folder structure, packages (with versions) and the key technical decisions (3D approach, encoder choice) with one-line reasons.
2. Then deliver **Phase 1 only**: every file, complete, with its path. Include `pubspec.yaml`, the full GitHub Actions workflow, and any manifest/Gradle patch steps.
3. End each phase with: (a) what is done, (b) what is intentionally not done yet, (c) a numbered manual test checklist, (d) known limitations, (e) the exact next phase.
4. If something in this brief is technically impossible or too risky with the chosen packages, say so plainly, propose the best alternative, and continue. Do not silently drop a feature and do not fake it.
5. When I report a bug, reply with the root cause and the corrected full files only.

Begin now with the architecture summary and Phase 1.
