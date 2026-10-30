# BlackIcons

An Android icon pack that turns app icons into bold white symbols on black circles. Works with launchers that take icon packs
(Nova, Lawnchair, Niagara and others).

- About 15,000 icons come from [Arcticons](https://github.com/Arcticons-Team/Arcticons) by the Arcticons team,
  licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). They are fetched and rendered at build
  time by `ci/make_icons.py` (white lines drawn bolder on a black circle, 192px WebP); this repository doesn't contain them.
- Apps without an Arcticons icon get the launcher fallback: their own icon, shrunk onto a black circle.
- The app can also make a matching white-on-black icon from any installed app's own icon (its monochrome layer when the app has
  one). Pick it from the launcher's "edit icon" screen, or save it to Photos.

GitHub Actions builds every push to `main` and publishes `BlackIcons-ci.apk` as a pre-release.
