# WordFlow Android source layout

The original Base64 source archive has been replaced with normal files.

- `app/src/main/java`: Android Java app
- `app/src/test`: unit tests
- `scripts`: offline tests and device smoke tests
- `.github/workflows/build.yml`: CI Android APK build

GitHub Actions runs Gradle against these checked-in sources.
