/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;

import java.util.ArrayList;

/**
 * This class is called by the BookDetailsActivity activity and displays the Loaned Tab
 *
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference.
 */
public class EditBookLoanedFragment extends EditBookAbstractFragment {

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_loan_base, container, false);
    }

    /**
     * Called when the activity is first created. This function will check whether a book has been loaned
     * and display the appropriate page as required.
     *
     * @param savedInstanceState The saved bundle (from pausing). Can be null.
     */
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onActivityCreated(savedInstanceState);
            String friend = mDb.getLoanByBookId(mEditManager.getBook().getBookId());
            if (friend == null) {
                showLoanTo();
            } else {
                showLoaned(friend);
            }
        } catch (Exception e) {
            Logger.logError(e);
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    /**
     * Display the loan to page. It is slightly different to the existing loan page
     */
    private void showLoanTo() {
        ScrollView sv = loadFragmentIntoScrollView(R.layout.fragment_edit_book_loan_to);

        // Auto complete list comes from your Contacts
        final AutoCompleteTextView who = sv.findViewById(R.id.who);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, getFriends());
        who.setAdapter(adapter);

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String friend = who.getText().toString().trim();
                saveLoan(friend);
                showLoaned(friend);
            }
        });
    }

    /**
     * Display the existing loan page. It is slightly different to the loan to page
     *
     * @param user The user the book was loaned to
     */
    private void showLoaned(@NonNull final String user) {
        ScrollView sv = loadFragmentIntoScrollView(R.layout.fragment_edit_book_loaned);

        TextView who = sv.findViewById(R.id.who);
        who.setText(user);

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                removeLoan();
                showLoanTo();
            }
        });
    }

    @NonNull
    private ScrollView loadFragmentIntoScrollView(@LayoutRes final int resId) {
        ScrollView sv = getView().findViewById(R.id.root);
        sv.removeAllViews();
        getActivity().getLayoutInflater().inflate(resId, sv);
        return sv;
    }

    private void saveLoan(@NonNull final String friend) {
        Book values = mEditManager.getBook();
        values.putString(UniqueId.KEY_LOANED_TO, friend);
        mDb.insertLoan(values, true);
    }

    private void removeLoan() {
        mDb.deleteLoan(mEditManager.getBook().getBookId(), true);
    }

    @Override
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        if (!setAllDone) {
            mFields.setAll(book);
        }
    }

    /**
     * Return a list of friends from your contact list.
     * This is for the autoComplete textView
     *
     * @return an ArrayList of names
     */
    @NonNull
    private ArrayList<String> getFriends() {
        ArrayList<String> friend_list = new ArrayList<>();

        // got permission ? then send contacts list back
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            ContentResolver cr = getActivity().getContentResolver();
            try (Cursor contactsCursor = cr.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION, null, null, null)) {
                if (contactsCursor != null) {
                    while (contactsCursor.moveToNext()) {
                        String name = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                        friend_list.add(name);
                    }
                }
            }
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.READ_CONTACTS}, UniqueId.ACTIVITY_REQUEST_CODE_PERMISSIONS_REQUEST);
        }
        return friend_list;
    }


    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == UniqueId.ACTIVITY_REQUEST_CODE_PERMISSIONS_REQUEST) {

        }
    }

}
