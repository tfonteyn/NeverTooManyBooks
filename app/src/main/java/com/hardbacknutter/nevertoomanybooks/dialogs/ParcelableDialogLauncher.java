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
 * <p>
 * {@link EditAction#Add}:
 * <ul>
 * <li>used for list-dialogs needing to add a NEW item to the list</li>
 * <li>the item is NOT stored in the database</li>
 * <li>returns the new item</li>
 * </ul>
 * <p>
 * {@link EditAction#Edit}:
 * <ul>
 * <li>used for list-dialogs needing to EDIT an existing item in the list</li>
 * <li>the modifications are NOT stored in the database</li>
 * <li>returns the original untouched + a new copy with the modifications</li>
 * </ul>
 *
 * @param <T> type of editable object
 */
public class ParcelableDialogLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "ParcelableDialogLauncher";

    private static final String MODIFIED = TAG + ":m";
    @NonNull
    private final ResultListener<T> resultListener;

    public ParcelableDialogLauncher(@NonNull final String requestKey,
                                    @NonNull final Supplier<DialogFragment> dialogSupplier,
                                    @NonNull final ResultListener<T> resultListener) {
        super(requestKey, dialogSupplier);
        this.resultListener = resultListener;
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

        // original can be null, modified cannot be null
        resultListener.onResult(result.getParcelable(BKEY_ITEM),
                                Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
    }

    @FunctionalInterface
    public interface ResultListener<T> {
        /**
         * Callback handler.
         *
         * @param original the original item; or {@code null} if we're adding a new item
         * @param modified the modified or new item
         */
        void onResult(@Nullable T original,
                      @NonNull T modified);
    }
}
