# Mangetsu

<div align="center">

<a href="https://github.com/maxlander2004/max-manga-reader">
  <img src=".github/readme-images/app-icon.png" alt="Mangetsu" height="160" width="160">
</a>

**Mangetsu · 満月** — a free and open source manga reader for Android.

</div>

<div align="center">

[![Build debug APK](https://github.com/maxlander2004/max-manga-reader/actions/workflows/apk.yml/badge.svg?labelColor=27303D)](https://github.com/maxlander2004/max-manga-reader/actions/workflows/apk.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/maxlander2004/max-manga-reader?labelColor=27303D&color=0877d2)](LICENSE)
[![Android](https://img.shields.io/badge/minSdk-26-blue.svg?labelColor=27303D)](https://apilevels.com/api/26)
[![Mirror: git.aap](https://img.shields.io/badge/mirror-git.aap-red.svg?labelColor=27303D)](https://git.aap.my.id/maxlander2004/mangetsu)

</div>

<img src=".github/readme-images/banner.png" alt="Mangetsu" width="960">

## Download

The debug APK is built on every push to `main` and `master` by CI. The latest artifact is on the [Actions page](https://github.com/maxlander2004/max-manga-reader/actions/workflows/apk.yml).

You can also build it yourself:

```bash
git clone https://github.com/maxlander2004/max-manga-reader.git
cd max-manga-reader
./gradlew :app:assembleStandardDebug
ls app/build/outputs/apk/standard/debug/*.apk
```

## About this fork

Mangetsu is a free and open source manga reader for Android. It reads manga you already have or can download yourself — it does not host any manga.

The project exists to keep the things that matter to the owner: a clean reader, sensible library behavior, and a small set of upgrades that are useful day to day. It is also a place to try modest new tech without committing every change upstream.

## Features

<div align="left">

<details>
<summary><h3>What is new in Mangetsu</h3></summary>

- The launcher icon is built from the repository — the same torii-and-full-moon art is what you install and what you see on the repo.
- Many reading directions, with each manga keeping its own layout.
- A single library list view with categories you can expand.
- Icons and media read from your device, so offline libraries still work.
- A details screen, shelf and recents that keep the look you set.
- Share sheets and drag-and-drop sorting that work the way you would expect.

</details>

<details>
<summary><h3>What this fork keeps from J2K</h3></summary>

- The manga details screen UI design.
- A library page redesigned as a single list view.
- Expandable categories.
- A centred shelf.
- An expanded toolbar.
- A floating search bar.
- Combined pages for a better tablet experience.
- Books grouped by categories, status, source, and tag.
- A comprehensive recents screen.
- A shelf page.
- Dynamic shortcuts.
- Share sheets done the right way.
- More Material 3 throughout.
- Android 12 features such as automatic extension and app updates.

</details>

</div>

## Getting started

- Android 8.0 or higher.
- Download the latest debug APK from the Actions page, or build it yourself.
- Open the file.
- Pick your manga source.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

<div align="left">

<details><summary>Issues</summary>

Before reporting a new issue, check the [FAQ](https://mihon.app/docs/faq/general), the [changelog](https://github.com/maxlander2004/max-manga-reader/releases) and the already opened [issues](https://github.com/maxlander2004/max-manga-reader/issues).

</details>

<details><summary>Bugs</summary>

- Include the version.
- Include steps to reproduce.
- Include a screenshot.
- If it could be device-dependent, try to reproduce on another device.
- For large logs use [Pastebin](https://pastebin.com/).
- Don't group unrelated requests into one issue.

</details>

</div>

</div>

### Credits

Thank you to all the people who have contributed.

<a href="https://github.com/maxlander2004/max-manga-reader/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=maxlander2004/max-manga-reader" alt="Mangetsu contributors" title="Mangetsu contributors" width="600">
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 null2264

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
