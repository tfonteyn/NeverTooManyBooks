<!--
  ~ @Copyright 2018-2025 HardBackNutter
  ~ @License GNU General Public License
  ~
  ~ This file is part of NeverTooManyBooks.
  ~
  ~ NeverTooManyBooks is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ NeverTooManyBooks is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  ~ See the GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
  -->

This is a book collection application available for **Android** devices, to keep track of your books
and comics.

Add books by scanning their barcode, ISBN, or generic text searches.

Make sure to check the [documentation](https://github.com/tfonteyn/NeverTooManyBooks/wiki)

If you're migrating from the app "Book Catalogue", please
see [importing-from-book-catalogue](https://github.com/tfonteyn/NeverTooManyBooks/wiki#importing-from-book-catalogue)

Scan the QR code to open this page on your phone:<br>
![QR Code](qr-code.png)

### Download the latest release: 7.8.0

<a href="https://github.com/tfonteyn/NeverTooManyBooks/releases">
<img src="metadata/en-US/images/get-it-on-github.png" alt="Get it on GitHub" style="width:150px;"/></a>

<a href="https://apt.izzysoft.de/fdroid/index/apk/com.hardbacknutter.nevertoomanybooks">
<img src="metadata/en-US/images/IzzyOnDroid.png" alt="Get it on IzzySoft" style="width:150px;"/></a>

Signer "DN: CN=ca"; SHA-256 digest:
df971ce7d58d3cdf377c32e0e2f53d6599bd7f130a50c0529c45583ddc12a008

## User Interface languages:

- Chinese Simplified(CN), Chinese Traditional(TW),
  Czech, Dutch, English, French, Galician, German, Hungarian, Italian, Polish
  Portuguese, Portuguese(Brazil), Russian, Slovak, Spanish, Tamil, Turkish, Vietnamese.
- Machine translated, no guarantees for quality: Greek, Swedish.

Translations are editable on [Weblate](https://hosted.weblate.org/engage/nevertoomanybooks/)

- Pull-requests with translations are also welcome.
- Contact me by logging an [issue](https://github.com/tfonteyn/NeverTooManyBooks/issues) if you want
  to provide translations in another way.
- Please log a GitHub [issue](https://github.com/tfonteyn/NeverTooManyBooks/issues)
  if you want to be credited by name (instead of github alias) with your help.

<a href="https://hosted.weblate.org/engage/nevertoomanybooks/">
<img src="https://hosted.weblate.org/widget/nevertoomanybooks/multi-auto.svg"
     alt="Translation status" />
</a>

## Data sources

Data is fetched on-demand from multiple internet sites.
You can enable/disable and prioritize the sites in Settings/Search/Websites..

- **Amazon** with support for .com, .co.uk, .fr, .de, .nl, .com.be, .es sites.
  Other sites *may* work.

> *WARNING:* Amazon is increasingly blocking access.
> If you see any Amazon specific errors, I suggest you switch off Amazon in
> Settings/Search/Websites and log a
> GitHub [issue](https://github.com/tfonteyn/NeverTooManyBooks/issues)
> explaining what went wrong.

- **Bedetheque** (French and more; Catalogue; European Comics)
- **Bertrand.pt** (Portuguese and more; Shop)
- **BOL.com** (Dutch and more; Shop)
- **databazeknih.cz** (Czech and more; Catalogue)
- **dnb.de** (German; Catalogue)
- **Douban.com** (Chinese; Catalogue)
- **Goodreads** (English and more; Catalogue)
- **Google Books** (English and more; Catalogue)
- **ISFDB** (English and more; Catalogue; Fantasy and Science Fiction)
- **KB.NL** (Dutch and more; Catalogue)
- **LastDodo** (Dutch and more; Catalogue; European Comics)
- **OpenLibrary** (English and more; Catalogue)
- **StripInfo** (Dutch and more; Catalogue; European Comics)
- **StripWeb** (Dutch/French; Shop; European Comics)
- Supports synchronizing with a [Calibre](https://calibre-ebook.com/) Content Server.

## Device support:

- Requires minimal Android 8.0 (API 26)
- Supported/tested up to Android 16 (API 36).

## Android Permissions:

Please see
the [documentation](https://github.com/tfonteyn/NeverTooManyBooks/wiki#android-permissions).

## Screen size support:

- 4" works but will be very cramped.
- 5" is the default aimed for.
- 7" and 10" tablets fully supported with dedicated screen layouts.

### History

Please see the [CHANGELOG](/CHANGELOG.md)

---

## Thanks

- Hungarian translation: [boldizsar-nagy](https://hosted.weblate.org/user/boldizsar-nagy/)
- Galician translation: [Vaicheboa O'Loubam](https://hosted.weblate.org/user/vaicheboa/)
- Slovak translation + major improvements to the Czech
  translation: [Milan](https://hosted.weblate.org/user/Milan/)
- Tamil translation: [தமிழ்நேரம்](https://hosted.weblate.org/user/TamilNeram/)
- Portuguese(Brazil) translation: [Suburbanno](https://github.com/Suburbanno)
- Chinese (Simplified) translation: [CloneWith](https://github.com/CloneWith)
  and [evoke322](https://github.com/evoke322)
- Vietnamese translation: [ngocanhtve](https://github.com/ngocanhtve).
- Portuguese translation: [maverick74](https://github.com/maverick74).
- All other volunteers
  on [Weblate](https://hosted.weblate.org/user/?q=%20contributes:nevertoomanybooks).

In August 2018, this project was forked from:
_Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn._
Without their original creation, this project would not exist in its current form.
It was however largely rewritten/refactored and any comments on this fork should be
directed at myself and not at the original creators.