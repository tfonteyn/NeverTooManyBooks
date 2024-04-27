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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditAction;

/**
 * Launcher to edit a Parcelable object.
 *
 * @param <T> type of editable object
 */
public class EditParcelableLauncher<T extends Parcelable>
        extends DialogLauncher {

    private static final String TAG = "EditParcelableLauncher";

    /** Input value: the item we're going to edit. */
    public static final String BKEY_ITEM = TAG + ":item";

    private static final String MODIFIED = TAG + ":m";

    @Nullable
    private final OnAddListener<T> onAddListener;
    @Nullable
    private final OnEditListener<T> onEditListener;
    @Nullable
    private final OnModifiedListener<T> onEditInPlaceListener;

    /**
     * Constructor for doing {@link EditAction#Add} or {@link EditAction#Edit}.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param dialogSupplier a supplier for a new DialogFragment
     * @param onAddListener  results listener
     * @param onEditListener results listener
     */
    public EditParcelableLauncher(@NonNull final String requestKey,
                                  @NonNull final Supplier<DialogFragment> dialogSupplier,
                                  @NonNull final OnAddListener<T> onAddListener,
                                  @NonNull final OnEditListener<T> onEditListener) {
        super(requestKey, dialogSupplier);
        this.onAddListener = onAddListener;
        this.onEditListener = onEditListener;
        this.onEditInPlaceListener = null;
    }

    /**
     * Constructor for doing {@link EditAction#EditInPlace}.
     *
     * @param requestKey            FragmentResultListener request key to use for our response.
     * @param dialogSupplier        a supplier for a new DialogFragment
     * @param onEditInPlaceListener results listener
     */
    public EditParcelableLauncher(@NonNull final String requestKey,
                                  @NonNull final Supplier<DialogFragment> dialogSupplier,
                                  @NonNull final OnModifiedListener<T> onEditInPlaceListener) {
        super(requestKey, dialogSupplier);
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
     * Launch the dialog.
     *
     * @param action one of the {@link EditAction}s.
     * @param item   to edit
     */
    public void launch(@NonNull final EditAction action,
                       @NonNull final T item) {
        if (BuildConfig.DEBUG /* always */) {
            if (action == EditAction.EditInPlace && onEditInPlaceListener == null) {
                throw new IllegalArgumentException("EditInPlace missing onEditInPlaceListener");
            } else if (action == EditAction.Add && onAddListener == null) {
                throw new IllegalArgumentException("Add missing onAddListener");
            } else if (action == EditAction.Edit && onEditListener == null) {
                throw new IllegalArgumentException("Edit missing onEditListener");
            }
        }

        final Bundle args = new Bundle(3);
        args.putParcelable(EditAction.BKEY, action);
        args.putParcelable(BKEY_ITEM, item);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final EditAction action = Objects.requireNonNull(
                result.getParcelable(EditAction.BKEY), EditAction.BKEY);
        switch (action) {
            case Add:
                Objects.requireNonNull(onAddListener, "onAddListener");
                onAddListener.onAdd(
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
            case Edit:
                Objects.requireNonNull(onEditListener, "onEditListener");
                onEditListener.onEdit(
                        Objects.requireNonNull(result.getParcelable(BKEY_ITEM), BKEY_ITEM),
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
            case EditInPlace:
                Objects.requireNonNull(onEditInPlaceListener, "onEditInPlaceListener");
                onEditInPlaceListener.onModified(
                        Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
                break;
        }
    }

    @FunctionalInterface
    public interface OnAddListener<T> {

        /**
         * Callback handler - {@link EditAction#Add}.
         *
         * @param item the new item
         */
        void onAdd(@NonNull T item);
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

    @FunctionalInterface
    public interface OnModifiedListener<T> {

        /**
         * Callback handler - {@link EditAction#EditInPlace}.
         *
         * @param item the modified item
         */
        void onModified(@NonNull T item);
    }
}
