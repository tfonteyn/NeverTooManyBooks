/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DAO;

public interface BindableItem<
        BICursor extends BindableItemCursor,
        BIViewHolder extends BindableItemViewHolder> {

    /**
     * Get the row id for this item.
     *
     * @return the row id
     */
    long getId();

    /**
     * Get a new BindableItemViewHolder object suitable for displaying this type of object.
     *
     * @param parent ViewGroup that will contain the new View.
     *
     * @return a new BindableItemViewHolder
     */
    @NonNull
    BIViewHolder onCreateViewHolder(@NonNull ViewGroup parent);

    /**
     * Bind this object to the passed holder.
     *
     * @param holder to bind
     * @param row    row for this item in the cursor.
     * @param db     Database Access
     */
    void onBindViewHolder(@NonNull BIViewHolder holder,
                          @NonNull BICursor row,
                          @NonNull DAO db);

    /**
     * Called when an item in a list has been clicked, this method should populate the passed
     * 'items' parameter with one {@link ContextDialogItem} per operation that can be
     * performed on this object.
     *
     * @param context   that can be used to get String resources for the menus
     * @param menuItems menu collection to fill
     * @param db        Database Access
     */
    void addContextMenuItems(@NonNull Context context,
                             @NonNull List<ContextDialogItem> menuItems,
                             @NonNull DAO db);
}
