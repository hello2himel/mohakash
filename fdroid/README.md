# F-Droid staging metadata

`metadata/mohakash.stargzr.space.yml` is the app's metadata for
[fdroiddata](https://gitlab.com/fdroid/fdroiddata).

F-Droid does not use the release key in `android/keystore/`. It builds from the
tagged source and signs the resulting APK with F-Droid's own key. The metadata
therefore points at an **unsigned** release output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Before opening a merge request:

1. Push the release tag.
2. Replace `Builds.commit` with the full commit hash of the tagged release.
3. Copy the YAML file to `metadata/mohakash.stargzr.space.yml` in a fork of
   `fdroiddata`.
4. Open a merge request and answer the reviewer questions.

F-Droid users cannot upgrade between the GitHub APK, the Play APK and the
F-Droid APK because each is signed with a different key. They must uninstall
before switching sources.
