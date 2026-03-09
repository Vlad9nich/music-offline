# YaNeoDex

YaNeoDex is a lightweight Android 11+ offline music app with local playback, playlists, shuffle, and a scaffold for external HTML-based music sources.

## Current capabilities

- import local audio files into private app storage
- extract metadata for title, artist, and duration
- create playlists and add imported tracks
- play local tracks and playlist queues
- shuffle playlist playback
- build a debug APK for Android 11+

## Parser scaffold

The app includes a reusable HTML source scaffold for future integrations.

Core classes:
- `com.codex.signalflow.source.MusicSource`
- `com.codex.signalflow.source.SelectorBasedHtmlMusicSource`
- `com.codex.signalflow.source.HtmlMusicSourceTemplate`
- `com.codex.signalflow.source.PendingHtmlSourceTemplate`
- `com.codex.signalflow.source.MusicSourceCatalog`

## Build output

Debug APK:
- `app/build/outputs/apk/debug/app-debug.apk`
