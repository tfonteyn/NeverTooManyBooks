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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class EditLenderViewModel
        extends ViewModel {

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The book we're lending. */
    private long bookId;
    /** Displayed for info. */
    private String bookTitle;

    /**
     * The person who currently has the book.
     * Will be {@code null} if the book is available.
     * <p>
     * {@link DBKey#LOANEE_NAME} in savedInstanceState.
     */
    @Nullable
    private String loanee;

    /** The loanee being edited. */
    @Nullable
    private String currentEdit;

    private final List<String> people = new ArrayList<>();

    public void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                                DialogLauncher.BKEY_REQUEST_KEY);

            final LoaneeDao loaneeDao = ServiceLocator.getInstance().getLoaneeDao();
            // get previously used lender names
            people.addAll(loaneeDao.getList());

            bookId = args.getLong(DBKey.FK_BOOK);
            bookTitle = Objects.requireNonNull(args.getString(DBKey.TITLE), DBKey.TITLE);

            loanee = loaneeDao.findLoaneeByBookId(bookId);
            currentEdit = loanee;
        }
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    public long getBookId() {
        return bookId;
    }

    @NonNull
    String getBookTitle() {
        return bookTitle;
    }

    @Nullable
    public String getLoanee() {
        return loanee;
    }

    @Nullable
    public String getCurrentEdit() {
        return currentEdit;
    }

    public void setCurrentEdit(@Nullable final String currentEdit) {
        this.currentEdit = currentEdit;
    }

    @NonNull
    public List<String> getPeople() {
        return people;
    }

    @NonNull
    List<String> getContacts(@NonNull final Context context) {
        // LinkedHashSet to remove duplicates
        final Set<String> contacts = new LinkedHashSet<>(people);
        final ContentResolver cr = context.getContentResolver();
        try (Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI,
                                      new String[]{ContactsContract.Contacts.LOOKUP_KEY,
                                              ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                                      null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    final String name = cursor.getString(cursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                    contacts.add(name);
                }
            }
        }

        final List<String> sorted = new ArrayList<>(contacts);
        Collections.sort(sorted);
        return sorted;
    }

}
