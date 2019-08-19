/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Replacement for the old FastScrollListView which was ListView specific.
 * <p>
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView} extends RecyclerView.
 * <p>
 * FastScroller initialisation code lifted from "recyclerview-1.1.0-alpha01-sources.jar"
 * <br>
 * <br>1. xml attributes to enable the fast-scroller (and set the thumb etc)
 * <br>2. xml attribute to set the Overlay drawable; this does not enable it!
 * <br>
 * <br>3. Call RecyclerView#setAdapter with a 'normal' Adapter ==> independent from 1+2 above
 * <br>OR
 * <br>3. Call RecyclerView#setAdapter with an Adapter that implements
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay.SectionIndexerV2}
 * The fast scroller will detect this and call
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSRecyclerView#drawIndexerOverlay}
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
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSFastScroller}
 * A modified copy of the original androidx.recyclerview.widget.FastScroller
 * <p>
 * Code taken from  "recyclerview-1.1.0-alpha05-sources.jar"
 * androidx/recyclerview/widget/FastScroller.java
 * <p>
 * Added this to the end of
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSFastScroller#onDrawOver}
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
 * {@link com.hardbacknutter.nevertoomanybooks.widgets.cfs.CFSFastScroller#updateScrollPosition}
 * <pre>
 *     // BEGIN - CFSRecyclerView
 *     if (mVerticalThumbHeight < mMinVerticalThumbHeight) {
 *         mVerticalThumbHeight = mMinVerticalThumbHeight;
 *     }
 *         // END - CFSRecyclerView
 * </pre>
 */
package com.hardbacknutter.nevertoomanybooks.widgets.cfs;
