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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * - used for direct/in-place editing of an existing item
 * - modifications ARE STORED in the database
 * - returns the modified item.
 */
public abstract class EditInPlaceParcelableLauncher<T extends Parcelable>
        extends EditLauncher {

    private static final String TAG = "EditInPlaceParcelableLa";
    private static final String MODIFIED = TAG + ":m";

    protected EditInPlaceParcelableLauncher(@NonNull final String requestKey,
                                            @NonNull final Supplier<DialogFragment> dialogSupplier) {
        super(requestKey, dialogSupplier);
    }


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
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(BKEY_ITEM, item);

        final DialogFragment dialogFragment = dialogFragmentSupplier.get();
        dialogFragment.setArguments(args);
        dialogFragment.show(fragmentManager, TAG);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        onModified(Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
    }

    /**
     * Callback handler.
     *
     * @param modified the modified item
     */
    public abstract void onModified(@NonNull final T modified);
}
