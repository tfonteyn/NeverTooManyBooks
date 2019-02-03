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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;

import java.util.ArrayList;

/**
 * This class is called by {@link EditBookFragment} and displays the Loaned Tab.
 * <p>
 * Users can select a book and, from this activity, select a friend to "loan" the book to.
 * This will then be saved in the database on the fly!
 * <p>
 * So this fragment does NOT participate in
 * {@link #initFields()}
 * {@link #onLoadFieldsFromBook} and {@link #onSaveFieldsToBook}
 */
public class EditBookLoanedFragment
        extends EditBookBaseFragment {

    public static final String TAG = "EditBookLoanedFragment";

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    private TextView mLoanedToView;

    /* ------------------------------------------------------------------------------------------ */
    @Override
    @NonNull
    protected BookManager getBookManager() {
        //noinspection ConstantConditions
        return ((EditBookFragment) this.getParentFragment()).getBookManager();
    }

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @NonNull
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_loan_base,
                                container, false);
    }

    /**
     * Has no specific Arguments or savedInstanceState.
     * All storage interaction is done via:
     * {@link BookManager#getBook()} on the hosting Activity
     * {@link #onLoadFieldsFromBook(Book, boolean)} from base class onResume
     * {@link #onSaveFieldsToBook(Book)} from base class onPause
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        String friend = mDb.getLoaneeByBookId(getBookManager().getBook().getId());
        if (friend == null) {
            showLoanTo();
        } else {
            showLoaned(friend);
        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Populate">

    /**
     * Display the loan to page. It is slightly different to the existing loan page.
     */
    private void showLoanTo() {
        ViewGroup sv = loadFragmentIntoView(R.layout.fragment_edit_book_loan_to);
        mLoanedToView = sv.findViewById(R.id.loaned_to);
        setPhoneContactsAdapter();

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                String loanee = mLoanedToView.getText().toString().trim();
                getBookManager().getBook().loan(mDb, loanee);
                showLoaned(loanee);
            }
        });
    }

    /**
     * Display the existing loan page. It is slightly different to the loan to page.
     *
     * @param user The user the book was loaned to
     */
    private void showLoaned(@NonNull final String user) {
        ViewGroup sv = loadFragmentIntoView(R.layout.fragment_edit_book_loaned);
        mLoanedToView = sv.findViewById(R.id.loaned_to);
        mLoanedToView.setText(user);

        sv.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(@NonNull final View v) {
                getBookManager().getBook().loanReturned(mDb);
                showLoanTo();
            }
        });
    }

    @NonNull
    private ViewGroup loadFragmentIntoView(@LayoutRes final int fragmentLayoutId) {
        //noinspection ConstantConditions
        ViewGroup sv = getView().findViewById(R.id.root);
        sv.removeAllViews();
        requireActivity().getLayoutInflater().inflate(fragmentLayoutId, sv);
        return sv;
    }
    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Auto complete list comes from your Contacts.
     */
    private void setPhoneContactsAdapter() {
        // check security
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    UniqueId.REQ_ANDROID_PERMISSIONS);
            return;
        }
        // call secured method
        ArrayList<String> contacts = getPhoneContacts();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_dropdown_item_1line,
                contacts);
        ((AutoCompleteTextView) mLoanedToView).setAdapter(adapter);
    }

    /**
     * Return a list of friends from your contact list.
     *
     * @return an ArrayList of names
     *
     * @throws SecurityException if we did not have the required permissions
     */
    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    @NonNull
    private ArrayList<String> getPhoneContacts()
            throws SecurityException {
        ArrayList<String> list = new ArrayList<>();
        ContentResolver cr = requireActivity().getContentResolver();
        try (Cursor contactsCursor = cr.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION,
                                              null, null, null)) {
            if (contactsCursor != null) {
                while (contactsCursor.moveToNext()) {
                    String name = contactsCursor.getString(contactsCursor.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
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
     * @param grantResults The grant results for the corresponding permissions which is either
     *                     {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *                     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}.
     *                     Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    @PermissionChecker.PermissionResult
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        //ENHANCE: when/if we request more permissions, then the permissions[] and grantResults[]
        // must be checked in parallel
        switch (requestCode) {
            case UniqueId.REQ_ANDROID_PERMISSIONS:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setPhoneContactsAdapter();
                }
                break;

            default:
                Logger.debug("unknown requestCode=" + requestCode);
                break;
        }
    }
}
