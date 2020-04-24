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

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Class to make building a 'context menu' for an AlertDialog a little easier.
 * <p>
 * Basically links a menu choice string to a Runnable.
 */
public class ContextDialogItem
        implements CharSequence {

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Runnable mHandler;
    @NonNull
    private final String mName;

    public ContextDialogItem(@NonNull final String name,
                             @NonNull final Runnable handler) {
        mName = name;
        mHandler = handler;
    }

    /**
     * Displays an array of ContextDialogItems in an alert.
     *
     * @param context Current context
     * @param items   List of items to display
     */
    static void showContextDialog(@NonNull final Context context,
                                  @NonNull final Collection<ContextDialogItem> items) {
        if (!items.isEmpty()) {
            final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
            items.toArray(itemArray);
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_select_an_action)
                    .setItems(itemArray, (d, which) -> itemArray[which].mHandler.run())
                    .create()
                    .show();
        }
    }

    /**
     * NOT debug!
     * Returns the name of the item to display in the AlertDialog.
     *
     * @return the name
     */
    @NonNull
    @Override
    public String toString() {
        return mName;
    }

    @Override
    public int length() {
        return mName.length();
    }

    @Override
    public char charAt(final int index) {
        return mName.charAt(index);
    }

    @NonNull
    @Override
    public CharSequence subSequence(final int start,
                                    final int end) {
        return mName.subSequence(start, end);
    }
}

