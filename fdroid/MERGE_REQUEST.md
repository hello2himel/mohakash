<!--
Draft MR description for https://gitlab.com/fdroid/fdroiddata.

Open the MR after 2027-01-22 (three months after the public repository was
created on 2026-10-22). Update the version, commit hash, and screenshots link
to the latest tagged release before submitting.
-->

## Abstract for new app inclusion

Mohakash is a NASA Astronomy Picture of the Day (APOD) client and Android live
wallpaper. It shows today's picture, title, credit and explanation, and lets
people browse the full 1995-present archive, search/filter it, and keep
favourites for offline reading. The live wallpaper uses a real Android
`WallpaperService`, with Fill/Fit modes, optional daily updates, and
Wi-Fi-only downloads.

Screenshots:
https://github.com/hello2himel/mohakash/tree/main/fastlane/metadata/android/en-US/images/phoneScreenshots

### Why this app is useful and distinct

- It is a live wallpaper, not only an APOD viewer. The wallpaper is drawn by a
  native `WallpaperService`, can refresh daily or rotate through valid
  historical APODs from today’s date, and can avoid mobile data.
- It ships a searchable archive from 1995 to today, with year/media filters,
  offline favourites and a local History of random discoveries and carousel
  wallpaper choices.
- The API endpoint is configurable. The default API is generated from this
  repository and can be self-hosted; the app is not tied to a proprietary
  service.
- No account, ads, analytics, tracking, or crash reporting. Donation support is
  optional, external and disabled by a single setting; it unlocks nothing.
- The UI is a dark Material 3 interface designed for phones and tablets.

### Maintenance window

The public repository was created on 2026-10-22. I will submit after
2027-01-22, when it has been maintained for more than three months as
requested. The app is actively maintained.

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified and does not oppose inclusion. I am the author.
* [x] The upstream repository contains [Fastlane](https://gitlab.com/snippets/1895688) metadata: summary, full description, icon, screenshots, changelog, and the `en-US` locale.

### Docs

* [x] I have read the [contribution guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md).
* [x] The metadata follows the [metadata templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] I have read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/).
* [x] I have read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title is `New app: Mohakash`.
* [x] The fdroiddata fork is public and the branch is not protected.
* [x] The branch is based on current `master`; I will rebase only if there is a conflict.
* [x] There is no RFP issue for this app; no issue is closed by this MR.
* [x] This MR submits one app.

### Metadata

* [x] The metadata is in `metadata/mohakash.stargzr.space.yml`.
* [x] The metadata is valid YAML and uses LF line endings.
* [x] I did not add summary, description, changelog, or images to fdroiddata. They are provided by the upstream Fastlane metadata.
* [x] The release is tagged. For the final submission I will use the latest tag and enable `UpdateCheckMode: Tags ^v[0-9.]+$` / `AutoUpdateMode: Version`.
  - The current tag is `v2026.10.27.22`; the metadata uses `UpdateCheckMode: Tags` with the full tagged commit in `Builds.commit`.
* [x] The issue tracker and contact information are available at https://github.com/hello2himel/mohakash/issues.
* [x] `AuthorName` is set.
* [x] No external repositories or srclibs are needed.
* [ ] Reproducible Builds are not enabled. The release keystore is private to the GitHub/Play channel, so F-Droid signs its own APK. I can revisit this if needed.
* [x] No ABI split is needed; the APK is small.
* [x] Only the latest version is present, with no disabled versions.
* [x] The `commit` field is a full 40-character hash.

### Pipeline

* [x] All pipelines pass.
* [x] Warnings and errors in the Reports tab are fixed or explained.
* [x] F-Droid CI runners are provided by GitLab's FOSS program; no payment is needed.

/label ~"New App"
