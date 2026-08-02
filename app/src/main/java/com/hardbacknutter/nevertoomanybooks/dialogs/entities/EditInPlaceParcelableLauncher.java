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

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

/**
 * Launcher to edit-in-place a Parcelable object.
 *
 * @param <T> type of editable object
 *
 * @see EditParcelableLauncher
 */
public final class EditInPlaceParcelableLauncher<T extends Parcelable>
        extends DialogLauncher {

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
    public EditInPlaceParcelableLauncher(
            @NonNull final String requestKey,
            @NonNull final Supplier<DialogFragment> dialogSupplier,
            @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
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

        final EditParcelableInput<T> input = new EditParcelableInput<>(
                getRequestKey(), EditAction.Edit, item, null);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(listener, ERROR_NULL_LISTENER);

        final T data = Output.fromBundle(result);
        listener.onEdit(Objects.requireNonNull(data, Output.MODIFIED));
    }

    public static class Output<T extends Parcelable>
            implements LauncherOutput {

        private static final String MODIFIED = "modified";

        @NonNull
        private final T data;

        public Output(@NonNull final T data) {
            this.data = data;
        }

        @Nullable
        static <T extends Parcelable> T fromBundle(@NonNull final Bundle args) {
            //noinspection deprecation,unchecked
            return (T) args.getParcelable(MODIFIED);
        }

        @NonNull
        @Override
        public Bundle toBundle() {
            final Bundle args = new Bundle(1);
            args.putParcelable(MODIFIED, data);
            return args;
        }
    }

}
