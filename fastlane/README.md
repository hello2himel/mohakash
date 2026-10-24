# Store metadata

This directory follows the Fastlane Android layout used by Google Play
(`supply`) and F-Droid.

```text
fastlane/metadata/android/en-US/
├── title.txt
├── short_description.txt
├── full_description.txt
├── privacy_url.txt
├── changelogs/2.txt
└── images/
    ├── icon.png              # 512 × 512
    ├── featureGraphic.png    # 1024 × 500
    └── phoneScreenshots/     # at least two 9:16 screenshots
```

The icon and feature graphic are ready. The phone screenshots must be captured
from a real device or emulator and added to `phoneScreenshots/` before the Play
listing can be submitted.

`changelogs/<versionCode>.txt` is used by both Play and the optional Play upload
workflow. Add a new file for every version code.
