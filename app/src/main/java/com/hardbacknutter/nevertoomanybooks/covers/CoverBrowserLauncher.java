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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class CoverBrowserLauncher
        extends DialogLauncher {

    private static final String COVER_FILE_SPEC = "fileSpec";
    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    CoverBrowserLauncher(@NonNull final String requestKey,
                         @NonNull final ResultListener resultListener) {
        super(requestKey, CoverBrowserDialogFragment::new);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param fileSpec   for the selected file
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final String fileSpec) {
        final Bundle result = new Bundle(1);
        result.putString(COVER_FILE_SPEC, fileSpec);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param bookTitle to display
     * @param isbn      ISBN of book
     * @param cIdx      0..n image index
     */
    public void launch(@NonNull final String bookTitle,
                       @NonNull final String isbn,
                       @IntRange(from = 0, to = 1) final int cIdx) {

        final Bundle args = new Bundle(4);
        args.putString(DBKey.TITLE, bookTitle);
        args.putString(DBKey.BOOK_ISBN, isbn);
        args.putInt(CoverBrowserViewModel.BKEY_FILE_INDEX, cIdx);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(
                Objects.requireNonNull(result.getString(COVER_FILE_SPEC), COVER_FILE_SPEC));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param fileSpec for the selected file
         */
        void onResult(@NonNull String fileSpec);
    }
}
