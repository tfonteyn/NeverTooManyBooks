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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;

@SuppressWarnings("WeakerAccess")
public class EditLenderViewModel
        extends ViewModel {

    private final List<String> people = new ArrayList<>();

    private LoaneeDao dao;

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

    /**
     * Pseudo constructor.
     *
     * @param args {@link Fragment#requireArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (dao == null) {
            dao = ServiceLocator.getInstance().getLoaneeDao();

            // get previously used lender names
            final List<String> list = dao.getList();
            // We have seen an NPE in github #70 which almost certainly can be attributed
            // to the Contacts application returning null data. See #getContact
            // but to make sure we never crash again here, adding a sanity/paranoia
            // check for null here as well.
            // We're NOT adding the null check to the DAO though leaving future investigation open
            people.addAll(list.stream().filter(Objects::nonNull).collect(Collectors.toList()));

            bookId = args.getLong(DBKey.FK_BOOK);
            bookTitle = Objects.requireNonNull(args.getString(DBKey.TITLE), DBKey.TITLE);

            loanee = dao.findLoaneeByBookId(bookId);
            currentEdit = loanee;
        }
    }

    public long getBookId() {
        return bookId;
    }

    @NonNull
    String getBookTitle() {
        return bookTitle;
    }

    @Nullable
    public String getCurrentEdit() {
        return currentEdit;
    }

    public void setCurrentEdit(@Nullable final String currentEdit) {
        this.currentEdit = currentEdit;
    }

    /**
     * Get the list of people whom we <strong>currently</strong> have books on loan to.
     * <p>
     * Used initially and/or if the user does not give us permission to access their contacts.
     *
     * @return list
     */
    @NonNull
    public List<String> getPeople() {
        return people;
    }

    /**
     * Combine the list of people whom we <strong>currently</strong> have books on loan to,
     * with the contacts from the user/device's contact list.
     * <p>
     * Used when the user have given us permission to access their contacts.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    List<String> getContacts(@NonNull final Context context) {
        // Dev note: We tried to:
        //    people.clear();
        //    people.addAll(sorted);
        //
        // and from the caller:
        //    vm.loadContacts(getContext());
        //    adapter.notifyDataSetChanged();
        // but we ended up with the adapter not showing anything.
        // It's like if the used ExtArrayAdapter looses the pointer to the people list?

        // We'll combine the people/names we already have with the contacts.
        // Use a LinkedHashSet to remove duplicates.
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
                    // sanity check added due to github #70
                    if (name != null && !name.isBlank()) {
                        contacts.add(name);
                    }
                }
            }
        }

        final List<String> sorted = new ArrayList<>(contacts);
        Collections.sort(sorted);
        return sorted;
    }

    boolean isModified() {
        if (currentEdit != null) {
            return !currentEdit.equalsIgnoreCase(loanee);
        } else {
            return loanee != null;
        }
    }

    boolean saveChanges() {
        return dao.setLoanee(bookId, currentEdit);
    }
}
