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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin.ContextDialogItem;

/**
 * An TQItem *MUST* be serializable as we're going to store it in a database.
 * This means that it can not contain any references to UI components or similar objects.
 */
public interface TQItem
        extends Serializable {

    long getId();

    void setId(long id);

    String getDescription(@NonNull Context context);

    /**
     * Called when an item in a list has been clicked, this method should populate the passed
     * 'menuItems' parameter with one {@link ContextDialogItem} per operation that can be
     * performed on this object.
     *
     * @param context   that can be used to get String resources for the menus
     * @param menuItems menu collection to fill
     * @param db        Database Access
     */
    void addContextMenuItems(@NonNull final Context context,
                             @NonNull final List<ContextDialogItem> menuItems,
                             @NonNull final DAO db);
}
