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
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Dialog to create a new loan, or edit an existing one.
 */
public class LendBookDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = LendBookDialogFragment.class.getSimpleName();

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    private DBA mDb;
    private AutoCompleteTextView mLoaneeView;
    private String mAuthorName;
    private String mLoanee;

    public static LendBookDialogFragment newInstance(final long bookId,
                                                     final long authorId,
                                                     @NonNull final String title) {
        LendBookDialogFragment frag = new LendBookDialogFragment();
        Bundle args = new Bundle();
        args.putLong(UniqueId.KEY_ID, bookId);
        args.putLong(UniqueId.KEY_AUTHOR, authorId);
        args.putString(UniqueId.KEY_TITLE, title);
        frag.setArguments(args);
        return frag;
    }

    public static LendBookDialogFragment newInstance(@NonNull final Book book) {
        LendBookDialogFragment frag = new LendBookDialogFragment();
        Bundle args = new Bundle();
        args.putLong(UniqueId.KEY_ID, book.getId());
        args.putString(UniqueId.KEY_AUTHOR_FORMATTED, book.getPrimaryAuthor());
        args.putString(UniqueId.KEY_TITLE, book.getString(UniqueId.KEY_TITLE));
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final FragmentActivity mActivity = requireActivity();
        Bundle args = requireArguments();
        mDb = new DBA(mActivity);
        final long bookId = args.getLong(UniqueId.KEY_ID);

        if (savedInstanceState == null) {
            // see if the string is there
            mAuthorName = args.getString(UniqueId.KEY_AUTHOR_FORMATTED);
            // if not, we must have the id.
            if (mAuthorName == null) {
                //noinspection ConstantConditions
                mAuthorName = mDb.getAuthor(args.getLong(UniqueId.KEY_AUTHOR)).getDisplayName();
            }
            mLoanee = mDb.getLoaneeByBookId(bookId);
        } else {
            mAuthorName = savedInstanceState.getString(UniqueId.KEY_AUTHOR);
            mLoanee = savedInstanceState.getString(UniqueId.KEY_BOOK_LOANEE);
        }

        @SuppressLint("InflateParams")
        View root = mActivity.getLayoutInflater().inflate(R.layout.dialog_edit_loan, null);

        TextView titleView = root.findViewById(R.id.title);
        titleView.setText(args.getString(UniqueId.KEY_TITLE));
        TextView authorView = root.findViewById(R.id.author);
        authorView.setText(getString(R.string.lbl_by_author_s, mAuthorName));

        mLoaneeView = root.findViewById(R.id.loaned_to);
        if (mLoanee != null) {
            mLoaneeView.setText(mLoanee);
        }

        setPhoneContactsAdapter();

        root.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String newName = mLoaneeView.getText().toString().trim();
                if (newName.isEmpty()) {
                    UserMessage.showUserMessage(mActivity, R.string.warning_required_name);
                    return;
                }

                dismiss();

                // check if there was something changed at all.
                if (newName.equals(mLoanee)) {
                    return;
                }
                mLoanee = newName;

                // lend book, reluctantly...
                mDb.updateOrInsertLoan(bookId, mLoanee);

                Bundle data = new Bundle();
                data.putString(UniqueId.KEY_BOOK_LOANEE, mLoanee);
                tellCaller(bookId, data);
            }
        });

        // the book was returned, remove the loan data
        root.findViewById(R.id.return_book).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dismiss();
                mDb.deleteLoan(bookId);

                tellCaller(bookId, new Bundle());
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                dismiss();
            }
        });

        return new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.lbl_lend_to)
                .create();
    }

    private void tellCaller(final long bookId,
                            @NonNull final Bundle data) {

        data.putLong(UniqueId.KEY_ID, bookId);

        // see if there was a fragment?
        if (getParentFragment() instanceof BookChangedListener) {
            ((BookChangedListener) getParentFragment())
                    .onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, data);
            // or directly to an activity?
        } else if (requireActivity() instanceof BookChangedListener) {
            ((BookChangedListener) requireActivity())
                    .onBookChanged(bookId, BookChangedListener.BOOK_LOANEE, data);
        }
    }

    /**
     * Auto complete list comes from your Contacts.
     */
    private void setPhoneContactsAdapter() {
        // check security
        if (ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    UniqueId.REQ_ANDROID_PERMISSIONS);
            return;
        }
        // call secured method
        ArrayList<String> contacts = getPhoneContacts();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireActivity(), android.R.layout.simple_dropdown_item_1line,
                                   contacts);
        mLoaneeView.setAdapter(adapter);
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
    @Override
    public void onPause() {
        mLoanee = mLoaneeView.getText().toString().trim();
        super.onPause();
    }
    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(UniqueId.KEY_AUTHOR, mAuthorName);
        outState.putString(UniqueId.KEY_BOOK_LOANEE, mLoanee);
    }

    @Override
    public void onDestroyView() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroyView();
    }
}
