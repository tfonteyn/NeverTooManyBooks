Draft notes for next version:

### 7.1.0

NEW:
- #91 Date (first) published: the date-picker has been redesigned, and is much easier to use.
- #93 sorting by language now uses the fully localized language name
- #96 language field added for translated book
- #113 add a group-level summation of the books in that group.
- local search has been redesigned, making the UI cleaner
- added filter for identifiers
- date parsers performance increased, which *might* show while scrolling the book list.

FIXES:
- #109 sorting by dates on the book level now supports mixing partial-dates
- Citations have been tweaked to better conform to the standards

REMOVED:
- local search from the book-details screen.

<hr style="border:1px solid blue;">

Supported Android versions:

* v2.1.0 requires Android 8.0 (API 26)

https://gs.statcounter.com/os-version-market-share/android/mobile-tablet/worldwide

https://apilevels.com/

2025-01-31:
14.0     37.09
13.0     57.66
12.0     70.73
11.0     82.35
10.0     88.56
9.0      92.15
8.1      93.23
8.0      95.24

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

ENHANCE Pseudonyms on ISFDB:
 While reading the book page, we could follow the author url,
 and from there check the header for "Used As Alternate Name By"
 Problem: if it's the real-author anyhow, then loading
 that url can become very slow if the author has many books.
 Alternative: lookup Author names individually.

<hr style="border:1px solid red;">
Known issues:


FIXME: bug in android framework causing a TransactionTooLargeException

      Reproducing:
      - open a book detail
      - swipe to next book
      - repeat for 20..40 books
      - edit book
      - crash

https://stackoverflow.com/questions/11451393/what-to-do-on-transactiontoolargeexception
==> viewpager1
https://medium.com/shopback-tech-blog/handle-transactiontoolargeexception-237961bd5ef8
==> it's not us
https://medium.com/inloopx/adventures-with-fragmentstatepageradapter-4f56a643f8e0
==> viewpager1

BUT... androidx.viewpager2.adapter is the root problem.
https://issuetracker.google.com/issues?q=componentid:561920%20status:open
It saves the state for each fragment it has displayed,
so swipe enough... and crash.

    java.lang.RuntimeException: android.os.TransactionTooLargeException: data parcel size 543240 bytes
      Bundle stats:
        android:viewHierarchyState [size=2928]
          android:views [size=2880]
        androidx.lifecycle.BundlableSavedStateRegistry.key [size=539584]
          android:support:activity-result [size=10616]
            KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS [size=9548]
          android:support:fragments [size=528592]
            fragment_0ad96e00-3f7c-4a23-b5a5-095f25e35c8f [size=528208]
              viewState [size=467820]
                0x7f090317 [size=467652]
              childFragmentManager [size=59276]
                fragment_b78f1ec9-6877-4c19-9947-21f2f1af61c0 [size=11592]
                  viewState [size=9228]
                  childFragmentManager [size=1484]
                    fragment_ac6548fc-392b-4ef3-ac8b-16700c25de71 [size=1100]
                fragment_8282bf04-d139-45db-b6b3-1fc796fe754a [size=11500]
                  viewState [size=9136]
                  childFragmentManager [size=1484]
                    fragment_af705a3f-55a4-4d83-bd09-ea2ea1f120a9 [size=1100]
                fragment_1bdd665e-3ded-4c87-813b-2d5a77a286d8 [size=11592]
                  viewState [size=9228]
                  childFragmentManager [size=1484]
                    fragment_a71abbc4-27b9-42d0-b388-5b4802aab467 [size=1100]
                fragment_63a14570-794e-4c92-875e-809a84cb2157 [size=11592]
                  viewState [size=9228]
                  childFragmentManager [size=1484]
                    fragment_6e786c56-c8e6-4b4d-9e9d-9282c364d42d [size=1100]
                fragment_15e15901-37a1-439b-a628-cf68d13f8989 [size=11592]
                  viewState [size=9228]
                  childFragmentManager [size=1484]
                    fragment_450f681d-0d4d-45fa-a642-6f5ce11504cb [size=1100]
      PersistableBundle stats:
        [null]
        at android.app.servertransaction.PendingTransactionActions$StopInfo.run(PendingTransactionActions.java:146)
        at android.os.Handler.handleCallback(Handler.java:991)
        at android.os.Handler.dispatchMessage(Handler.java:102)
        at android.os.Looper.loopOnce(Looper.java:232)
        at android.os.Looper.loop(Looper.java:317)
        at android.app.ActivityThread.main(ActivityThread.java:8787)
        at java.lang.reflect.Method.invoke(Native Method)
        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:591)
        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:871)
      Caused by: android.os.TransactionTooLargeException: data parcel size 543240 bytes
        at android.os.BinderProxy.transactNative(Native Method)
        at android.os.BinderProxy.transact(BinderProxy.java:586)
        at android.app.IActivityClientController$Stub$Proxy.activityStopped(IActivityClientController.java:1498)
        at android.app.ActivityClient.activityStopped(ActivityClient.java:100)
        at android.app.servertransaction.PendingTransactionActions$StopInfo.run(PendingTransactionActions.java:135)
        ... 8 more

<hr style="border:1px solid red;">

Window insets:
https://issuetracker.google.com/issues/388867281
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