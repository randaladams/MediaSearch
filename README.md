# Media Search

An Android app that keeps an offline, searchable list of the movie and TV folders on a Raspberry Pi, fetched over SSH with a private key.

## How it works

- **Refresh** (↻ in the top bar) connects to the Pi and runs one `find` per library, so only folder names cross the network.
  - Movies: every folder directly inside the Movies folder, e.g. `The Matrix (1999)`.
  - TV: every show folder inside the TV folder plus the season folders inside each show, e.g. `Breaking Bad (2008)/Season 1`.
- Names are split into title and year. `Title (1999)`, `Title [1999]`, `Title 1999` and `Title.1999` all work; a folder with no year just keeps its name.
- Results go into a local Room (SQLite) database, so search works with no network. A refresh replaces the database in one transaction; if the server returns no folders at all (drive not mounted, wrong path), the old copy is kept.
- Search matches the title or the full folder name, so typing a year works too. Sorting ignores a leading "The". Tap a show to see its seasons.

## Build

1. Open the `MediaSearch` folder in Android Studio (Ladybug or newer). It will download Gradle and generate the wrapper jar on first sync.
2. Plug in your phone (USB debugging on) and press Run.

minSdk 26 (Android 8.0), targetSdk 35. Libraries: Jetpack Compose + Material 3, Room, DataStore, JSch (mwiede fork) + Bouncy Castle.

## Set up a key for the phone

A dedicated key for the phone is best, so you can revoke it without affecting your other machines. On your computer:

```bash
ssh-keygen -t ed25519 -f mediasearch_key -C "media-search-phone"
ssh-copy-id -i mediasearch_key.pub pi@192.168.1.50     # or append the .pub to ~/.ssh/authorized_keys on the Pi
```

Copy `mediasearch_key` (the file **without** `.pub`) to the phone (USB, cloud drive, etc.), then in the app open **Settings → Import private key…** and pick it. The app copies it into its private storage; you can delete the copy from Downloads afterwards.

RSA, ECDSA and Ed25519 keys work, in OpenSSH or PEM format. If you set a passphrase, enter it in Settings.

## Settings

| Field | Example |
|---|---|
| IP address | `192.168.1.50` |
| Port | `22` |
| Username | `pi` |
| Movies folder | `/mnt/media/Movies` (or `~/Movies`) |
| TV folder | `/mnt/media/TV` |

**Test connection** checks the values on screen (saved or not) and reports whether both folders exist. **Save** stores them on the phone with DataStore.

## Notes

- Host key checking is off (`StrictHostKeyChecking=no`), which is reasonable on a home LAN. For use over the internet, add a known-hosts check in `ssh/SshClient.kt`.
- The key passphrase is stored in app-private storage in plain text. Leave it blank (unencrypted key) or move it to Android Keystore if that matters to you.
- Hidden folders (`.Trash`, `@eaDir`, …) are skipped.
- Listing uses GNU `find -printf`, which Raspberry Pi OS has by default.

## Project layout

```
app/src/main/java/com/example/mediasearch/
  MediaSearchApp.kt         app singletons, Bouncy Castle setup
  MainActivity.kt           switches between Library and Settings
  data/
    SettingsRepository.kt   DataStore settings + key import
    Entities.kt, MediaDao.kt, AppDatabase.kt   Room database
    NameParser.kt           "Title (Year)" and "Season N" parsing
    MediaRepository.kt      refresh: SSH listing → database
  ssh/SshClient.kt          JSch connection and find commands
  ui/                       Compose screens and ViewModels
```
