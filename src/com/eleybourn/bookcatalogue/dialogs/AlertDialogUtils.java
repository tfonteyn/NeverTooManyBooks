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
package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities related to building an AlertDialog that is just a list of clickable options.
 *
 * @author Philip Warner
 */
public class AlertDialogUtils {
    private AlertDialogUtils() {
    }

    /**
     * Utility routine to display an array of ContextDialogItems in an alert.
     *
     * @param title Title of Alert
     * @param items Items to display
     */
    public static void showContextDialogue(@NonNull final Context context,
                                           @NonNull final String title,
                                           @NonNull final List<AlertDialogItem> items) {
        if (items.size() > 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(title);

            final AlertDialogItem[] itemArray = new AlertDialogItem[items.size()];
            items.toArray(itemArray);

            builder.setItems(itemArray, new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog, final int item) {
                    itemArray[item].handler.run();
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    /**
     * Class to make building a 'context menu' from an AlertDialog a little easier.
     * Used in Event.buildDialogItems and related Activities.
     *
     * @author Philip Warner
     */
    public static class AlertDialogItem implements CharSequence {
        public final String name;
        public final Runnable handler;

        public AlertDialogItem(@NonNull final String name, @NonNull final Runnable handler) {
            this.name = name;
            this.handler = handler;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }

        @Override
        public char charAt(final int index) {
            return name.charAt(index);
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return name.subSequence(start, end);
        }
    }

}
