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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public abstract class EditLauncher<T extends Parcelable>
        implements FragmentResultListener {

    private static final String TAG = "EditLauncher";
    private static final String ADD_NEW = TAG + ":n";
    private static final String ORIGINAL = TAG + ":o";
    private static final String MODIFIED = TAG + ":m";

    private String requestKeyName;
    private String requestKeyValue;
    private FragmentManager fragmentManager;

    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T item) {
        final Bundle result = new Bundle(1);
        result.putParcelable(ADD_NEW, item);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    public static <T extends Parcelable> void setResult(@NonNull final Fragment fragment,
                                                        @NonNull final String requestKey,
                                                        @NonNull final T original,
                                                        @NonNull final T modified) {
        final Bundle result = new Bundle(2);
        result.putParcelable(ORIGINAL, original);
        result.putParcelable(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    public abstract void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                                   @NonNull final String requestKeyValue,
                                                   @NonNull final LifecycleOwner lifecycleOwner);

    /**
     * Called from {@link #registerForFragmentResult(FragmentManager, String, LifecycleOwner)}.
     */
    protected void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                             @NonNull final String requestKeyName,
                                             @NonNull final String requestKeyValue,
                                             @NonNull final LifecycleOwner lifecycleOwner) {
        this.fragmentManager = fragmentManager;
        this.requestKeyName = requestKeyName;
        this.requestKeyValue = requestKeyValue;
        this.fragmentManager.setFragmentResultListener(this.requestKeyValue, lifecycleOwner, this);
    }

    public abstract void launch(@NonNull EditAction action,
                                @NonNull T item);

    /**
     * Launch the dialog. Called from {@link #launch(EditAction, Parcelable)}.
     *
     * @param fragment a new instance of the desired fragment to launch
     * @param action   add or edit
     * @param itemKey  the {@link DBKey} for the item
     * @param item     to edit
     */
    protected void launch(@NonNull final DialogFragment fragment,
                          @NonNull final EditAction action,
                          @NonNull final String itemKey,
                          @NonNull final T item) {
        final Bundle args = new Bundle(3);
        args.putString(requestKeyName, requestKeyValue);
        args.putParcelable(EditAction.BKEY, action);
        args.putParcelable(itemKey, item);

        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final T addNew = result.getParcelable(ADD_NEW);
        if (addNew != null) {
            onAdd(addNew);
        } else {
            onModified(Objects.requireNonNull(result.getParcelable(ORIGINAL), ORIGINAL),
                       Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
        }
    }

    /**
     * Callback handler - adding a new item.
     *
     * @param item the new item
     */
    public abstract void onAdd(@NonNull T item);

    /**
     * Callback handler - modifying an existing item.
     *
     * @param original the original item
     * @param modified the modified item
     */
    public abstract void onModified(@NonNull T original,
                                    @NonNull T modified);
}
