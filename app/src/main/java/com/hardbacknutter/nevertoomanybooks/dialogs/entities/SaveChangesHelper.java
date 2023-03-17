/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.EntityBookLinksDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.MoveBooksDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

//TODO: this is rather hacky... needs to be refactored.
final class SaveChangesHelper {
    private static final String TAG = "SaveChangesHelper";

    private SaveChangesHelper() {
    }

    static <T extends Entity> boolean save(@NonNull final DialogFragment fragment,
                                           @NonNull final EntityBookLinksDao<T> dao,
                                           @NonNull final T item,
                                           final boolean nameChanged,
                                           @NonNull final Locale bookLocale,
                                           @NonNull final Consumer<T> onSuccess,
                                           @StringRes final int mergeMessageResId) {
        final Context context = fragment.requireContext();

        if (item.getId() > 0 && !nameChanged) {
            // It's an existing one and the name was not changed;
            // just update the other attributes
            try {
                dao.update(context, item, bookLocale);
                onSuccess.accept(item);
                return true;
            } catch (@NonNull final DaoWriteException e) {
                return false;
            }
        }

        // It's either a new one, or an existing one of which the name was changed.
        // Check if there is an another one with the same new name.
        final Optional<T> existingEntity = dao
                .findByName(context, item, () -> item.getLocale(context).orElse(bookLocale));

        if (existingEntity.isPresent()) {
            askToMerge(fragment, dao, item, onSuccess, mergeMessageResId, existingEntity.get());
        } else {
            try {
                if (item.getId() == 0) {
                    // it's an entirely new one; add it.
                    dao.insert(context, item, bookLocale);
                } else {
                    // no-one else with the same name; so we just update this one
                    dao.update(context, item, bookLocale);
                }
                onSuccess.accept(item);
                return true;

            } catch (@NonNull final DaoWriteException ignore) {
            }
        }
        return false;
    }

    static <T extends Entity> void askToMerge(@NonNull final DialogFragment fragment,
                                              @NonNull final MoveBooksDao<T> dao,
                                              @NonNull final T item,
                                              @NonNull final Consumer<T> onSuccess,
                                              final int mergeMessageResId,
                                              @NonNull final T target) {
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
                        // Note that we ONLY move the books. No other attributes from
                        // the source item are copied to the target item!
                        dao.moveBooks(context, item, target);

                        // return the item who 'lost' it's books
                        onSuccess.accept(item);
                    } catch (@NonNull final DaoWriteException e) {
                        LoggerFactory.getLogger().e(TAG, e);
                        StandardDialogs.showError(context, e);
                    }
                })
                .create()
                .show();
    }
}
