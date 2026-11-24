# Kaalam

**Every age. Every moment.** (காலம் = time / age)

A personal Android app: an age calculator plus birthdays, anniversaries and your own events, with fingerprint and PIN lock, sync to Google Calendar, and an encrypted backup in Google Drive.

## Features

- **Home**: your age in years, months and days, total days, weeks and hours lived, and a live countdown to your next birthday. Tap *Change* to work out anyone's age.
- **People**: everyone you add, grouped by month and sorted by days left. You can search and filter by 🎂 / 💍 / ⭐, or sort A–Z.
- **Events**: Birthday, Anniversary or Custom (any name you like). Each one can repeat yearly or happen once, and you can set reminders (on the day, 1 day before, 1 week before, or a custom number of days). The age or year count is optional.
- **Lock**: fingerprint on every launch, with a 6-digit PIN as backup. After 5 wrong tries you wait 30 seconds. Admin asks for the fingerprint or PIN again. You can choose how long before the app locks itself, hide the app preview and block screenshots.
- **Google Calendar**: events go into a **separate "Kaalam" calendar** with its own teal colour, so they're easy to tell apart. Each event title has an emoji and ends with " · Kaalam", the description says "⏳ Created by Kaalam app", and a hidden `source=kaalam` tag stops duplicates. Calendar reminders are off by default, so you only get Kaalam's own notifications.
- **Google Drive**: `My Drive/Kaalam/kaalam_data.enc` is AES-256 encrypted with your PIN. It saves after every change and keeps one daily copy in `backups/` (the last 7 days). You can restore on a new phone with the same PIN.
- **Admin**: shows stats and has your profile, people, custom event types, Drive, calendar and security settings, import from contacts, and export to CSV for Excel.
- **Notifications**: a reminder with a **Send wish** button that opens WhatsApp with a ready-made message.

## One-time Google Cloud setup (needed for Drive + Calendar)

Google only lets the app sign in once it's registered. This takes about 10 minutes in a browser.

1. Go to <https://console.cloud.google.com/> and create a project, e.g. **Kaalam**.
2. Open **APIs & Services › Library**. Enable **Google Drive API**, then enable **Google Calendar API**.
3. Open **APIs & Services › OAuth consent screen** (also called *Google Auth Platform › Branding / Audience*):
   - User type: **External**. App name: **Kaalam**. Use your email for support and developer contact.
   - Under **Audience › Test users**, add **your own Gmail address**.
   - Leave it in **Testing** mode. That's fine for personal use.
4. Open **APIs & Services › Credentials › Create credentials › OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.prabhu.kaalam`
   - SHA-1 certificate fingerprint:
     ```
     03:FE:1F:DD:80:3B:B4:33:63:6F:CC:97:84:D5:00:D0:9B:E7:3B:B0
     ```
   - Tap **Create**. You don't need to download anything.
5. In Kaalam, go to **Admin › Google Drive data › Connect Google account**.

Because the app is in Testing mode, Google shows a "Google hasn't verified this app" screen. Tap **Continue**. This is expected for your own app.

## Install

Open the repository's **Releases** page on your phone while signed in to GitHub. Download `Kaalam-vX.Y.Z.apk` and open it. Android asks once to allow installs from your browser.

## How builds work

`.github/workflows/build.yml` builds the APK on GitHub:

- Every push to `main` builds a test APK. Download it from the Actions run, under *Artifacts*.
- To publish a release, go to **Actions › Build APK › Run workflow** and enter a version (e.g. `1.0.1`). That builds the signed APK and puts it on the Releases page.

The app is signed with the key in the `SIGNING_KEY` repository secret. **Never change or lose this key.** Android only installs updates signed with the same key, and Google sign-in is registered against its SHA-1 (shown above).
