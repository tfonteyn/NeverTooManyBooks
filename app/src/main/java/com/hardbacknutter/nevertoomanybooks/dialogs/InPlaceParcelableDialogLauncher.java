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

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Launcher to edit an in-place Parcelable object.
 *
 * @param <T> type of editable object
 */
public class InPlaceParcelableDialogLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "EditInPlaceParcelableLa";

    private static final String MODIFIED = TAG + ":m";

    @NonNull
    private final ResultListener<T> resultListener;

    public InPlaceParcelableDialogLauncher(@NonNull final String requestKey,
                                           @NonNull final Supplier<DialogFragment> dialogSupplier,
                                           @NonNull final ResultListener<T> resultListener) {
        super(requestKey, dialogSupplier);
        this.resultListener = resultListener;
    }

    /**
     * Set the result.
     *
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param item       which was added
     * @param <T>        type of the item
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T item) {
        final Bundle result = new Bundle(1);
        result.putParcelable(MODIFIED, item);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param item to edit
     */
    public void launch(@NonNull final T item) {
        final Bundle args = new Bundle(2);
        args.putParcelable(BKEY_ITEM, item);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
    }

    @FunctionalInterface
    public interface ResultListener<T> {

        /**
         * Callback handler.
         *
         * @param item the modified item
         */
        void onResult(@NonNull T item);
    }
}
