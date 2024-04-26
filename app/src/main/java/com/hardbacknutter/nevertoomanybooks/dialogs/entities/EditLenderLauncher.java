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

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class EditLenderLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param dialogSupplier a supplier for a new DialogFragment
     * @param resultListener listener
     */
    public EditLenderLauncher(@NonNull final String requestKey,
                              @NonNull final Supplier<DialogFragment> dialogSupplier,
                              @NonNull final ResultListener resultListener) {
        super(requestKey, dialogSupplier);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param bookId     the id of the updated book
     * @param loanee     the name of the loanee, or {@code ""} for a returned book
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @IntRange(from = 1) final long bookId,
                          @NonNull final String loanee) {
        final Bundle result = new Bundle(2);
        result.putLong(DBKey.FK_BOOK, bookId);
        result.putString(DBKey.LOANEE_NAME, loanee);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param book to lend
     */
    public void launch(@NonNull final Book book) {
        launch(book.getId(), book.getTitle());
    }

    /**
     * Launch the dialog.
     *
     * @param bookId    to lend
     * @param bookTitle displayed for info only
     */
    public void launch(@IntRange(from = 1) final long bookId,
                       @NonNull final String bookTitle) {

        final Bundle args = new Bundle(3);
        args.putLong(DBKey.FK_BOOK, bookId);
        args.putString(DBKey.TITLE, bookTitle);

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final long value = result.getLong(DBKey.FK_BOOK);
        if (value <= 0) {
            throw new IllegalArgumentException(DBKey.FK_BOOK);
        }
        resultListener.onResult(value, Objects.requireNonNull(
                result.getString(DBKey.LOANEE_NAME), DBKey.LOANEE_NAME));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param bookId the id of the updated book
         * @param loanee the name of the loanee, or {@code ""} for a returned book
         */
        void onResult(@IntRange(from = 1) long bookId,
                      @NonNull String loanee);
    }
}
