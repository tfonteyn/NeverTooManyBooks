/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

/**
 * Launcher to edit-in-place a Parcelable object.
 *
 * @param <T> type of editable object
 *
 * @see EditParcelableLauncher
 */
public final class EditInPlaceParcelableLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "EditInPlacePL";

    // Input value: the item we're going to edit
    // uses the shared key: EditParcelableLauncher.BKEY_ITEM
    // as we're using the SAME viewmodel.

    /** Output value: the (same) item with the edits. */
    private static final String MODIFIED = TAG + ":m";

    private static final String ERROR_NULL_LISTENER = "onEditInPlaceListener";

    @Nullable
    private OnEditInPlaceListener<T> listener;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     */
    public EditInPlaceParcelableLauncher(@NonNull final String requestKey,
                                         @NonNull final Supplier<DialogFragment> dialogSupplier,
                                         @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
    }

    /**
     * Set the result.
     *
     * @param <T>        type of the item
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param modified   the modified item
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T modified) {
        final Bundle result = new Bundle(2);
        result.putParcelable(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Set the listener which will be used by {@link #edit(Context, Parcelable)}.
     *
     * @param listener to use
     */
    public void setListener(@NonNull final OnEditInPlaceListener<T> listener) {
        this.listener = listener;
    }

    /**
     * Launch the dialog for an edit-in-place-operation.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param item    to edit
     *
     * @throws NullPointerException if there is no {@link OnEditInPlaceListener} set
     */
    public void edit(@NonNull @UiContext final Context context,
                     @NonNull final T item) {
        Objects.requireNonNull(listener, ERROR_NULL_LISTENER);

        final Bundle args = new Bundle(2);
        args.putParcelable(EditParcelableLauncher.BKEY_ITEM, item);
        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {

        Objects.requireNonNull(listener, ERROR_NULL_LISTENER);
        listener.onEdit(
                Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
    }
}
