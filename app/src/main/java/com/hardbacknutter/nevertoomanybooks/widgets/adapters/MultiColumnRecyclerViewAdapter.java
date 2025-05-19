/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.widgets.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;

public abstract class MultiColumnRecyclerViewAdapter<HOLDER extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<HOLDER> {

    /** Cached inflater. */
    @NonNull
    private final LayoutInflater inflater;
    private final int columnCount;

    /**
     * Constructor.
     *
     * @param context     Current context
     * @param columnCount the number of columns to be used
     */
    protected MultiColumnRecyclerViewAdapter(@NonNull final Context context,
                                             final int columnCount) {
        this.inflater = LayoutInflater.from(context);
        this.columnCount = columnCount;
    }

    @NonNull
    protected LayoutInflater getInflater() {
        return inflater;
    }

    /**
     * Optionally adjust the margins of the columns if there is more than 1.
     * i.e. on larger displays.
     * <p>
     * Call from {@link #onCreateViewHolder(ViewGroup, int)}
     *
     * @param columnView to adjust
     */
    protected void adjustColumns(@NonNull final ViewGroup columnView) {
        if (columnCount > 1) {
            final GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                    columnView.getLayoutParams();
            final Resources res = columnView.getContext().getResources();
            lp.setMarginStart(res.getDimensionPixelSize(R.dimen.field_margin_start));
            lp.setMarginEnd(res.getDimensionPixelSize(R.dimen.field_margin_end));
            columnView.setLayoutParams(lp);
        }
    }

    protected int transpose(final int position) {
        final int realItemCount = getRealItemCount();
        final int rowCount = getRowCount(realItemCount);

        final int column = position % columnCount;
        final int row = position / columnCount;

        int listIndex = (column * rowCount) + row;

        if (listIndex >= realItemCount) {
            listIndex = RecyclerView.NO_POSITION;
        }

        return listIndex;
    }

    protected int revert(final int listIndex) {
        final int realItemCount = getRealItemCount();
        final int rowCount = getRowCount(realItemCount);

        final int column = listIndex % rowCount;
        final int row = listIndex / rowCount;

        return (column * columnCount) + row;
    }

    @SuppressWarnings("WeakerAccess")
    protected int getRowCount(final int realItemCount) {
        final int rowCount;
        if (realItemCount % columnCount == 0) {
            rowCount = realItemCount / columnCount;
        } else {
            rowCount = (realItemCount / columnCount) + 1;
        }
        return rowCount;
    }

    /**
     * Acts like the original getItemCount() method.
     *
     * @return the actual item count
     */
    protected abstract int getRealItemCount();

    /**
     * Return the <strong>CELL COUNT</strong> for the grid.
     *
     * @return cell count
     */
    @Override
    public int getItemCount() {
        final int realItemCount = getRealItemCount();
        final int rowCount = getRowCount(realItemCount);
        return rowCount * columnCount;
    }
}
