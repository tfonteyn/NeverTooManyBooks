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

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditAction;

/**
 * Launcher to edit a Parcelable object.
 *
 * @param <T> type of editable object
 */
public final class EditParcelableLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "EditParcelableLauncher";

    /** Input value: the item we're going to edit. */
    public static final String BKEY_ITEM = TAG + ":item";

    private static final String MODIFIED = TAG + ":m";

    private static final String ERROR_NULL_ON_ADD_LISTENER = "onAddListener";
    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";
    private static final String ERROR_NULL_ON_EDIT_IN_PLACE_LISTENER = "onEditInPlaceListener";

    @Nullable
    private final OnAddListener<T> onAddListener;
    @Nullable
    private final OnEditListener<T> onEditListener;
    @Nullable
    private final OnEditInPlaceListener<T> onEditInPlaceListener;

    /**
     * Constructor for doing {@link EditAction#Add} or {@link EditAction#Edit}.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     * @param onAddListener       results listener
     * @param onEditListener      results listener
     */
    public EditParcelableLauncher(@NonNull final String requestKey,
                                  @NonNull final Supplier<DialogFragment> dialogSupplier,
                                  @NonNull final Supplier<DialogFragment> bottomSheetSupplier,
                                  @Nullable final OnAddListener<T> onAddListener,
                                  @Nullable final OnEditListener<T> onEditListener) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
        this.onAddListener = onAddListener;
        this.onEditListener = onEditListener;
        this.onEditInPlaceListener = null;
    }

    /**
     * Constructor for doing {@link EditAction#EditInPlace}.
     *
     * @param requestKey            FragmentResultListener request key to use for our response.
     * @param dialogSupplier        a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier   a supplier for a new BottomSheetDialogFragment.
     * @param onEditInPlaceListener results listener
     */
    public EditParcelableLauncher(@NonNull final String requestKey,
                                  @NonNull final Supplier<DialogFragment> dialogSupplier,
                                  @NonNull final Supplier<DialogFragment> bottomSheetSupplier,
                                  @NonNull final OnEditInPlaceListener<T> onEditInPlaceListener) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
        this.onAddListener = null;
        this.onEditListener = null;
        this.onEditInPlaceListener = onEditInPlaceListener;
    }

    /**
     * Set the result for {@link EditAction#EditInPlace}.
     *
     * @param <T>        type of the item
     * @param fragment   the fragment returning a result
     * @param requestKey as received in the constructor
     * @param modified   the modified item
     */
    public static <T extends Parcelable> void setEditInPlaceResult(@NonNull final Fragment fragment,
                                                                   @NonNull final String requestKey,
                                                                   @NonNull final T modified) {
        final Bundle result = new Bundle(3);
        result.putParcelable(EditAction.BKEY, EditAction.EditInPlace);
        result.putParcelable(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Set the result for {@link EditAction#Add} or {@link EditAction#Edit}.
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
     * Launch the dialog for an add-operation.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param item    to edit
     */
    public void add(@NonNull final Context context,
                    @NonNull final T item) {
        Objects.requireNonNull(onAddListener, ERROR_NULL_ON_ADD_LISTENER);

        final Bundle args = new Bundle(3);
        args.putParcelable(EditAction.BKEY, EditAction.Add);
        args.putParcelable(BKEY_ITEM, item);

        showDialog(context, args);
    }

    /**
     * Launch the dialog for an edit-operation.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param item    to edit
     */
    public void edit(@NonNull final Context context,
                     @NonNull final T item) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle(3);
        args.putParcelable(EditAction.BKEY, EditAction.Edit);
        args.putParcelable(BKEY_ITEM, item);

        showDialog(context, args);
    }

    /**
     * Launch the dialog for an edit-in-place-operation.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param item    to edit
     */
    public void editInPlace(@NonNull final Context context,
                            @NonNull final T item) {
        Objects.requireNonNull(onEditInPlaceListener, ERROR_NULL_ON_EDIT_IN_PLACE_LISTENER);

        final Bundle args = new Bundle(3);
        args.putParcelable(EditAction.BKEY, EditAction.EditInPlace);
        args.putParcelable(BKEY_ITEM, item);

        showDialog(context, args);
    }

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
            case EditInPlace:
                Objects.requireNonNull(onEditInPlaceListener, ERROR_NULL_ON_EDIT_IN_PLACE_LISTENER);
                onEditInPlaceListener.onEdit(
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
        }
    }
}
