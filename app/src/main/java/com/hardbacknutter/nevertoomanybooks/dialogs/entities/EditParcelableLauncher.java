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

/**
 * Launcher to add or edit a Parcelable object.
 *
 * @param <T> type of editable object
 *
 * @see EditInPlaceParcelableLauncher
 */
public final class EditParcelableLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String ERROR_NULL_ON_ADD_LISTENER = "onAddListener";
    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private OnAddListener<T> onAddListener;
    @Nullable
    private OnEditListener<T> onEditListener;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     */
    public EditParcelableLauncher(@NonNull final String requestKey,
                                  @NonNull final Supplier<DialogFragment> dialogSupplier,
                                  @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
    }

    /**
     * Set the listener which will be used by {@link #add(Context, Parcelable, String)}.
     *
     * @param listener to use
     */
    public void setOnAddListener(@NonNull final OnAddListener<T> listener) {
        this.onAddListener = listener;
    }

    /**
     * Set the listener which will be used by {@link #edit(Context, Parcelable, String)}.
     *
     * @param listener to use
     */
    public void setOnEditListener(@NonNull final OnEditListener<T> listener) {
        this.onEditListener = listener;
    }

    /**
     * Launch the dialog for an add-operation.
     *
     * @param context  preferably the {@code Activity}
     *                 but another UI {@code Context} will also do.
     * @param item     to edit
     * @param bookIssn (optional) whether to show the ISSN edit field or hide it.
     *                 Will be ignored by Book-Author/Book-Publisher; pass in {@code null}.
     *                 Book-Series: the ISSN-8 code string from the book,
     *                 or {@code null} if the code is absent or not an ISSN
     *
     * @throws NullPointerException if there is no {@link OnAddListener} set
     */
    public void add(@NonNull @UiContext final Context context,
                    @NonNull final T item,
                    @Nullable final String bookIssn) {
        Objects.requireNonNull(onAddListener, ERROR_NULL_ON_ADD_LISTENER);

        final EditParcelableInput<T> input = new EditParcelableInput<>(
                getRequestKey(), EditAction.Add, item, bookIssn);
        showDialog(context, input.toBundle());
    }

    /**
     * Launch the dialog for an edit-operation.
     *
     * @param context  preferably the {@code Activity}
     *                 but another UI {@code Context} will also do.
     * @param item     to edit
     * @param bookIssn (optional) whether to show the ISSN edit field or hide it.
     *                 Will be ignored by Book-Author/Book-Publisher; pass in {@code null}.
     *                 Book-Series: the ISSN-8 code string from the book,
     *                 or {@code null} if the code is absent or not an ISSN
     *
     * @throws NullPointerException if there is no {@link OnEditListener} set
     */
    public void edit(@NonNull @UiContext final Context context,
                     @NonNull final T item,
                     @Nullable final String bookIssn) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        final EditParcelableInput<T> input = new EditParcelableInput<>(
                getRequestKey(), EditAction.Edit, item, bookIssn);
        showDialog(context, input.toBundle());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {

        final EditParcelableOutput<T> output = EditParcelableOutput.fromBundle(result);
        switch (output.getAction()) {
            case Add:
                Objects.requireNonNull(onAddListener, ERROR_NULL_ON_ADD_LISTENER);
                onAddListener.onAdd(output.getEdited());
                break;
            case Edit:
                Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);
                onEditListener.onEdit(output.getOriginal(), output.getEdited());
                break;
            default:
                throw new IllegalStateException(
                        "EditInPlace must use EditInPlaceParcelableLauncher");
        }
    }
}
