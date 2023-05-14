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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Launcher for one of the inline-string fields in the Books table.
 * <ul>
 * <li>used for direct/in-place editing of an inline field text</li>
 * <li>modifications ARE STORED in the database</li>
 * <li>returns the modified text (and the original)</li>
 * </ul>
 */
public class EditStringLauncher
        extends EditLauncher {

    private static final String TAG = "EditStringLauncher";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private static final String ORIGINAL = TAG + ":o";
    private static final String MODIFIED = TAG + ":m";
    @NonNull
    private final OnModifiedCallback onModifiedCallback;

    public EditStringLauncher(@NonNull final String requestKey,
                              @NonNull final Supplier<DialogFragment> dialogSupplier,
                              @NonNull final OnModifiedCallback onModifiedCallback) {
        super(requestKey, dialogSupplier);
        this.onModifiedCallback = onModifiedCallback;
    }

    public static void setResult(@NonNull final Fragment fragment,
                                 @NonNull final String requestKey,
                                 @NonNull final String original,
                                 @NonNull final String modified) {
        final Bundle result = new Bundle(2);
        result.putString(ORIGINAL, original);
        result.putString(MODIFIED, modified);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param text to edit.
     */
    public void launch(@NonNull final String text) {
        Objects.requireNonNull(fragmentManager, "fragmentManager");

        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_ITEM, text);

        final DialogFragment dialogFragment = dialogFragmentSupplier.get();
        dialogFragment.setArguments(args);
        dialogFragment.show(fragmentManager, TAG);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        onModifiedCallback.onModified(requestKey,
                                      Objects.requireNonNull(result.getString(ORIGINAL), ORIGINAL),
                                      Objects.requireNonNull(result.getString(MODIFIED), MODIFIED));
    }

    @FunctionalInterface
    public interface OnModifiedCallback {
        /**
         * Callback handler - modifying an existing item.
         *
         * @param requestKey the key as passed in
         * @param original   the original item
         * @param modified   the modified item
         */
        void onModified(@NonNull String requestKey,
                        @NonNull String original,
                        @NonNull String modified);
    }
}
