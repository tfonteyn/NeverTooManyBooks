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

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.List;


/**
 * Class to make building a 'context menu' for an AlertDialog a little easier.
 * <p>
 * Basically links a menu choice string to a Runnable.
 *
 * @author Philip Warner
 */
public class ContextDialogItem
        implements CharSequence {

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
     * @param title Title of Alert
     * @param items Items to display
     */
    public static void showContextDialog(@NonNull final Context context,
                                         @StringRes final int title,
                                         @NonNull final List<ContextDialogItem> items) {
        if (items.size() > 0) {
            final ContextDialogItem[] itemArray = new ContextDialogItem[items.size()];
            items.toArray(itemArray);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setItems(itemArray, new DialogInterface.OnClickListener() {
                        public void onClick(@NonNull final DialogInterface dialog,
                                            final int which) {
                            itemArray[which].mHandler.run();
                        }
                    }).create();

            dialog.show();
        }
    }

    @NonNull
    @Override
    public String toString() {
        return mName;
    }

    @Override
    public char charAt(final int index) {
        return mName.charAt(index);
    }

    @Override
    public int length() {
        return mName.length();
    }

    @NonNull
    @Override
    public CharSequence subSequence(final int start,
                                    final int end) {
        return mName.subSequence(start, end);
    }
}

