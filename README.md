# Mangetsu

<div align="center">

<a href="https://github.com/maxlander2004/max-manga-reader">
  <img src=".github/readme-images/app-icon.png" alt="Mangetsu" height="160" width="160">
</a>

**Mangetsu · 満月**

A free and open source manga reader for Android.

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

Mangetsu is a personal rebrand of a fork of Yōkai / Mihon. It is a reader for manga you already have or can download yourself — it does not host any manga.

The project exists to keep hold of the things that matter to the owner: a clean reader, sensible library behavior, and a small set of upgrades that are useful day to day. It is also a place to try modest new tech without committing every change to the upstream readers.

## Features

<div align="left">

<details>
<summary><h3>What is new in Mangetsu</h3></summary>

- Full moon over a torii — the launcher icon is built from the repository, so the same art is what you install and what you see on the repo.
- Torii — the reader supports many reading directions and keeps each manga's own layout.
- Torii — the library is a single list view with categories you can expand.
- Torii — icons and media are read from your device, so offline libraries still work.
- Tei — the details screen, shelf and recents keep the look you set.
- Tei — share sheets and drag and drop sorting work the way you would expect.

</details>

<details>
<summary><h3>What this fork keeps from Yōkai / Mihon</h3></summary>

- Torii — manga downloader that downloads individual manga rather than copying whole files.
- Torii — local source chapters read their own headings.
- Torii — the library supports a lot of tracking.
- Torii — the library can be backed up to read offline or to cloud.
- Torii — the library supports updates from MANGA / ANILIST / Kitsu / Manga Updates / Shikimori / Bangumi.
- Torii — the library page is a single list view.

</details>

<details>
<summary><h3>What this fork keeps from J2K</h3></summary>

- Tei — UI design of the manga details screen.
- Tei — the library page is redesigned as a single list view.
- Tei — expandable categories.
- Tei — centred shelf.
- Tei — expanded toolbar.
- Tei — floating search bar.
- Tei — combined pages for a better tablet experience.
- Tei — group books by categories, status, source, and tag.
- Tei — comprehensive recents screen.
- Tei — shelf page.
- Tei — dynamic shortcuts.
- Tei — share sheets the right way.
- Tei — a lot more Material 3.
- Tei — Android 12 features such as automatic extension and app updates.

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
