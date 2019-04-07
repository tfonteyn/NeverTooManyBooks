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
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.cursors.BookCursorRowBase;

/**
 * Interface for handling the View-related tasks in a multi-type ListView.
 *
 * @author Philip Warner
 */
public interface MultiTypeListHandler {

    /**
     * Return the view type that will be used for any row of the type represented by
     * the current cursor position.
     *
     * @param cursor Cursor position at representative row.
     *
     * @return view type
     */
    int getItemViewType(@NonNull Cursor cursor);

    /**
     * @return the total number of view types that can be returned.
     */
    int getViewTypeCount();

    /**
     * Create a new view and fill it in with details pointed to by the current cursor. The
     * convertView parameter (if not null) points to a reusable view of the right type.
     *
     * @param cursor      Cursor, positioned at current row
     * @param inflater    Inflater to use in case a new view resource must be expanded
     * @param convertView Pointer to reusable view of correct type (may be null)
     * @param parent      Parent view group
     *
     * @return Filled-in view to use.
     */
    @NonNull
    View getView(@NonNull Cursor cursor,
                 @NonNull LayoutInflater inflater,
                 @Nullable View convertView,
                 @NonNull ViewGroup parent);

    /**
     * Get the text to display in ListView for row at current cursor position.
     *
     * @param cursor Cursor, correctly positioned.
     *
     * @return text to display
     */
    String[] getSectionText(@NonNull Cursor cursor);
}
