<!--
  ~ @Copyright 2018-2022 HardBackNutter
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

This is a book collection application, to keep track of your books and comics.

Add books by scanning their barcode, ISBN, or generic text searches.

Make sure to check the [documentation](https://github.com/tfonteyn/NeverTooManyBooks/wiki)

## User Interface languages:

- English, Dutch, Portuguese.
- German, French: quality should be very good but may lack in some places.
- Italian, Spanish: should be usable, but certainly not perfect.
- Czech, Greek, Polish, Russian, Turkish: mostly machine translated, no guarantees for quality.

Translations are now integrated and editable
on [https://hosted.weblate.org/engage/nevertoomanybooks/](https://hosted.weblate.org/engage/nevertoomanybooks/)

- everyone welcome; log a github 'issue' if you want to be credited with your help.

## Data sources

Data is fetched on-demand from multiple internet sites.
You can enable/disable and prioritize the sites in Settings/Search/Websites..

- **Amazon** with support for .com, .co.uk, .fr, .de, .nl, .com.be, .es sites.
  Other sites *may* work.

> > *WARNING:* Amazon is increasingly blocking access.
> > If you see any Amazon specific errors, I suggest you switch off Amazon in Settings/Search/Websites.

> > Please do not ask for GoodReads/LibraryThing/AbeBooks to be added.

- **Bedetheque** (French and more; Catalogue; European Comics)
- **BOL.com** (Dutch and more; Shop)
- **Google Books** (English and more; Catalogue)
- **ISFDB** (English and more; Catalogue; Fantasy and Science Fiction)
- **KB.NL** (Dutch and more; Catalogue)
- **LastDodo** (Dutch and more; Catalogue; European Comics)
- **OpenLibrary** (English and more; Catalogue)
- **StripInfo** (Dutch and more; Catalogue; European Comics)
- Supports synchronizing with a [Calibre](https://calibre-ebook.com/) Content Server.

## Device support:

Requires minimal Android 8.0 (API 26)

## Screen size support:

- 4" works but will be very cramped.
- 5" is the default aimed for.
- 7" and 10" tablets fully supported with dedicated screen layouts.

## Thanks

In August 2018, this project was forked from:
Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn.
Without their original creation, this project would not exist in its
current form. It was however largely rewritten/refactored and any
comments on this fork should be directed at myself and not
at the original creators.

## History

### 4.5.0

NEW:

- Amazon parsing has been brought up-to-date + amazon.es has been added to the tests.

- Deleted books will now be 'remembered'. This will allow importing a backup on a second device
  to automatically synchronize/mirror these deletions.

- improved displaying full-text-search results.

- added support for importing the "books.json" file from a backup-archive directly.

- add preference setting for a single barcode scan to automatically restart the scanner.
  There are now 3 ways to add books by scanning their barcodes:
  - single: "scan/edit"
  - continuous: "scan/edit/repeat"
  - batch: "scan/scan/.../edit/edit/..."

- resolving author names for Bedetheque itself is now also a user preference setting.

FIXES:

- fixed obscure bug the list would not rebuild after editing multiple books.

---
4.4.2

- fix #17 : Devices like the Pixel 6a which have a `height:expanded/width:compact` screen size
  should now display the filter and style-picker dialogs correctly.

---
4.4.1

- fix GoogleBooks engine compression support.
- fix setting a books read-status from from the book-list row menus.

---
4.4.0

NEW:

- performance improvement: automatic support for compression when fetching data from the web.

- improved compatibility with web sites.

- New field to hold the original-title of a translated book. None of the current information
  sources provide this data automatically; manual entry required. Displayed by default,
  but can be hidden via Settings/Field-Visibility.

- When editing a book, the language, format and colour drop-down fields will now show a
  set of defaults in addition to the users previously used values.

- Amazon captcha blocking now reported to the user + Amazon disabled when needed.
  It can still be re-enabled by the user to try again.

FIXES:

- fix #11:
  - OpenCamera on Android 10 and lower will now work as expected. Android 11+ prevents
    any non-standard camera apps to be used automatically however.
  - The option to crop the image immediately after using the camera will now work as expected.

- date-added + date-last-updated are now displayed in the local timezone (previously shown in UTC).

- fixed an obscure bug in editing the styles where editing an existing style could result
  in creating a duplicate.

- Bedetheque author resolution was broken on Android 10 or lower.

- Bol.COM more reliable cover image download.

REMOVED:

- The crop option "whole picture" was removed and this behaviour is now always on.

---
4.3.5

- fix #12; the cover browser is now fullscreen on medium screens.

---
4.3.4

- fix #10 : when searching by Author/Title a search was quitting at the first "not-found".
  The search will now proceed to the next site.

---
4.3.3

NEW:

- The real-author name (pseudonym usage) can now be globally hidden (Settings/Field-visibility)
  and when enabled, is shown in some more places.
- The date-added + date-last-update fields for a book are now shown by default,
  but can be hidden (Settings/Field-visibility).

FIXES:

- BUG fix for Android 8.x new installations which could crash on first start (as seen in #3).
- Unifies the UI for the filter-dialog and the style-picker making them consistent. This fixes #8.
- Apply workaround for google issue 181655428. This fixes #9.
- Improved/added runtime catching and reporting of Android errors.
- Minor layout fixes.
- Spanish and Italian translation improvements - with thanks to the contributors on
  [Weblate](https://hosted.weblate.org/engage/nevertoomanybooks/).

---
4.3.0

NEW:

- Portuguese translation added - with thanks to the contributors on
  [Weblate](https://hosted.weblate.org/engage/nevertoomanybooks/).

FIXES:

- 10" screen transitioning from landscape to portrait: the book options menu is cleaned up now.
- When using dark mode, dialogues now uniformly have a dark background as intended.
- Non-Latin script languages (Georgian, Greek,...) no longer fail being compared in lists
  (e.g. multiple authors for a book are now accepted).

---
4.2.0

NEW:

- Added BOL.COM as a search-source. This is a shopping site in Belgium & The Netherlands.
- Added preference setting to show/hide the shopping menus for amazon/bol.com.
- Note that this app is NOT affiliated with any sites.

FIXES:

- filter/style dialogs now expand properly on smaller screens.

---
4.1.2

- fix: enable KBNL and BedeTheque globally making them actually available. When upgrading,
  you'll still need to manually enable them in Settings/Websites on the books and covers tabs.
- Updated amazon price parsing for better results.

---
4.1.1

- Added more defensive checks and extra logging to the parsers which should help with
  decimal separator conflicts.
- Editing a book can now deal with invalid typed data and no longer hangs.
- fix for an issue with the debug-report.

---
4.1.0

NEW:

- Context menu button now available for all lists.
- Group/sorting by date-acquired now uses date-added as fallback when the former is
  not explicitly set.
- Embedded list/detail mode no longer needs to refresh the whole list when a cover is changed.
- Dialog boxes have been restyled using full Material.io v3.

FIXES:

- Importing Calibre library data now correctly maps bookshelves

---
4.0.0

NEW:

- New search-engine for Bedetheque (BDGest) for French comics
- Updated and re-enabled search-engine for KB.NL for Dutch books.
- Author pseudonym support:
  - The user can manage/display pseudonyms.
  - Automatic resolving is available for comics with stripinfo/lastdodo which use Bedetheque,
    and of course Bedetheque itself. This means the majority of european non-english comics
    (and a fairly large amount of English) are supported.
- Easier access to context menu's on the book list (button and/or long-click configurable).
- Booklist groups for author and publisher provide gouping/sorting on 1st character of the name.
- Update-from-internet is now available for a number of date based booklist groups
- Refitted support for importing Book Catalogue "bcbk" backup file
- New scanner code from 3.4 is considered stable.

FIXES:

- Merging duplicate Authors/Series/Publishers updated and made more robust.
- Importing badly formatted list-price fields no longer skips the book, but just skips
  the price field.
- Fixed filter name mismatches.

REMOVED:

- All CSV and XML exports are removed. It was increasingly difficult to keep them updated.
  Use JSON instead. A simplified CSV export is planned in a future version.
- TAR import is removed. This allowed the removal of the Apache library for tar.
  Repack any tar archive as a zip and import the zip.
