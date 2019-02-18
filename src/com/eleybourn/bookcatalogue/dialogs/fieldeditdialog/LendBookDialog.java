/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;

/**
 * Dialog to create a new loan, or edit an existing one.
 */
public class LendBookDialog {

    @NonNull
    private final Activity mActivity;
    @NonNull
    private final DBA mDb;
    @Nullable
    private final OnChanged mOnChanged;

    private AutoCompleteTextView mNameView;

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    /**
     * @param activity  hosting activity (needed for user messages)
     * @param db        the database
     * @param onChanged (optional) class/method to run if something was changed
     */
    public LendBookDialog(@NonNull final Activity activity,
                          @NonNull final DBA db,
                          @Nullable final OnChanged onChanged) {
        mDb = db;
        mActivity = activity;
        mOnChanged = onChanged;
    }

    public void edit(@NonNull final Book book) {
        // Build the base dialog
        final View root = mActivity.getLayoutInflater()
                                   .inflate(R.layout.dialog_edit_loan, null);

        TextView titleView = root.findViewById(R.id.title);
        titleView.setText(book.getString(UniqueId.KEY_TITLE));
        TextView authorView = root.findViewById(R.id.author);
        authorView.setText(book.getPrimaryAuthor());

        mNameView = root.findViewById(R.id.loaned_to);
        final String loanee = mDb.getLoaneeByBookId(book.getId());
        if (loanee != null) {
            mNameView.setText(loanee);
        }
        setPhoneContactsAdapter();

        final AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.lbl_loan_to)
                .create();

        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = mNameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showUserMessage(mActivity, R.string.warning_required_name);
                    return;
                }

                dialog.dismiss();

                // check if there was something changed at all.
                if (newName.equals(loanee)) {
                    return;
                }

                // lend book
                mDb.updateOrInsertLoan(book.getId(), newName);
                if (mOnChanged != null) {
                    mOnChanged.onChanged(book.getId(), loanee);
                }
            }
        });

        root.findViewById(R.id.return_book).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
                mDb.deleteLoan(book.getId());
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * Auto complete list comes from your Contacts.
     */
    private void setPhoneContactsAdapter() {
        // check security
        if (ContextCompat.checkSelfPermission(
                mActivity,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    mActivity,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    UniqueId.REQ_ANDROID_PERMISSIONS);
            return;
        }
        // call secured method
        ArrayList<String> contacts = getPhoneContacts();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                mActivity,
                android.R.layout.simple_dropdown_item_1line,
                contacts);
        mNameView.setAdapter(adapter);
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
        ContentResolver cr = mActivity.getContentResolver();
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

    public interface OnChanged {

        /**
         * Called after the user confirms a change.
         *
         * @param bookId the id of the book.
         * @param loanee name of loanee, or null if the book was returned/cancelled
         */
        void onChanged(long bookId,
                       @Nullable String loanee);
    }
}
