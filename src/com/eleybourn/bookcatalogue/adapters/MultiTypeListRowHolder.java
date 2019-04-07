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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * Holder for rows in a {@link MultiTypeListHandler}.
 *
 * @author Philip Warner
 */
public interface MultiTypeListRowHolder<T> {

    /**
     * Use the passed T to determine the kind of View that is required.
     *
     * @return a new view.
     */
    View createView(@NonNull T row,
                    @NonNull LayoutInflater inflater,
                    @NonNull ViewGroup parent);

    /**
     * Setup a new holder for row type based on the passed T. This holder will be
     * associated with a reusable view that will always be used for rows of the current
     * kind. We avoid having to call findViewById() by doing it once at creation time.
     */
    void map(@NonNull T row,
             @NonNull View convertView);

    /**
     * Use the passed T to fill in the actual details for the current row.
     */
    void set(@NonNull T row,
             @NonNull View view);
}
