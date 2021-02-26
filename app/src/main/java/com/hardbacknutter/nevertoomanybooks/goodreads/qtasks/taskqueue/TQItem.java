/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin.ContextDialogItem;

/**
 * An TQItem *MUST* be serializable as we're going to store it in a database.
 * This means that it can not contain any references to UI components or similar objects.
 */
public interface TQItem
        extends Serializable {

    long getId();

    void setId(long id);

    @NonNull
    String getDescription(@NonNull Context context);

    /**
     * Called when an item in a list has been clicked, this method should populate the passed
     * 'menuItems' parameter with one {@link ContextDialogItem} per operation that can be
     * performed on this object.
     *
     * @param context   that can be used to get String resources for the menus
     * @param menuItems menu collection to fill
     * @param bookDao   Database Access
     */
    void addContextMenuItems(@NonNull Context context,
                             @NonNull List<ContextDialogItem> menuItems,
                             @NonNull BookDao bookDao);
}
