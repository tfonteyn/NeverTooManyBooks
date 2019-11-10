/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

public abstract class BookSearchByIsbnBaseFragment
        extends BookSearchBaseFragment {

    /** process search results. */
    private final SearchCoordinator.SearchFinishedListener mSearchFinishedListener =
            (wasCancelled, bookData) -> {
                try {
                    if (!wasCancelled) {
                        // A non-empty result will have a title or at least 3 fields.
                        // The isbn field will always be present as we searched on one.
                        // The title field, *might* be there but *might* be empty.
                        // So a valid result means we either need a title, or a
                        // third field.
                        String title = bookData.getString(DBDefinitions.KEY_TITLE);
                        if ((title != null && !title.isEmpty())
                            || bookData.size() > 2) {
                            Intent intent = new Intent(getContext(), EditBookActivity.class)
                                    .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                            startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);

                            clearPreviousSearchCriteria();
                        } else {
                            //noinspection ConstantConditions
                            UserMessage.show(getView(), R.string.warning_no_matching_book_found);
                        }
                    } else {
                        startInput();
                    }
                } finally {
                    mBookSearchBaseModel.setSearchCoordinator(0);
                    // Tell our listener they can close the progress dialog.
                    mTaskManager.sendHeaderUpdate(null);
                }
            };
    /** Flag to allow ASIN key input (true) or pure ISBN input (false). */
    boolean mAllowAsin;

    @Override
    SearchCoordinator.SearchFinishedListener getSearchFinishedListener() {
        return mSearchFinishedListener;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mandatory
        setHasOptionsMenu(true);
    }

    /**
     * Search with ISBN.
     * <p>
     * mIsbnSearchText must be 10 characters (or more) to even consider a search.
     */
    void prepareSearch(@NonNull String isbn) {
        // sanity check
        if (isbn.length() < 10) {
            return;
        }

        // intercept UPC numbers
        isbn = ISBN.upc2isbn(isbn);
        if (isbn.length() < 10) {
            return;
        }

        // not a valid ISBN/ASIN ?
        if (!ISBN.isValid(isbn) && (!mAllowAsin || !ISBN.isValidAsin(isbn))) {
            isbnInvalid(isbn);
            return;
        }
        // at this point, we have a valid isbn/asin.
        onValid();

        // See if ISBN already exists in our database, if not then start the search.
        final long existingId = mBookSearchBaseModel.getDb().getBookIdFromIsbn(isbn, true);
        if (existingId != 0) {
            isbnAlreadyPresent(existingId);
        } else {
            startSearch();
        }
    }

    /**
     * ISBN was already present, Verify what the user wants to do.
     *
     * @param existingId of the book we already have in the database
     */
    private void isbnAlreadyPresent(final long existingId) {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_duplicate_book)
                .setMessage(R.string.confirm_duplicate_book_message)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                // User aborts this isbn
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    clearPreviousSearchCriteria();
                    startInput();
                })
                // User wants to review the existing book
                .setNeutralButton(R.string.edit, (dialog, which) -> {
                    Intent intent = new Intent(getContext(), EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, existingId);
                    startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                })
                // User wants to add regardless
                .setPositiveButton(R.string.btn_confirm_add, (dialog, which) -> startSearch())
                .create()
                .show();
    }

    /**
     * ISBN was invalid. Inform the user and go back to either the UI or the scanner.
     *
     * @param isbn the isbn which was invalid
     */
    private void isbnInvalid(@NonNull final String isbn) {
        onInvalid();

        String msg;
        if (mAllowAsin) {
            msg = getString(R.string.warning_x_is_not_a_valid_isbn_or_asin, isbn);
        } else {
            msg = getString(R.string.warning_x_is_not_a_valid_isbn, isbn);
        }
        //noinspection ConstantConditions
        UserMessage.show(getView(), msg);

        startInput();
    }

    /**
     * Optional hook for child class to start their input process.
     * e.g. start the camera... etc...
     */
    void startInput() {

    }

    /**
     * Optional hook for child class to be notified when we have determined
     * we got a valid ISBN.
     */
    void onValid() {

    }

    /**
     * Optional hook for child class to be notified when we have determined
     * we got an invalid ISBN.
     */
    void onInvalid() {

    }
}
