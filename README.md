# Folio

A manga reader for Android. MangaFire is the main source and MangaDex is the fallback. Folio keeps itself
working when MangaFire changes.

Built with Jetpack Compose. Android 10 and newer.

## Sources

**MangaFire**: Folio doesn't scrape MangaFire itself. It loads Keiyoushi's MangaFire extension (the same one
Mihon uses) through its own implementation of the extension API (`eu.kanade.tachiyomi.*`, lib 1.6).

- **Installed privately.** Folio downloads the APK from Keiyoushi's store index into its own storage and
  loads it directly. There is no system install prompt and nothing else can use it.
- **Signature checked.** Folio only loads an extension signed with Keiyoushi's key
  (`9add655a…5d4da2`). It checks the key on install and again on every load.
- **Self-healing.** If a MangaFire call fails in a way that suggests the site changed, Folio checks the store
  straight away, installs the fixed build and retries the call. It also checks every 6 hours and at start-up.
- **Cloudflare and captchas.** Folio clears checks in a hidden WebView. When a check needs a tap, Folio shows
  it in a sheet and the waiting request continues once it passes. The extension's own captcha solver runs
  over Folio's UI too.
- **Its own settings.** The extension's preferences (content rating, volumes, captcha solver…) appear as
  native Folio settings.

**Comix** (optional): install it from Settings → Sources. It's Keiyoushi's English-only comix.to extension,
with the same signature checks and self-healing as MangaFire. Once installed, it appears in Discover and
Search.

**MangaDex**: built in. Folio talks to MangaDex's official public API directly, so MangaDex doesn't depend
on scraping or extensions. When MangaFire can't load something, Folio offers MangaDex instead:

- Discover and Browse switch to MangaDex in one tap.
- A title that fails to refresh shows its MangaDex match. **Move to MangaDex** carries your progress
  over by chapter number.
- If a chapter won't open, the reader can open the same chapter number on MangaDex.

If Keiyoushi ever moves to a newer extension API than Folio supports, Sources says so, and the Folio update
that adds support comes through the self-updater.

## Features

**Reader**
- Right-to-left, left-to-right and vertical pages, plus continuous scroll for webtoons. **Automatic**
  picks one from the genres and the page shapes, and you can set a different mode for each title.
- Zoom like the Photos app, with tiles loaded at full resolution for huge pages. Pinch-zoom works in
  scroll mode too.
- Tap the sides to turn pages, use the volume keys, or drag the page scrubber.
- The next chapter loads as you near the end, so you can keep swiping or scrolling straight into it.
- Reading progress is saved on every page, and the chapter is marked read at the end.
- Night filter, background colour, page size, page gaps, keep-screen-on and an immersive full screen.
- Bookmarks.

**Library**
- Cover grid with unread badges. Sort it, search it, and choose 2–4 per row.
- A Continue Reading shelf with a progress bar for each title.
- Background checks for new chapters (every 6–48 h, optionally Wi-Fi only) with notifications, and an
  **Updates** feed grouped by day.
- A **History** list where you can swipe to remove an entry.
- Offline downloads: one chapter, a selection, all unread, or new chapters automatically. Downloads can
  be deleted once read.
- Backup and restore of your library and progress to a JSON file.

**Browsing**
- **Discover**: a carousel of popular titles plus Popular and Latest shelves, with a health dot for each
  source.
- **Browse**: an endless grid with each source's own filters shown as native controls.
- **Search**: searches all your sources together, keeps recent searches, and opens pasted links.
  MangaFire and MangaDex links shared from other apps also open in Folio, as do Comix links once it's
  installed.

**Design**
- Cover-led: each title takes its accent and backdrop colours from its cover, so its page, buttons,
  reader controls and Continue card match the art.
- Title pages are posters: the cover full-bleed at the top, with the title set big over it.
- Archivo (condensed) for titles and headings, Inter for reading. Ink-black dark mode and a warm
  paper light mode.
- A floating dock for the tabs, spring animations, covers that fly between screens, and haptics.
- Five accent colours for everything that isn't a title.

**Updates**
- Every push to `main` builds a signed APK and publishes it as a GitHub release. Folio checks for new
  releases and installs them itself (Settings → About).

## Building

```sh
./gradlew assembleDebug          # installs beside release as dev.pridgey.folio.debug
./gradlew testDebugUnitTest      # unit tests, including the extension host contract
./gradlew recordRoborazziDebug   # renders the main screens to app/screenshots/ for design review
```

You need JDK 17+ (set `JAVA_HOME` if your default JDK is different) and Android SDK 37.

## Releases

`.github/workflows/build.yml` lints, tests and builds on every push and pull request. Each push to `main`
becomes the release `v1.0.<run number>`, and installed copies pick it up as an update.

Every release has to be signed with the same key or updates won't install. Run this once to create the key
and store it in the repo secrets:

```sh
scripts/setup-signing.sh
```

The key is kept in `~/keystores/folio/`. Back it up.

## How it works

- `eu/kanade/tachiyomi/`: the extension host API that Keiyoushi extensions link against. The shapes have
  to match lib 1.6 exactly: `HttpSource.headers$delegate`, the interceptor class names KeiSource checks for,
  and the synthetic default-argument constructors. `HostContractTest` pins these down.
- `source/ExtensionManager`: reads the store index, downloads extensions, verifies signatures, loads them
  with a `DelegateLastClassLoader`, and handles `heal()`.
- `source/SourceManager`: looks up sources, tracks their health and runs calls with self-heal-and-retry.
- `source/MangaDexSource`: the built-in MangaDex client.
- `data/`: Room database (titles, chapters, download queue), settings, and `LibraryRepository` (sync,
  progress, fallback moves, backup).
- `image/`: Coil fetchers that load covers and pages through each source's client into the disk cache.
- `reader/`: the reader.
- `ui/`: the design system (`theme/`, `components/`) and screens.

See `NOTICE` for credits.
