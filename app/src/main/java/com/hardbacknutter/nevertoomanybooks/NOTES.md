Draft notes for next version:

### 7.7.0

NEW:
- github #164: adding 2 extra image slots for books
- The code for fetching images was rewritten. This finally fixes the issues with OpenLibrary.
- DatabazeKnihSearchEngine & BOL parsers updated for site changes

FIXES:
- on-screen keyboard fixes for Android 15+ Edge2Edge usage

REMOVED:

<hr style="border:1px solid blue;">

Supported Android versions:

* v2.1.0 requires Android 8.0 (API 26)

https://gs.statcounter.com/os-version-market-share/android/mobile-tablet/worldwide

2025-07-31:

| version | %     |     
|---------|-------|
| 15.0    | 26.75 |
| 14.0    | 46.25 |
| 13.0    | 62.20 |
| 12.0    | 73.74 |
| 11.0    | 83.51 |
| 10.0    | 88.75 |
| 9.0     | 91.68 |
| 8.1     | 92.51 |
| 8.0     | 94.44 |

<hr style="border:1px solid blue;">

Android 15 insets / EdgeToEdge

// https://developer.android.com/develop/ui/views/layout/edge-to-edge#system-gesture-insets

Need to make a decision on adding WindowInsetsCompat.Type.systemGestures()

According to the docs, we should apply this to the ViewPager2 usage.
Tried it out for the ViewPager2 usage:

- book details + book edit
  Effect is not really visible, but touching area is indeed as per gesture docs.
  Not sure this is really useful on these screens.
  Not enabling for now.
- search-admin (settings)
  Effect is very visible making the site lists narrow.
  Not enabling for now.

<hr style="border:1px solid blue;">

ENHANCE: ISSN (serials) lookups?

ENHANCE: https://www.belgischebibliografie.be

ENHANCE: add book by search: allow publisher + present list of finds instead of using first found.
==> SearchCoordinator.search when isbn not provided, will run a search until it finds an isbn,
and then redo the search WITH the isbn
==> so this is why we only ever get ONE result back.

TODO: Look into using
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

<hr style="border:1px solid black;">

ENHANCE: add a UUID to bookshelves

- "My Books", id=1, uuid=x1
- "new shelf", id=2, uuid=x2
- backup


- restore same device
- all shelves recognized by uuid
- done


- restore new device
- "My Books", id=1, uuid=x3

- "new shelf", id=2, uuid=x2 ==> x2 does not exist, import, all fine

- "My Books", id=1, uuid=x1 ==> x1 does NOT exist
    - if the name does exist, import, done
    - name EXISTS:
      ? merge, drop x2
      ? rename to "bis", keep x2; user can manually merge x2->x3 or x3->x2

So basically, always either matching uuid, or import as new.
When conflict in name, auto rename and have user merge manually

import old backup, so changes, just check name as we do now.

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
Android 16, 14, 13 and 12 work FINE.

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