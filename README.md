# BlackIcons

An Android icon pack that turns app icons into black line glyphs. Works with launchers that take icon packs
(Nova, Lawnchair, Niagara and others).

- About 15,000 icons come from [Arcticons](https://github.com/Arcticons-Team/Arcticons) by the Arcticons team,
  licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). They are fetched and rendered at build
  time by `ci/make_icons.py` (black variant, 160px WebP); this repository doesn't contain them.
- Apps without an Arcticons icon get the launcher fallback: a darkened circle with a black ring.
- The app can also make a black glyph from any installed app's own icon (its monochrome layer when the app has
  one). Pick it from the launcher's "edit icon" screen, or save it to Photos.

GitHub Actions builds every push to `main` and publishes `BlackIcons-ci.apk` as a pre-release.
