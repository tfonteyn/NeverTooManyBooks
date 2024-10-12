/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.TopRowListPosition;
import com.hardbacknutter.nevertoomanybooks.booklist.header.HeaderAdapter;

/**
 * A delegate/wrapper for the book-list view handling all things related to positioning.
 * <p>
 * Takes care of conversions between adapter-position and layout-position
 * due to the {@link ConcatAdapter} used with the {@link HeaderAdapter}
 */
public class PositioningHelper {

    private final int headerRowCount;
    @NonNull
    private final RecyclerView recyclerView;

    /**
     * Constructor.
     *
     * @param recyclerView   to control
     * @param headerRowCount the number of rows used by the header
     */
    public PositioningHelper(@NonNull final RecyclerView recyclerView,
                             final int headerRowCount) {
        this.recyclerView = recyclerView;
        this.headerRowCount = headerRowCount;
    }

    /**
     * Find the View for the given adapter position.
     *
     * @param adapterPosition to lookup
     *
     * @return View
     */
    @Nullable
    public View findViewByAdapterPosition(final int adapterPosition) {
        //noinspection DataFlowIssue
        return recyclerView.getLayoutManager()
                           .findViewByPosition(adapterPosition + headerRowCount);
    }

    /**
     * Highlight (select) the current row and unhighlight (unselect) the previous one.
     *
     * @param previousAdapterPosition to un-select, can be {@code RecyclerView.NO_POSITION}
     * @param currentAdapterPosition  to select, can be {@code RecyclerView.NO_POSITION}
     */
    public void highlightSelection(final int previousAdapterPosition,
                                   final int currentAdapterPosition) {
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();

        if (previousAdapterPosition != RecyclerView.NO_POSITION) {
            if (previousAdapterPosition != currentAdapterPosition) {
                //noinspection DataFlowIssue
                final View view = layoutManager.findViewByPosition(
                        previousAdapterPosition + headerRowCount);
                if (view != null) {
                    view.setSelected(false);
                }
            }
        }

        if (currentAdapterPosition != RecyclerView.NO_POSITION) {
            //noinspection DataFlowIssue
            final View view = layoutManager.findViewByPosition(currentAdapterPosition
                                                               + headerRowCount);
            if (view != null) {
                view.setSelected(true);
            }
        }
    }

    /**
     * Retrieve the current adapter position and view-offset of the top-most visible row.
     *
     * @return adapter position of the top row
     */
    @NonNull
    public TopRowListPosition getTopRowPosition() {
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();

        //noinspection DataFlowIssue
        final int firstVisibleLayoutPos = layoutManager.findFirstVisibleItemPosition();
        if (firstVisibleLayoutPos == RecyclerView.NO_POSITION) {
            return new TopRowListPosition(0, 0);
        }

        // convert the layout position to the BookList adapter position.
        int adapterPosition = firstVisibleLayoutPos - headerRowCount;
        // can theoretically happen with an empty list which has a header
        if (adapterPosition < 0) {
            adapterPosition = 0;
        }

        final int viewOffset = getViewOffset(0);
        return new TopRowListPosition(adapterPosition, viewOffset);
    }

    private int getViewOffset(@SuppressWarnings("SameParameterValue") final int index) {
        final int viewOffset;
        // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
        final View topView = recyclerView.getChildAt(index);
        if (topView == null) {
            viewOffset = 0;
        } else {
            // currently our padding is 0, but this is future-proof
            final int paddingTop = recyclerView.getPaddingTop();
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    topView.getLayoutParams();
            viewOffset = topView.getTop() - lp.topMargin - paddingTop;
        }
        return viewOffset;
    }

    /**
     * Scroll the list to the given adapter-position/view-offset.
     *
     * @param adapterPosition to scroll to
     * @param viewOffset      to set
     * @param maxPosition     the last/maximum position to which we can scroll
     *                        (i.e. the length of the list)
     */
    public void scrollTo(final int adapterPosition,
                         final int viewOffset,
                         final int maxPosition) {
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();

        final int layoutPosition = adapterPosition + headerRowCount;

        // sanity check
        if (layoutPosition <= headerRowCount) {
            // Scroll to the top
            //noinspection DataFlowIssue
            layoutManager.scrollToPosition(0);

        } else if (layoutPosition >= maxPosition) {
            // The list is shorter than it used to be,
            // scroll to the end disregarding the offset
            //noinspection DataFlowIssue
            layoutManager.scrollToPosition(layoutPosition);
        } else {
            //noinspection DataFlowIssue
            layoutManager.scrollToPositionWithOffset(layoutPosition, viewOffset);
        }
    }

    /**
     * Scroll to the "best" of the given target nodes.
     * <p>
     * The {@link #recyclerView} <strong>MUST></strong> have been through a layout phase.
     * <p>
     * If the best node is currently on-screen, no scrolling will be done,
     * otherwise the list will be scrolled <strong>centering</strong> the best node.
     *
     * @param targetNodes candidates to scroll to.
     *
     * @return the node we ended up at.
     *
     * @throws IllegalArgumentException debug only: if the list had less then 2 nodes
     */
    @Nullable
    public BooklistNode scrollTo(@NonNull final List<BooklistNode> targetNodes) {
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();

        // the LAYOUT positions; i.e. including the header rows
        //noinspection DataFlowIssue
        final int firstLayoutPos = layoutManager.findFirstCompletelyVisibleItemPosition();
        final int lastLayoutPos = layoutManager.findLastCompletelyVisibleItemPosition();

        // Sanity check, should never happen... flw
        if (firstLayoutPos == RecyclerView.NO_POSITION) {
            return null;
        }

        // the ADAPTER positions; i.e. without the header rows
        final int firstAdapterPos = Math.max(0, firstLayoutPos - headerRowCount);
        final int lastAdapterPos = Math.max(0, lastLayoutPos - headerRowCount);
        final int centerPos = Math.max(0, (firstAdapterPos + lastAdapterPos) / 2);

        BooklistNode best;
        if (targetNodes.size() == 1) {
            // There is only one, so it's always the "best"
            best = targetNodes.get(0);

        } else {
            // Find the target which is closest to the current centered position
            // Assume first is best
            best = targetNodes.get(0);
            // distance from currently visible center row
            int distance = Math.abs(best.getAdapterPosition() - centerPos);
            // Loop all other rows, looking for a nearer one
            int row = 1;
            while (distance > 0 && row < targetNodes.size()) {
                final BooklistNode node = targetNodes.get(row);
                final int newDist = Math.abs(node.getAdapterPosition() - centerPos);
                if (newDist < distance) {
                    distance = newDist;
                    best = node;
                }
                row++;
            }
        }

        final int destPos = best.getAdapterPosition();

        // TODO: decide whether we want to CENTER the destination,
        //  or just scroll until it's in view at the top or bottom.
        //  This code should be enabled to CENTER
        //        if (destPos > lastAdapterPos) {
        //            // The destination is offscreen at the bottom.
        //            // Scroll up until the destPos is centered
        //            final int diff = lastAdapterPos - centerPos;
        //            destPos += diff;
        //        }

        // If the destination is off-screen, scroll it into view
        if (destPos < firstAdapterPos) {
            // Offscreen before "first"
            // back to LAYOUT position by adding the header
            layoutManager.scrollToPosition(destPos + headerRowCount);
        } else if (destPos > lastAdapterPos) {
            // URGENT: this is based on the list BEFORE covers are shown
            //  hence... the lastAdapterPos is TOO HIGH
            // Offscreen after "last"
            layoutManager.scrollToPosition(destPos + 1 + headerRowCount);
        }

        return best;
    }
}
