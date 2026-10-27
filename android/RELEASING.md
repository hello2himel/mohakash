# Releasing the Android app

The permanent application ID is:

```text
mohakash.stargzr.space
```

The Kotlin namespace stays `app.mohakash.apod`. The package name is the
identity that Play and F-Droid use forever; do not change it after the first
store release.

---

## 1. Signing key

A 4096-bit RSA release key was generated for this project:

| Item | Path |
|---|---|
| Keystore | `android/keystore/mohakash-release.jks` |
| Passwords | `android/keystore.properties` |

Both files are git-ignored. **Back them up now, before publishing anything.**
Store the keystore and the password file in a password manager or another
off-machine location. Losing them means losing the ability to update the Play
listing with this upload key. If Play App Signing is enabled, Google holds the
app-signing key, but the upload key is still needed for every upload.

Certificate fingerprint (SHA-256):

```text
04:41:88:79:E6:5E:35:34:6F:C4:06:F5:70:85:8E:EE:D3:EE:21:B0:E5:F8:B5:8E:2A:0F:F3:7F:0A:14:C0:AB
```

The same fingerprint is published in
[`web/public/.well-known/assetlinks.json`](../web/public/.well-known/assetlinks.json)
so Android can verify the App Links for `https://mohakash.netlify.app/...`.
Update that file and redeploy the website whenever a signing certificate
changes: the GitHub release key above, the Play App Signing certificate (if
Google re-signs the bundle) and the F-Droid signing certificate. A missing or
stale fingerprint makes those links open in the browser instead of the app; it
does not break the app itself.

`android/keystore.properties` has this shape (the real values are not in git):

```properties
storeFile=keystore/mohakash-release.jks
storePassword=...
keyAlias=mohakash
keyPassword=...
```

### CI secrets

The tag workflow needs four repository secrets. From the repository root:

```bash
base64 -w0 android/keystore/mohakash-release.jks \
  | gh secret set ANDROID_KEYSTORE_BASE64

sed -n 's/^storePassword=//p' android/keystore.properties \
  | gh secret set ANDROID_KEYSTORE_PASSWORD

sed -n 's/^keyAlias=//p' android/keystore.properties \
  | gh secret set ANDROID_KEY_ALIAS

sed -n 's/^keyPassword=//p' android/keystore.properties \
  | gh secret set ANDROID_KEY_PASSWORD
```

Optionally set a repository variable so CI refuses to publish with the wrong
key:

```bash
gh variable set ANDROID_CERT_SHA256 \
  --body "04418879e65e35346fc406f570858eeed3ee21b0e5f8b58e2a0ff37f0a14c0ab"
```

The workflow checks for all four secrets and fails before building if they are
missing. It never publishes an unsigned APK.

Both signing and Play uploads run in a GitHub **environment** named `release`.
Configure that environment with required reviewers and a tag/ref rule so a
write-access token alone cannot spend the signing key or the Play service
account. Add `PLAY_SERVICE_ACCOUNT_JSON` as an environment secret for the Play
workflow.

> If the repository has collaborators you do not trust with the upload key, do
> not put the key in repository secrets. Build the signed artifacts locally and
> upload them by hand instead; the workflow is optional.

---

## 2. Version numbers

The checked-in source of truth is `android/app/version.properties`:

```properties
VERSION_CODE=22
VERSION_NAME=2026.10.27.15
```

The version name is `YYYY.MM.DD.N`:

- `YYYY.MM.DD` is the start date of the current release series.
- `N` increments for every release in that series (`.1`, `.2`, … `.9`, `.10`).
- The Git tag is exactly `v` + `VERSION_NAME`, for example `v2026.10.27.15`.
- `VERSION_CODE` is an independent, monotonically increasing integer. It must
  increase for every upload to Play/F-Droid, even a re-upload, and is never
  reused for the application ID.
- Keep the literal `versionCode`/`versionName` defaults at the top of
  `defaultConfig` in `android/app/build.gradle.kts` in sync with
  `version.properties`. fdroidserver's `checkupdates` parser reads those
  literals, while the build still uses the checked-in/env values below them.

To prepare the next release, update both files, commit them, tag the release
commit, and keep the README/F-Droid metadata latest-release values in sync.

---

## 3. Build and verify locally

Use JDK 17. On this machine:

```bash
export JAVA_HOME=/home/himel/jdks/jdk-17.0.20.1+1
cd android
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleRelease :app:bundleRelease
```

Outputs:

```text
android/app/build/outputs/apk/release/app-release.apk
android/app/build/outputs/bundle/release/app-release.aab
```

Verify the APK:

```bash
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
AAPT2="$ANDROID_HOME/build-tools/36.0.0/aapt2"

"$APKSIGNER" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
"$AAPT2" dump badging \
  app/build/outputs/apk/release/app-release.apk | head
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Expected:

- package `mohakash.stargzr.space`
- version code `22`
- version name `2026.10.27.15`
- one signer whose SHA-256 certificate digest matches the fingerprint above
- `jar verified` for the AAB

If `assembleRelease` produces an unsigned APK, the signing properties were not
found. Check `android/keystore.properties` or the four `ANDROID_*` environment
variables.

---

## 4. Publish through GitHub Actions

The workflow is `.github/workflows/release.yml`. It runs on a tag push, runs the
unit tests, builds the signed APK and AAB, verifies the package/version/signature,
creates `SHA256SUMS.txt`, and publishes a GitHub release.

```bash
git add .
git commit -m "release: 2026.10.27.15 (code 22)"
git push origin main

git tag -a v2026.10.27.15 -m "Mohakash Android 2026.10.27.15"
git push origin v2026.10.27.15
```

The release assets are:

```text
mohakash.apk
mohakash-2026.10.27.15.aab
SHA256SUMS.txt
```

The website's direct-download link uses
`https://github.com/hello2himel/mohakash/releases/latest/download/mohakash.apk`,
so keep the asset name `mohakash.apk` stable.

A manual run of the workflow (`workflow_dispatch`) builds and uploads the
artifacts to the workflow run but does **not** create a release.

---

## 5. Google Play

The AAB is Play-ready, but the Play Console account, app record and store
assets are owner actions.

### Donation policy note

All support/donation actions open an external page in the browser. The app has no
Play Billing dependency and no digital benefits are unlocked by donating. Google
Play's Payments policy and recent enforcement around external donation links are
account- and jurisdiction-dependent; confirm the current policy in Play Console
before submitting this build. If needed, ship a Play-specific flavor that hides
the support card, feed prompt and donation-reminder worker while keeping
donations available on F-Droid/GitHub.

### One-time setup

1. Create a Google Play developer account.
2. Create an app with package name `mohakash.stargzr.space`.
3. Enable **Play App Signing**. The key in this repository is the **upload key**.
4. Upload the first AAB manually in Play Console. This is required before the
   Play API can upload later releases.
5. Complete the store listing and policy forms:
   - Privacy policy: `https://mohakash.netlify.app/privacy`
   - Data safety: no data collected, no data shared, no analytics, no ads.
   - Content rating questionnaire.
   - Target audience: choose the intended audience; the app is general-audience
     astronomy content, not directed to children under 13.
   - Ads: no.
   - App access: all functionality is available without login.
6. Upload the existing store assets from `fastlane/metadata/android/en-US/`:
   icon, feature graphic and six phone screenshots.
7. Review and roll out.

### Later uploads from CI (optional)

Create a Play Console service account with release permissions, download its
JSON key, and add it as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret. Then
run the **Publish to Google Play** workflow manually, choose the release tag,
track and status, and start with `internal` + `draft` for the first API upload.

The workflow uses
`r0adkll/upload-google-play@e738b9dd8f2476ea806d921b64aacd24f34515a5`
(pinned to v1.1.5). It only uploads an existing signed AAB from the GitHub
release; it does not build or re-sign anything.

### Important signature note

Google Play App Signing re-signs the uploaded AAB with Google's app-signing key.
The direct-download APK is signed with the project upload key. A user cannot
upgrade from one to the other without uninstalling first. Say this explicitly
in release notes and support answers.

---

## 6. F-Droid

F-Droid builds from source and signs with its own key. The staging metadata is:

```text
fdroid/metadata/mohakash.stargzr.space.yml
```

Before opening a merge request:

1. Push the current release tag (currently `v2026.10.27.15`; use the latest
   `vYYYY.MM.DD.N` tag).
2. Replace the `Builds.commit` tag in the metadata with the full commit hash of
   the tagged release (F-Droid prefers full hashes).
3. Submit the metadata to
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata) as a merge request.
   This requires a GitLab account.
4. F-Droid will run `gradle assembleRelease` in `android/`. Without a keystore
   the build produces `app-release-unsigned.apk`, which F-Droid signs itself.

F-Droid users cannot upgrade between the GitHub APK, the Play APK and the
F-Droid APK because each is signed with a different key. They must uninstall
before switching sources. Document this on the download page and in the
F-Droid listing.

---

## 7. What is already done

- Package name changed to `mohakash.stargzr.space`.
- Version moved to `android/app/version.properties` (currently `2026.10.27.15`, code `22`).
- Release signing reads environment variables or `android/keystore.properties`.
- Unsigned release builds still work for F-Droid-style builders.
- Signed APK/AAB built and signature-verified locally.
- Tag workflow, Play upload workflow, store metadata and privacy policy added.

## 8. What still needs the owner

- Back up the keystore and password file off-machine.
- Approve adding the signing key to GitHub Actions secrets (or publish locally).
- Create the Play Console app and complete the listing/policy forms.
- Keep the Play listing screenshots current with each major UI release.
- Create a GitLab account and submit the F-Droid merge request.
- Confirm the final store listing wording, target audience and content rating.
