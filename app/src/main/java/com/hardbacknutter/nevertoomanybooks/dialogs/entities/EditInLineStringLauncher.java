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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.OnEditListener;

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

    private static final String TAG = "Launcher";

    /** Input value: the text (String) to edit. */
    static final String BKEY_TEXT = TAG + ":text";

    /** Return value: the modified text. */
    private static final String MODIFIED = TAG + ":m";

    @NonNull
    private final OnEditListener<String> onEditListener;

    /**
     * Constructor.
     *
     * @param requestKey          FragmentResultListener request key to use for our response.
     *                            Typically the {@code DBKey} for the column we're editing.
     * @param dialogSupplier      a supplier for a new plain DialogFragment
     * @param bottomSheetSupplier a supplier for a new BottomSheetDialogFragment.
     * @param onEditListener      callback for results
     */
    public EditInLineStringLauncher(@NonNull final String requestKey,
                                     @NonNull final Supplier<DialogFragment> dialogSupplier,
                                     @NonNull final Supplier<DialogFragment> bottomSheetSupplier,
                                    @NonNull final OnEditListener<String> onEditListener) {
        super(requestKey, dialogSupplier, bottomSheetSupplier);
        this.onEditListener = onEditListener;
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
        result.putString(BKEY_TEXT, original);
        result.putString(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param text    to edit.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String text) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_TEXT, text);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        onEditListener.onEdit(Objects.requireNonNull(result.getString(BKEY_TEXT), BKEY_TEXT),
                              Objects.requireNonNull(result.getString(MODIFIED), MODIFIED));
    }
}
