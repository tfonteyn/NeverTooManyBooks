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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.lender;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.fragment.app.FragmentActivity;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PermissionRequester;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class EditLenderLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    @NonNull
    private final PermissionRequester permissionRequester;

    /**
     * Constructor.
     *
     * @param activity       the hosting Activity
     * @param contractOwner  the component which handles the {@link ActivityResultContract}
     * @param resultListener to use
     */
    public EditLenderLauncher(@NonNull final FragmentActivity activity,
                              @NonNull final ActivityResultCaller contractOwner,
                              @NonNull final ResultListener resultListener) {
        super(DBKey.LOANEE_NAME,
              EditLenderDialogFragment::new,
              EditLenderBottomSheet::new);
        this.resultListener = resultListener;

        permissionRequester = new PermissionRequester(activity, contractOwner);
        final String msg = activity.getString(R.string.info_read_contacts_permission);
        permissionRequester.addPermission(Manifest.permission.READ_CONTACTS, false, msg, msg);
    }

    /**
     * Launch the dialog.
     *
     * @param context preferably the {@code Activity}
     *                but another UI {@code Context} will also do.
     * @param book    to lend
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final Book book) {
        launch(context, book.getId(), book.getTitle());
    }

    /**
     * Launch the dialog.
     *
     * @param context   preferably the {@code Activity}
     *                  but another UI {@code Context} will also do.
     * @param bookId    to lend
     * @param bookTitle displayed for info only
     */
    public void launch(@NonNull @UiContext final Context context,
                       @IntRange(from = 1) final long bookId,
                       @NonNull final String bookTitle) {
        permissionRequester.request(Manifest.permission.READ_CONTACTS, dontCare -> {
            // The permission was optional - so regardless of the result, continue.
            // The delegate will simply check if the permission was granted when it
            // wants to get the contacts... or ignore it.

            final EditLenderInput input = new EditLenderInput(getRequestKey(), bookId, bookTitle);
            showDialog(context, input.toBundle());
        });
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final Output output = Output.fromBundle(result);
        if (output.getBookId() <= 0) {
            // Sanity check, we should not get a new book here (id==0)
            throw new IllegalArgumentException(DBKey.FK_BOOK);
        }
        resultListener.onResult(output.getBookId(), output.getLoanee());
    }

    public static class Output
            implements LauncherOutput {
        @IntRange(from = 1)
        private final long bookId;
        @Nullable
        private final String loanee;

        /**
         * Constructor.
         *
         * @param bookId the id of the lent book
         * @param loanee the name of the loanee,
         *               or {@code null} / {@code ""} for a returned book
         */
        public Output(final long bookId,
                      @Nullable final String loanee) {
            this.bookId = bookId;
            this.loanee = loanee;
        }

        @NonNull
        static Output fromBundle(final Bundle result) {
            final long bookId = result.getLong(DBKey.FK_BOOK);
            final String loanee = result.getString(DBKey.LOANEE_NAME);

            return new Output(bookId, loanee);
        }

        @NonNull
        @Override
        public Bundle toBundle() {
            final Bundle args = new Bundle(2);
            args.putLong(DBKey.FK_BOOK, bookId);
            if (loanee != null) {
                args.putString(DBKey.LOANEE_NAME, loanee);
            }

            return args;
        }

        long getBookId() {
            return bookId;
        }

        @Nullable
        String getLoanee() {
            return loanee;
        }
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler.
         *
         * @param bookId the id of the lent book
         * @param loanee the name of the loanee,
         *               or {@code null} / {@code ""} for a returned book
         */
        void onResult(@IntRange(from = 1) long bookId,
                      @Nullable String loanee);
    }
}
