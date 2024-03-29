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
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.bookedit.EditAction;

/**
 * Launcher to edit a Parcelable object.
 *
 * @param <T> type of editable object
 */
public class ParcelableDialogLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "ParcelableDialogLauncher";

    private static final String MODIFIED = TAG + ":m";
    @NonNull
    private final OnAddListener<T> onAddListener;
    private final OnEditListener<T> onEditListener;

    public ParcelableDialogLauncher(@NonNull final String requestKey,
                                    @NonNull final Supplier<DialogFragment> dialogSupplier,
                                    @NonNull final OnAddListener<T> onAddListener,
                                    @NonNull final OnEditListener<T> onEditListener) {
        super(requestKey, dialogSupplier);
        this.onAddListener = onAddListener;
        this.onEditListener = onEditListener;
    }

    /**
     * Set the result for an {@link EditAction#Add}.
     *
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param item       which was added
     * @param <T>        type of the item
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T item) {
        final Bundle result = new Bundle(2);
        result.putParcelable(MODIFIED, item);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Set the result for an {@link EditAction#Edit}.
     *
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param original   the original item
     * @param modified   the modified item
     * @param <T>        type of the item
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T original,
                                                        @NonNull final T modified) {
        final Bundle result = new Bundle(3);
        result.putParcelable(BKEY_ITEM, original);
        result.putParcelable(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param action one of the {@link EditAction}s.
     * @param item   to edit
     */
    public void launch(@NonNull final EditAction action,
                       @NonNull final T item) {
        final Bundle args = new Bundle(3);
        args.putParcelable(EditAction.BKEY, action);
        args.putParcelable(BKEY_ITEM, item);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {

        @Nullable
        final T original = result.getParcelable(BKEY_ITEM);
        if (original == null) {
            onAddListener.onAdd(Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
        } else {
            onEditListener.onEdit(original,
                                  Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
        }
    }

    @FunctionalInterface
    public interface OnAddListener<T> {

        /**
         * Callback handler - {@link EditAction#Add}.
         *
         * @param item the new item
         */
        void onAdd(@NonNull final T item);
    }

    @FunctionalInterface
    public interface OnEditListener<T> {

        /**
         * Callback handler - {@link EditAction#Edit}.
         *
         * @param original the original item
         * @param modified the modified item
         */
        void onEdit(@NonNull T original,
                    @NonNull T modified);
    }
}
