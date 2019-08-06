/**
 * Replacement for the old FastScrollListView which was ListView specific.
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.widgets.cfs.CFSRecyclerView} extends RecyclerView.
 * <p>
 * FastScroller initialisation code lifted from "recyclerview-1.1.0-alpha01-sources.jar"
 * <br>
 * <br>1. xml attributes to enable the fast-scroller (and set the thumb etc)
 * <br>2. xml attribute to set the Overlay drawable; this does not enable it!
 * <br>
 * <br>3. Call RecyclerView#setAdapter with a 'normal' Adapter ==> independent from 1+2 above
 * <br>OR
 * <br>3. Call RecyclerView#setAdapter with an Adapter that implements
 * {@link com.hardbacknutter.nevertomanybooks.widgets.FastScrollerOverlay.SectionIndexerV2}
 * The fast scroller will detect this and call
 * {@link com.hardbacknutter.nevertomanybooks.widgets.cfs.CFSRecyclerView#drawIndexerOverlay}
 *
 * <pre>
 *   csf:cfsEnabled="true"
 *   csf:cfsHorizontalThumbDrawable="@drawable/fast_scroll_thumb"
 *   csf:cfsHorizontalTrackDrawable="@drawable/fast_scroll_track"
 *   csf:cfsVerticalThumbDrawable="@drawable/fast_scroll_thumb"
 *   csf:cfsVerticalTrackDrawable="@drawable/fast_scroll_track"
 *   csf:cfsOverlayDrawable="@drawable/fast_scroll_overlay"
 * </pre>
 *
 * <p>
 * {@link com.hardbacknutter.nevertomanybooks.widgets.cfs.CFSFastScroller}
 * A modified copy of the original androidx.recyclerview.widget.FastScroller
 * <p>
 * Code taken from  "recyclerview-1.1.0-alpha05-sources.jar"
 * androidx/recyclerview/widget/FastScroller.java
 * <p>
 * Added this to the end of
 * {@link com.hardbacknutter.nevertomanybooks.widgets.cfs.CFSFastScroller#onDrawOver}
 * <pre>
 *       // BEGIN - CFSRecyclerView
 *       if (mRecyclerView instanceof CFSRecyclerView) {
 *           if (isDragging()) {
 *               ((CFSRecyclerView) mRecyclerView).drawIndexerOverlay(canvas, state);
 *           }
 *       }
 *       // END - CFSRecyclerView
 * </pre>
 * <p>
 * A secondary issue is fixed by:
 * https://issuetracker.google.com/issues/64729576
 * https://github.com/caarmen/RecyclerViewBug/blob/hack/app/src/main/java/android/support/v7/widget/HackFastScroller.java
 * <p>
 * Class member:
 * <pre>
 *     private int mMinVerticalThumbHeight;
 * </pre>
 * <p>
 * Constructor:
 * <pre>
 *     // BEGIN - CFSRecyclerView
 *     mMinVerticalThumbHeight = recyclerView.getContext().getResources()
 *                               .getDimensionPixelSize(R.dimen.cfs_fast_scroll_min_thumb_height);
 *     // END - CFSRecyclerView
 * </pre>
 * <p>
 * At the end of
 * {@link com.hardbacknutter.nevertomanybooks.widgets.cfs.CFSFastScroller#updateScrollPosition(int, int)}
 * <pre>
 *     // BEGIN - CFSRecyclerView
 *     if (mVerticalThumbHeight < mMinVerticalThumbHeight) {
 *         mVerticalThumbHeight = mMinVerticalThumbHeight;
 *     }
 *         // END - CFSRecyclerView
 * </pre>
 */
package com.hardbacknutter.nevertomanybooks.widgets.cfs;
