Draft notes for next version:

### 7.4.0

NEW:

- new UI language: Hungarian, kindly contributed
  by [boldizsar-nagy](https://hosted.weblate.org/user/boldizsar-nagy/)
- Author metadata: now collect/display birth/death dates
- Bedetheque: enhancements to find/select the correct edition when searching
  by ISBN for older comics
- Android 16 tested/supported.

FIXES:

REMOVED:

<hr style="border:1px solid blue;">

Supported Android versions:

* v2.1.0 requires Android 8.0 (API 26)

https://gs.statcounter.com/os-version-market-share/android/mobile-tablet/worldwide

2025-03-31:

| version | %     |     
|---------|-------|
| 15.0    | 7.56  |
| 14.0    | 42.15 |
| 13.0    | 59.54 |
| 12.0    | 71.97 |
| 11.0    | 82.82 |
| 10.0    | 88.57 |
| 9.0     | 91.85 |
| 8.1     | 92.84 |
| 8.0     | 94.90 |

<hr style="border:1px solid blue;">

Android 15 insets / EdgeToEdge

// https://developer.android.com/develop/ui/views/layout/edge-to-edge#system-gesture-insets

Need to make a decision on adding WindowInsetsCompat.Type.systemGestures()

According to the docs, we should apply this to the ViewPager2 usage and BottomSheets.
Tried it out for the ViewPager2 usage:

- book details + book edit
  Effect is not really visible, but touching area is indeed as per gesture docs.
  Not sure this is really useful on these screens.
  Not enabling for now.
- search-admin (settings)
  Effect is very visible making the site lists narrow.
  Not enabling for now.

BottomSheets:
I don't see the need... BottomSheet is the "focus". If the user decides they
want to go 'back' while a BottomSheet is displayed, they should close the BottomSheet first.

<hr style="border:1px solid blue;">

ENHANCE: ISSN (serials) lookups?

ENHANCE: https://www.belgischebibliografie.be

ENHANCE: add book by search: allow publisher + present list of finds instead of using first found.
==> SearchCoordinator.search when isbn not provided, will run a search until it finds an isbn,
and then redo the search WITH the isbn
==> so this is why we only ever get ONE result back.

Look into using
https://developer.android.com/guide/navigation/navigation-custom-back
https://developer.android.com/training/appbar/up-action

FIXME: check all cover logic where source and destination is allowed to be the same
We should ALWAYS use a temporary file to write to, and only on success rename it to
the actual destination file to prevent loss of original on failure.

FIXME: the dialog-fragment launcher api is increasingly adding overhead.
It needs to be replaced by ViewModel sharing on Activity level.

TODO: bring some unity to our use of child-fragments (e.g. ReadStatus)
and helper/handlers (e.g. CoverHandler) and similar constructs

TODO: TEST migrate to JUnit5 for on-device tests as well.

ENHANCE: add rotating functions to the cropper activity. This would allow
multi-rotate-undo by simply quiting the cropper.

<hr style="border:1px solid red;">
Known issues:

---

Window insets:
https://issuetracker.google.com/issues/388867281

2025-02-19: allegedly fixed in aug-2024, but not available in any image for the emulator...

On Android 15 (emulator standard image + ext-14 image), when the user changes the Theme Colors,
... THE ACTIVITY WILL RESTART as is normal...
the toolbar is automatically changed, but the background of the status bar
will switch to transparent.
When quitting settings back to the BoB, the status bar background will be correct again.

Second scenario: from BoB, do an import which changes preferences.
We force an activity restart, and now the BoB screen status bar is incorrect.

Emulator set to use "no cutout" or a "double cutout"
Android 14, 13 and 12 work FINE.

<hr style="border:1px solid red;">

Grouping on dates, e.g. on "Year-Read, Month-Read, Author".

If you read 3 books that month, first one by Author A, then one by Author B,
then another by Author A, they will show up as:

- year
    - month
        - A 2
            - title 3
        - B 1
            - title 2
        - A 2
            - title 1

So you get duplicate Author entries each showing the total (2) of books you
read that month of that Author.
This is due to the sorting on the date-read which **includes** the day.
If you added "Day-Read" to the grouping you would get:

- year
    - month
        - day 3
            - A 1
                - title 3
        - day 2
            - B 1
                - title 2
        - day 1
            - A 1
                - title 1

So the first list **is** correct if somewhat confusing.
You can either:

- include the Day group
- don't read two books by the same Author in the same month (and book(s) from other Authors in
  between)

<hr style="border:1px solid red;">

The node-management, i.e. storing the tree-state of the nodes in the booklist
is by design not foolproof. The nodes 'expanded' flag WILL get out of sync with
the display from time to time. There are no plans to fix this for now as the foolproof solution
would take far to much disk space.

<hr style="border:1px solid red;">

Prices: when importing books with a price field containing a value which cannot be parsed,
the price field is silently dropped. Details see BookDaoHelper#filterValues.
Some code can already deal with string-values in the Book bundle, but it is very likely
that IF such a string would end up in the database somehow, we'll
get a crash when trying to display it using a MoneyFormatter (FieldFormatter)
FIXME: Solution would be to allow Money to have string (instead/aside-of double) values.

<hr style="border:1px solid red;">

Booklist style sorting by date-read or date-added etc...:
samsung A520 with A8.0, month name not shown; we get the month number instead.
Does not happen on other devices/emulators. Won't pursue/fix.