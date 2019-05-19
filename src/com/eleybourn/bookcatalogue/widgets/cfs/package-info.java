/**
 * Replacement for the old FastScrollListView which was ListView specific.
 * <p>
 * {@link com.eleybourn.bookcatalogue.widgets.cfs.CFSRecyclerView} extends RecyclerView.
 * <p>
 * FastScroller initialisation code lifted from "recyclerview-1.1.0-alpha01-sources.jar"
 * <br>
 * <br>1. xml attributes to enable the fast-scroller (and set the thumb etc)
 * <br>2. xml attribute to set the Overlay drawable; this does not enable it!
 * <br>
 * <br>3. Call RecyclerView#setAdapter with a 'normal' Adapter ==> independent from 1+2 above
 * <br>OR
 * <br>3. Call RecyclerView#setAdapter with an Adapter that implements
 * {@link com.eleybourn.bookcatalogue.widgets.FastScrollerOverlay.SectionIndexerV2}
 * The fast scroller will detect this and call
 * {@link com.eleybourn.bookcatalogue.widgets.cfs.CFSRecyclerView#drawIndexerOverlay}
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
 * {@link com.eleybourn.bookcatalogue.widgets.cfs.CFSFastScroller}
 * A modified copy of the original androidx.recyclerview.widget.FastScroller
 * <p>
 * Code taken from  "recyclerview-1.1.0-alpha05-sources.jar"
 * androidx/recyclerview/widget/FastScroller.java
 * <p>
 * Added this to the end of onDrawOver
 * <pre>
 *       // BEGIN - CFSRecyclerView
 *       if (mRecyclerView instanceof CFSRecyclerView) {
 *           if (isDragging()) {
 *               ((CFSRecyclerView) mRecyclerView).drawIndexerOverlay(canvas, state);
 *           }
 *       }
 *       // END - CFSRecyclerView
 * </pre>
 *
 * <p>
 * Minimal changes made to enhance this with ability to display an overlay.
 * see:
 * {@link com.eleybourn.bookcatalogue.widgets.cfs.CFSFastScroller#onDrawOver}
 * where the {@link com.eleybourn.bookcatalogue.widgets.cfs.CFSRecyclerView#drawIndexerOverlay}
 * is requested to draw the overlay.
 */
package com.eleybourn.bookcatalogue.widgets.cfs;
