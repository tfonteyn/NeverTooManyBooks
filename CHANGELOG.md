### 5.5.0

NEW:

- Track your reading progress (see #16 ) options: [read/unread] (default)
  or [percentage] / [page x of y], configurable for each Style
- New search site: StripWeb (www.bdweb.be) for dutch/french comics.
- Author types added: storyboard, lettering

FIXES:

- Calibre synchronization: fixes a database lock preventing imports sometimes.
- LastDodo site: better handling of duplicate authors

### 5.4.0

- Vietnamese translation completed.

---

### 5.3.2

- partial Vietnamese translation activated.
- added haptic feedback to the ISBN keypad (follows your device haptic settings)

---

### 5.3.1

- fixes an issue with the "Set complete" function for both Authors and Series.

---

### 5.3.0

NEW:

- Improved grid-layout: the cover size as set in the style preference which was previously
  limited to list-mode only, is now also applied to the grid-layout. An XL size was added
  which in portrait view will display 1 cover/book the full-width of the screen, and in
  landscape view 2 covers/books. The 'large' and other sizes will use a number of covers
  as suited to the size of your screen. (refer to #32)
- Further customizing of grid-layout with style-preferences: Bringing up the
  book-context-menu can now be configured to use a long-click on the cover
  instead of the 3dot button.

FIXES:

- books without a cover are now displayed properly in list-mode.

---

### 5.2.4

- Optimized build of 5.2.3 with better responsiveness during book searches.

---

### 5.2.3

NEW:

- The default style settings is now available from the menu in the styles list
- The global setting whether to reorder titles/names is now moved to the Style level.
  It can now be set globally, and overridden on individual styles.
- Cropping a cover image can now also be done by 'pinching' with two fingers.
  Also provides a revamped action bar with a new option to undo changes
  without the need to quit and restart the cropping action.

FIXES:

- Issue #29 Crop cover left/right usable with Android 12+ gesture navigation; see above.
- Issue #30 which was introduced in 5.1.2 and causes an upgrade from 5.1.2 straight to 5.2.1 to
  fail.

REMOVED:

- predefined style "Compact" has been removed. It's easy to recreate as a user-style if needed.

---

### 5.1.1

- fix issue #27
- sync menu visibility no longer needs a restart

---

### 5.1.0

NEW:

- Full support for Android 14 (includes fix #23)
- Global defaults for styles: allows changing the defaults as used by the builtin styles.
- The style grouping "Read & Unread" (read-status) adds "Reading" (enhancement #26) showing books
  which have a read-start-date set, but which you have not finished yet.
- The filter for 'Read' also provides the 'Reading' option.
- A crash-report can now be saved to storage, and users get redirected to
  the project Github issues page.

FIXES:

- improved/optimized scrolling to the correct book row after a rebuild
- the generic error message "Please check that storage is writable..." has been split/redone,
  and will now be much clearer as to what is really wrong.
- user editable URLs are now checked for being syntactically valid.

---

### 5.0.0

NEW:

- Grid layout (see #22): aside of the traditional list, a new grid layout option was added which
  shows only the cover image (or author/title if there is no image available). Please note this is
  the first release of this functionality. All comments are welcome: just log a new
  GitHub [issue](https://github.com/tfonteyn/NeverTooManyBooks/issues) with a bug-report or any
  other remarks.
- Sorting by using groups in the list has now been extended to allow sorting on the book-level as
  well.
- Tapping on a cover in the book-list can now be configured to either zoom-in on the image as
  before, or to open the book-detail page. This is mostly useful in the new grid layout.
- A 'Help' menu-item was added that takes you to the
  project [documentation](https://github.com/tfonteyn/NeverTooManyBooks/wiki).

FIXES:

- LastDodo started to store series titles as "Title, The". We're now reversing those titles back to
  the original "The Title" and provide the usual dynamic reordering instead.

---

### 4.6.6

- fix #21 regression bug in UI in export

---

### 4.6.5

NEW:

- "page count" can now be shown on the booklist book level
- restructured access to individual website configuration settings.
  Instead of the more or less obscure "More..." preference menu,
  they are now accessible straight from the main "Websites" ordering screen.
- Clarified the limitations of "Search by title/author".

FIXES:

- fix #19 Publisher and PubDate are now fully independent to show/hide
  instead of partially linked as before.

---

### 4.6.0

- Cover operations can now be undone (via the cover context menu).
  This can be disabled in preferences if you are low on storage space.

---

### 4.5.1

- fix UI bug in export

---

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

### 4.4.2

- fix #17 : Devices like the Pixel 6a which have a `height:expanded/width:compact` screen size
  should now display the filter and style-picker dialogs correctly.

---

### 4.4.1

- fix GoogleBooks engine compression support.
- fix setting a books read-status from from the book-list row menus.

---

### 4.4.0

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

### 4.3.5

- fix #12; the cover browser is now fullscreen on medium screens.

---

### 4.3.4

- fix #10 : when searching by Author/Title a search was quitting at the first "not-found".
  The search will now proceed to the next site.

---

### 4.3.3

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

### 4.3.0

NEW:

- Portuguese translation added - with thanks to the contributors on
  [Weblate](https://hosted.weblate.org/engage/nevertoomanybooks/).

FIXES:

- 10" screen transitioning from landscape to portrait: the book options menu is cleaned up now.
- When using dark mode, dialogues now uniformly have a dark background as intended.
- Non-Latin script languages (Georgian, Greek,...) no longer fail being compared in lists
  (e.g. multiple authors for a book are now accepted).

---

### 4.2.0

NEW:

- Added BOL.COM as a search-source. This is a shopping site in Belgium & The Netherlands.
- Added preference setting to show/hide the shopping menus for amazon/bol.com.
- Note that this app is NOT affiliated with any sites.

FIXES:

- filter/style dialogs now expand properly on smaller screens.

---

### 4.1.2

- fix: enable KBNL and BedeTheque globally making them actually available. When upgrading,
  you'll still need to manually enable them in Settings/Websites on the books and covers tabs.
- Updated amazon price parsing for better results.

---

### 4.1.1

- Added more defensive checks and extra logging to the parsers which should help with
  decimal separator conflicts.
- Editing a book can now deal with invalid typed data and no longer hangs.
- fix for an issue with the debug-report.

---

### 4.1.0

NEW:

- Context menu button now available for all lists.
- Group/sorting by date-acquired now uses date-added as fallback when the former is
  not explicitly set.
- Embedded list/detail mode no longer needs to refresh the whole list when a cover is changed.
- Dialog boxes have been restyled using full Material.io v3.

FIXES:

- Importing Calibre library data now correctly maps bookshelves

---

### 4.0.0

NEW:

- New search-engine for Bedetheque (BDGest) for French comics
- Updated and re-enabled search-engine for KB.NL for Dutch books.
- Author pseudonym support:
    - The user can manage/display pseudonyms.
    - Automatic resolving is available for comics with stripinfo/lastdodo which use Bedetheque,
      and of course Bedetheque itself. This means the majority of european non-english comics
      (and a fairly large amount of English) are supported.
- Easier access to context menu's on the book list (button and/or long-click configurable).
- Booklist groups for author and publisher provide grouping/sorting on 1st character of the name.
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