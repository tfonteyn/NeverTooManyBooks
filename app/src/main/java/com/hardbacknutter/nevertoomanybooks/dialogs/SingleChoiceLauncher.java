/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class SingleChoiceLauncher<T extends Parcelable & Entity>
        extends DialogLauncher {

    private static final String TAG = "SingleChoiceLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    static final String BKEY_SELECTED = TAG + ":selected";
    static final String BKEY_ALL_IDS = TAG + ":ids";
    static final String BKEY_ALL_LABELS = TAG + ":labels";
    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     * <p>
     * FIXME: currently not in use. When used, we need to review using a select/cancel
     * button for the dialog/bottomsheet.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    public SingleChoiceLauncher(@NonNull final String requestKey,
                                @NonNull final ResultListener resultListener) {
        super(requestKey,
              SingleChoiceDialogFragment::new,
              SingleChoiceBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment     the calling DialogFragment
     * @param requestKey   to use
     * @param selectedItem the  <strong>checked</strong> item, can be {@code null} for none.
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @Nullable final Long selectedItem) {
        final Bundle result = new Bundle(1);
        if (selectedItem != null) {
            result.putLong(BKEY_SELECTED, selectedItem);
        }
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param context      preferably the {@code Activity}
     *                     but another UI {@code Context} will also do.
     * @param dialogTitle  the dialog title
     * @param allItems     list of all possible items
     * @param selectedItem item which is currently selected; can be {@code null} for none
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @NonNull final List<T> allItems,
                       @Nullable final T selectedItem) {

        final Bundle args = new Bundle(5);
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);

        args.putLongArray(BKEY_ALL_IDS, allItems
                .stream().mapToLong(Entity::getId).toArray());
        args.putStringArray(BKEY_ALL_LABELS, allItems
                .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

        if (selectedItem != null) {
            args.putLong(BKEY_SELECTED, selectedItem.getId());
        }

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(result.getLong(BKEY_SELECTED));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param selectedItem the <strong>checked</strong> item, can be {@code null} for none.
         */
        void onResult(@Nullable Long selectedItem);
    }
}
