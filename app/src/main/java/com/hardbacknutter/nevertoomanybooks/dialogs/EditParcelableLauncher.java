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
public abstract class EditParcelableLauncher<T extends Parcelable>
        extends EditLauncher {

    private static final String TAG = "EditParcelableLauncher";
    private static final String ORIGINAL = TAG + ":o";
    private static final String MODIFIED = TAG + ":m";

    protected EditParcelableLauncher(@NonNull final String requestKey,
                                     @NonNull final Supplier<DialogFragment> dialogSupplier) {
        super(requestKey, dialogSupplier);
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
        result.putParcelable(EditAction.BKEY, EditAction.Add);
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
        result.putParcelable(EditAction.BKEY, EditAction.Edit);
        result.putParcelable(ORIGINAL, original);
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
        Objects.requireNonNull(fragmentManager, "fragmentManager");

        final Bundle args = new Bundle(3);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(EditAction.BKEY, action);
        args.putParcelable(BKEY_ITEM, item);

        final DialogFragment dialogFragment = dialogFragmentSupplier.get();
        dialogFragment.setArguments(args);
        dialogFragment.show(fragmentManager, TAG);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final EditAction action = Objects.requireNonNull(result.getParcelable(EditAction.BKEY),
                                                         EditAction.BKEY);
        switch (action) {
            case Add:
                onAdd(Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;

            case Edit:
                onModified(Objects.requireNonNull(result.getParcelable(ORIGINAL), ORIGINAL),
                           Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
        }
    }

    /**
     * Callback handler - {@link EditAction#Add}.
     *
     * @param item the new item
     */
    public void onAdd(@NonNull final T item) {
        throw new UnsupportedOperationException(EditAction.Add.name());
    }

    /**
     * Callback handler - {@link EditAction#Edit}.
     *
     * @param original the original item
     * @param modified the modified item
     */
    public void onModified(@NonNull final T original,
                           @NonNull final T modified) {
        throw new UnsupportedOperationException(EditAction.Edit.name());
    }
}
