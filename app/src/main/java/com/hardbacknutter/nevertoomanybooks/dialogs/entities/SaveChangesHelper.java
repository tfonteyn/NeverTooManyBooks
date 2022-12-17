/*
 * @Copyright 2018-2022 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.EntityBookLinksDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.MoveBooksDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

//TODO: class needs a better name
final class SaveChangesHelper {
    private static final String TAG = "SaveChangesHelper";

    private SaveChangesHelper() {
    }

    static <T extends Entity> boolean save(@NonNull final DialogFragment fragment,
                                           @NonNull final EntityBookLinksDao<T> dao,
                                           @NonNull final T item,
                                           final boolean nameChanged,
                                           @NonNull final Locale bookLocale,
                                           @NonNull final Consumer<Long> onSuccess,
                                           @StringRes final int mergeMessageResId) {
        final Context context = fragment.requireContext();

        if (item.getId() > 0 && !nameChanged) {
            // It's an existing one and the name was not changed;
            // just update the other attributes
            try {
                dao.update(context, item, bookLocale);
                onSuccess.accept(item.getId());
                return true;
            } catch (@NonNull final DaoWriteException e) {
                return false;
            }
        }

        // It's either a new one, or an existing one of which the name was changed.
        // Check if there is an another one with the same new name.
        final long existingId = dao.find(context, item, true, bookLocale);
        if (existingId == 0) {
            try {
                if (item.getId() == 0) {
                    // it's an entirely new one; add it.
                    dao.insert(context, item, bookLocale);
                } else {
                    // no-one else with the same name; so we just update this one
                    dao.update(context, item, bookLocale);
                }
                onSuccess.accept(item.getId());
                return true;

            } catch (@NonNull final DaoWriteException e) {
                return false;
            }
        }

        askToMerge(fragment, dao, item, onSuccess, mergeMessageResId, existingId);
        return false;
    }

    static <T extends Entity> void askToMerge(@NonNull final DialogFragment fragment,
                                              @NonNull final MoveBooksDao<T> dao,
                                              @NonNull final T item,
                                              @NonNull final Consumer<Long> onSuccess,
                                              final int mergeMessageResId,
                                              final long existingId) {
        final Context context = fragment.requireContext();
        // There is one with the same name; ask whether to merge the 2
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(item.getLabel(context))
                .setMessage(mergeMessageResId)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> {
                    fragment.dismiss();
                    try {
                        final T target = Objects.requireNonNull(dao.getById(existingId));

                        // Note that we ONLY move the books. No other attributes from
                        // the source item are copied to the target item!
                        dao.moveBooks(context, item, target);

                        // return the item who 'lost' it's books
                        onSuccess.accept(item.getId());
                    } catch (@NonNull final DaoWriteException e) {
                        Logger.error(TAG, e);
                        StandardDialogs.showError(context, R.string.error_storage_not_writable);
                    }
                })
                .create()
                .show();
    }
}