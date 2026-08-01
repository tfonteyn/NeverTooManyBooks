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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
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
     * Set the result.
     *
     * @param <T>        type of the item
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param output     result
     *
     * @throws IllegalArgumentException for an invalid EditAction
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final Output<T> output) {
        if (BuildConfig.DEBUG /* always */) {
            if (output.getAction() != EditAction.Add && output.getAction() != EditAction.Edit) {
                throw new IllegalArgumentException("action must be Add or Edit");
            }
        }

        fragment.getParentFragmentManager().setFragmentResult(requestKey, output.toBundle());
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

        final Output<T> output = Output.fromBundle(result);
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

    public static class Output<T extends Parcelable> {
        private static final String TAG = "Output";
        private static final String BKEY_ORIGINAL = TAG + ":original";
        private static final String BKEY_EDIT = TAG + ":edit";

        @NonNull
        private final EditAction action;
        @NonNull
        private final T original;
        @NonNull
        private final T edited;

        /**
         * Constructor.
         *
         * @param action   EditAction
         * @param original the previous value
         * @param edited   the new value
         */
        public Output(@NonNull final EditAction action,
                      @NonNull final T original,
                      @NonNull final T edited) {
            this.action = action;
            this.original = original;
            this.edited = edited;
        }

        @SuppressWarnings("deprecation")
        @NonNull
        static <T extends Parcelable> Output<T> fromBundle(@NonNull final Bundle args) {
            final EditAction action = Objects.requireNonNull(
                    args.getParcelable(EditAction.BKEY), EditAction.BKEY);
            final T previousValue = Objects.requireNonNull(
                    args.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL);
            final T currentValue = Objects.requireNonNull(
                    args.getParcelable(BKEY_EDIT), BKEY_EDIT);

            return new Output<>(action, previousValue, currentValue);
        }

        @NonNull
        Bundle toBundle() {
            final Bundle result = new Bundle(3);
            result.putParcelable(EditAction.BKEY, action);
            result.putParcelable(BKEY_ORIGINAL, original);
            result.putParcelable(BKEY_EDIT, edited);

            return result;
        }

        @NonNull
        EditAction getAction() {
            return action;
        }

        @NonNull
        T getOriginal() {
            return original;
        }

        @NonNull
        T getEdited() {
            return edited;
        }
    }
}
