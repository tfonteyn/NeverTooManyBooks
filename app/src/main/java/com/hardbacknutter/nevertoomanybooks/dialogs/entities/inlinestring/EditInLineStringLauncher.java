/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.inlinestring;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.OnEditListener;

/**
 * Launcher for one of the inline-string fields in the Books table.
 * <ul>
 * <li>used for direct/in-place editing of an inline field text; e.g. Book Color, Format...</li>
 * <li>modifications <strong>ARE STORED</strong> in the database</li>
 * <li>returns the original and the modified/stored text</li>
 * </ul>
 */
public class EditInLineStringLauncher
        extends DialogLauncher {

    private static final String TAG = "EditILStringLauncher";
    /** Input value: the text (String) to edit. */
    static final String BKEY_ITEM = TAG + ":item";
    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";
    /** Return value: the modified text. */
    private static final String MODIFIED = TAG + ":m";

    @Nullable
    private OnEditListener<String> onEditListener;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     *                            Typically the {@code DBKey} for the column we're editing.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     */
    public EditInLineStringLauncher(@NonNull final String requestKey,
                                    @NonNull final Supplier<DialogFragment> dialogSupplier,
                                    @NonNull final Supplier<DialogFragment> bottomSheetSupplier) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param original   the original text which was passed in to be edited
     * @param modified   the modified text
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final String original,
                          @NonNull final String modified) {
        final Bundle result = new Bundle(2);
        result.putString(BKEY_ITEM, original);
        result.putString(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    public void setOnEditListener(@NonNull final OnEditListener<String> listener) {
        this.onEditListener = listener;
    }

    /**
     * Launch the dialog.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param text    to edit.
     */
    public void edit(@NonNull final Context context,
                     @NonNull final String text) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle(2);
        args.putString(BKEY_ITEM, text);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(onEditListener, ERROR_NULL_ON_EDIT_LISTENER);

        onEditListener.onEdit(Objects.requireNonNull(result.getString(BKEY_ITEM), BKEY_ITEM),
                              Objects.requireNonNull(result.getString(MODIFIED), MODIFIED));
    }
}
