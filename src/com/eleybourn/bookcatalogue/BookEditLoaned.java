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
import android.support.annotation.NonNull;
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

import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

import java.util.ArrayList;

/**
 * This class is called by the BookEdit activity and displays the Loaned Tab
 * <p>
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database for reference.
 */
public class BookEditLoaned extends BookEditFragmentAbstract {

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    /**
     * Return a list of friends from your contact list.
     * This is for the autoComplete textView
     *
     * @return an ArrayList of names
     */
    private ArrayList<String> getFriends() {
        ArrayList<String> friend_list = new ArrayList<>();

        // bail out silently
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            // ask the user to access contact.
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS}, 0);
            // while they decide... bail out silently, then can click the btn again afterwards.
            // Also no need to setup a listener for the requestCode=0
            return friend_list;
        }

        ContentResolver cr = getActivity().getContentResolver();
        try (Cursor contactsCursor = cr.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION, null, null, null)) {
            while (contactsCursor.moveToNext()) {
                String name = contactsCursor.getString
                        (contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                friend_list.add(name);
            }
        }
        return friend_list;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (ContextCompat.checkSelfPermission(getContext(),
                    Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                //FIXME: this needs more work... we need to tell the adapter to reload the list.
                if (BuildConfig.DEBUG) {
                    System.out.println("FIXME: this needs more work... we need to tell the adapter to reload the list.");
                }
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_loan_base, container, false);
    }

    /**
     * Called when the activity is first created. This function will check whether a book has been loaned
     * and display the appropriate page as required.
     *
     * @param savedInstanceState The saved bundle (from pausing). Can be null.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            super.onActivityCreated(savedInstanceState);
            String user = mDb.fetchLoanByBook(mEditManager.getBookData().getRowId());
            if (user == null) {
                loanTo();
            } else {
                loaned(user);
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    /**
     * Display the loan to page. It is slightly different to the existing loan page
     */
    private void loanTo() {
        ScrollView sv = getView().findViewById(R.id.root);
        sv.removeAllViews();
        LayoutInflater inf = getActivity().getLayoutInflater();
        inf.inflate(R.layout.fragment_edit_book_loan_to, sv);

        AutoCompleteTextView mUserText = sv.findViewById(R.id.loan_to_who);
        try {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_dropdown_item_1line, getFriends());
            mUserText.setAdapter(adapter);
        } catch (Exception e) {
            Logger.logError(e);
        }
        Button mConfirmButton = sv.findViewById(R.id.confirm);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String friend = saveLoan();
                loaned(friend);
            }
        });
    }

    /**
     * Display the existing loan page. It is slightly different to the loan to page
     *
     * @param user The user the book was loaned to
     */
    private void loaned(String user) {
        ScrollView sv = getView().findViewById(R.id.root);
        sv.removeAllViews();
        LayoutInflater inf = getActivity().getLayoutInflater();
        inf.inflate(R.layout.fragment_edit_book_loaned, sv);

        TextView mWhoText = sv.findViewById(R.id.who);
        mWhoText.setText(user);
        Button mConfirmButton = sv.findViewById(R.id.confirm);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                removeLoan();
                loanTo();
            }
        });
    }

    /**
     * Save the user and book combination as a loan in the database
     *
     * @return the user
     */
    private String saveLoan() {
        AutoCompleteTextView mUserText = getView().findViewById(R.id.loan_to_who);
        String friend = mUserText.getText().toString();
        BookData values = mEditManager.getBookData();
        values.putString(ColumnNames.KEY_LOANED_TO, friend);
        mDb.createLoan(values, true);
        return friend;
    }

    /**
     * Delete the user and book combination as a loan from the database
     */
    private void removeLoan() {
        mDb.deleteLoan(mEditManager.getBookData().getRowId(), true);
        return;
    }

    @Override
    protected void onLoadBookDetails(BookData book, boolean setAllDone) {
        if (!setAllDone)
            mFields.setAll(book);
    }
}
