/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.dialogs;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.baseactivity.BindableItemListActivity;

/**
 * Class to make building a 'context menu' from an AlertDialog a little easier.
 * Used in {@link BindableItemListActivity} and related.
 *
 * Basically links a menu choice string to a Runnable
 *
 * Move code using this to {@link com.eleybourn.bookcatalogue.dialogs.picklist.SelectOneDialog.SimpleDialogMenuItem}
 *
 * @author Philip Warner
 */
@Deprecated
public class ContextDialogItem implements CharSequence {
    @NonNull
    public final Runnable handler;
    @NonNull
    private final String name;

    public ContextDialogItem(final @NonNull String name, final @NonNull Runnable handler) {
        this.name = name;
        this.handler = handler;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public char charAt(int index) {
        return name.charAt(index);
    }

    @Override
    public int length() {
        return name.length();
    }

    @NonNull
    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }
}

