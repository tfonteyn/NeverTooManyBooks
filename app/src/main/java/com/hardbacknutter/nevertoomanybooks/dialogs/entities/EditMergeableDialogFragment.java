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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.MoveBooksDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public abstract class EditMergeableDialogFragment<T extends Entity>
        extends FFBaseDialogFragment {

    private static final String TAG = "EditMergeableDialogFrag";

    EditMergeableDialogFragment(final int fullscreenLayoutId,
                                final int contentLayoutId) {
        super(fullscreenLayoutId, contentLayoutId);
    }

    void askToMerge(@NonNull final MoveBooksDao<T> dao,
                    @StringRes final int mergeMessageResId,
                    @NonNull final T source,
                    @NonNull final T target,
                    @NonNull final Consumer<T> onSuccess) {

        final Context context = getContext();
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(source.getLabel(context))
                .setMessage(mergeMessageResId)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> {
                    // Close the fragment here!
                    dismiss();
                    try {
                        // Note that we ONLY move the books. No other attributes from
                        // the source item are copied to the target item!
                        dao.moveBooks(context, source, target);

                        // return the item which 'lost' it's books
                        onSuccess.accept(source);
                    } catch (@NonNull final DaoWriteException e) {
                        // log, but ignore - should never happen unless disk full
                        LoggerFactory.getLogger().e(TAG, e, source);
                    }
                })
                .create()
                .show();
    }
}
