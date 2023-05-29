/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        final int itemCount = getRealItemCount();
        final int rowCount = getRowCount(itemCount);
        return rowCount * columnCount;
    }
}
