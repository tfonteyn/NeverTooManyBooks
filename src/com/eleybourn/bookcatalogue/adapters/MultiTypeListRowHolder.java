/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.adapters;

import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;

/**
 * Holder for rows in a {@link MultiTypeListHandler}.
 *
 * Method names now mimic {@link androidx.recyclerview.widget.RecyclerView.ViewHolder}
 * but with creating View and Holder split. This is done to allow for example scaling
 * the view components before using them.
 * Example see: {@link BooksMultiTypeListHandler#getView(Cursor, LayoutInflater, View, ViewGroup)}
 * <p>
 * Intention is to use a Recycler ViewHolder eventually.
 */
public interface MultiTypeListRowHolder<T> {

    /**
     * Uses the passed type of the rowData to determine the kind of View that is required.
     *
     * @return a new view.
     */
    View onCreateView(@NonNull T rowData,
                      @NonNull LayoutInflater inflater,
                      @NonNull ViewGroup parent);

    /**
     * Setup a new holder. This holder will be associated with a reusable view that will
     * always be used for rows of the current kind.
     * We avoid having to call findViewById() by doing it once at creation time.
     *
     * @param rowData to read global info from in order to setup the holder.
     *                Do NOT use specific row date here.
     * @param rowView the view as created in {@link #onCreateView} for this row.
     */
    void onCreateViewHolder(@NonNull T rowData,
                            @NonNull View rowView);

    /**
     * @param rowData to fill in the actual data details for the current row.
     * @param rowView the view as created in {@link #onCreateView} for this row.
     */
    void onBindViewHolder(@NonNull T rowData,
                          @NonNull View rowView);
}
