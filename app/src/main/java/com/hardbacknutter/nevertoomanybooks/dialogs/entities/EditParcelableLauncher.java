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

    private static final String TAG = "EditParcelableLauncher";

    /** Input value: the item we're going to edit. */
    public static final String BKEY_ITEM = TAG + ":item";
    /**
     * Input value: the issn-8 code from a book.
     *
     * @see #add(Context, Parcelable, String)
     */
    public static final String BKEY_BOOK_ISSN = TAG + ":issn";

    /** Output value: the item with the edits. */
    private static final String MODIFIED = TAG + ":m";

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
     * @param action     {@link EditAction#Add} or {@link EditAction#Edit}
     * @param original   the original item
     * @param modified   the modified item
     *
     * @throws IllegalArgumentException for an invalid EditAction
     */
    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final EditAction action,
                                                        @NonNull final T original,
                                                        @NonNull final T modified) {
        if (BuildConfig.DEBUG /* always */) {
            if (action != EditAction.Add && action != EditAction.Edit) {
                throw new IllegalArgumentException("action must be Add or Edit");
            }
        }

        final Bundle result = new Bundle(3);
        result.putParcelable(EditAction.BKEY, action);
        result.putParcelable(BKEY_ITEM, original);
        result.putParcelable(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
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

        final Bundle args = new Bundle(4);
        args.putParcelable(EditAction.BKEY, EditAction.Add);
        args.putParcelable(BKEY_ITEM, item);
        if (bookIssn != null) {
            args.putString(BKEY_BOOK_ISSN, bookIssn);
        }

        showDialog(context, args);
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

        final Bundle args = new Bundle(4);
        args.putParcelable(EditAction.BKEY, EditAction.Edit);
        args.putParcelable(BKEY_ITEM, item);
        if (bookIssn != null) {
            args.putString(BKEY_BOOK_ISSN, bookIssn);
        }

        showDialog(context, args);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final EditAction action = Objects.requireNonNull(result.getParcelable(EditAction.BKEY),
                                                         EditAction.BKEY);
        switch (action) {
            case Add:
                Objects.requireNonNull(onAddListener, ERROR_NULL_ON_ADD_LISTENER);
                onAddListener.onAdd(
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
            case Edit:
                Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);
                onEditListener.onEdit(
                        Objects.requireNonNull(result.getParcelable(BKEY_ITEM), BKEY_ITEM),
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
            default:
                throw new IllegalStateException(
                        "EditInPlace must use EditInPlaceParcelableLauncher");
        }
    }
}
