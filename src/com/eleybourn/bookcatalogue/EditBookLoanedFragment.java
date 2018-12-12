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
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;

import java.util.ArrayList;

/**
 * This class is called by {@link EditBookFragment} and displays the Loaned Tab
 *
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * * This will then be saved in the database.
 *
 *  So this fragment does NOT participate in
 *      {@link #initFields()}
 *      {@link #onLoadFieldsFromBook} and {@link #onSaveFieldsToBook}
 */
public class EditBookLoanedFragment extends BookBaseFragment {

    public static final String TAG = "EditBookLoanedFragment";

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
    };

    private TextView mLoanedTo;

    /* ------------------------------------------------------------------------------------------ */
    @NonNull
    protected BookManager getBookManager() {
        //noinspection ConstantConditions
        return ((EditBookFragment)this.getParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

//    /**
//     * Ensure activity supports interface
//     */
//    @Override
//    @CallSuper
//    public void onAttach(final @NonNull Context context) {
//        super.onAttach(context);
//    }

//    @Override
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//    }

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_loan_base, container, false);
    }

    /**
     * has no specific Arguments or savedInstanceState as all is done via
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        String friend = mDb.getLoanByBookId(getBookManager().getBook().getBookId());
        if (friend == null) {
            showLoanTo();
        } else {
            showLoaned(friend);
        }
        Tracker.exitOnActivityCreated(this);
    }

//    @Override
//    protected void initFields() {
//        super.initFields();
//    }

//    @CallSuper
//    @Override
//    public void onResume() {
//        super.onResume();
//    }

//    @Override
//    protected void onLoadFieldsFromBook(@NonNull final Book book, final boolean setAllFrom) {
//        super.onLoadFieldsFromBook(book, setAllFrom);
//    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">
    /**
     * Display the loan to page. It is slightly different to the existing loan page
     */
    private void showLoanTo() {
        ViewGroup sv = loadFragmentIntoView(R.layout.fragment_edit_book_loan_to);
        mLoanedTo = sv.findViewById(R.id.loaned_to);
        setPhoneContactsAdapter();

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String friend = mLoanedTo.getText().toString().trim();
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
    private void showLoaned(final @NonNull String user) {
        ViewGroup sv = loadFragmentIntoView(R.layout.fragment_edit_book_loaned);
        mLoanedTo = sv.findViewById(R.id.loaned_to);
        mLoanedTo.setText(user);

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                removeLoan();
                showLoanTo();
            }
        });
    }

    @NonNull
    private ViewGroup loadFragmentIntoView(final @LayoutRes int fragmentId) {
        //noinspection ConstantConditions
        ViewGroup sv = getView().findViewById(R.id.root);
        sv.removeAllViews();
        requireActivity().getLayoutInflater().inflate(fragmentId, sv);
        return sv;
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

//    @Override
//    @CallSuper
//    public void onPause() {
//        super.onPause();
//    }

//    @Override
//    @CallSuper
//    protected void onSaveFieldsToBook(final @NonNull Book book) {
//        super.onSaveFieldsToBook(book);
//    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    private void saveLoan(final @NonNull String friend) {
        Book book = getBookManager().getBook();
        book.putString(UniqueId.KEY_LOAN_LOANED_TO, friend);
        mDb.insertLoan(book, true);
    }

    private void removeLoan() {
        Book book = getBookManager().getBook();
        mDb.deleteLoan(book.getBookId(), true);
    }

    /**
     * Auto complete list comes from your Contacts
     */
    private void setPhoneContactsAdapter() {
        // check security
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.READ_CONTACTS}, UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS_REQUEST);
            return;
        }
        // call secured method
        ArrayList<String> contacts = getPhoneContacts();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_dropdown_item_1line, contacts);
        ((AutoCompleteTextView) mLoanedTo).setAdapter(adapter);
    }

    /**
     * Return a list of friends from your contact list.
     *
     * @return an ArrayList of names
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    @NonNull
    private ArrayList<String> getPhoneContacts() throws SecurityException {
        ArrayList<String> list = new ArrayList<>();
        ContentResolver cr = requireActivity().getContentResolver();
        try (Cursor contactsCursor = cr.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION, null, null, null)) {
            if (contactsCursor != null) {
                while (contactsCursor.moveToNext()) {
                    String name = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                    list.add(name);
                }
            }
        }
        return list;
    }


    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *                     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    @PermissionChecker.PermissionResult
    public void onRequestPermissionsResult(final int requestCode,
                                           final @NonNull String permissions[],
                                           final @NonNull int[] grantResults) {
        //ENHANCE: when/if we request more permissions, then the permissions[] and grantResults[] must be checked in parallel
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setPhoneContactsAdapter();
                }
            }
        }
    }
}
