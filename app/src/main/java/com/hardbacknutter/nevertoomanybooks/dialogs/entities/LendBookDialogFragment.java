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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Dialog to create a new loan, or edit an existing one.
 */
public class LendBookDialogFragment
        extends DialogFragment {

    public static final String TAG = "LendBookDialogFrag";

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    /** Database Access. */
    private DAO mDb;

    private long mBookId;
    private AutoCompleteTextView mLoaneeView;
    private String mAuthorName;
    private String mTitle;
    private String mLoanee;
    private WeakReference<BookChangedListener> mBookChangedListener;

    /**
     * Constructor.
     *
     * @param bookId   to lend
     * @param authorId informational display only
     * @param title    informational display only
     *
     * @return the instance
     */
    public static LendBookDialogFragment newInstance(final long bookId,
                                                     final long authorId,
                                                     @NonNull final String title) {
        LendBookDialogFragment frag = new LendBookDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DBDefinitions.KEY_PK_ID, bookId);
        args.putLong(DBDefinitions.KEY_FK_AUTHOR, authorId);
        args.putString(DBDefinitions.KEY_TITLE, title);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param book    to lend
     *
     * @return the instance
     */
    public static LendBookDialogFragment newInstance(@NonNull final Context context,
                                                     @NonNull final Book book) {
        LendBookDialogFragment frag = new LendBookDialogFragment();
        Bundle args = new Bundle();
        args.putLong(DBDefinitions.KEY_PK_ID, book.getId());
        args.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, book.getPrimaryAuthor(context));
        args.putString(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE));
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        Bundle args = requireArguments();
        mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
        mTitle = args.getString(DBDefinitions.KEY_TITLE);

        if (savedInstanceState == null) {
            // see if the string is there
            mAuthorName = args.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
            // if not, we must have the ID.
            if (mAuthorName == null) {
                //noinspection ConstantConditions
                mAuthorName = mDb.getAuthor(args.getLong(DBDefinitions.KEY_FK_AUTHOR))
                                 .getLabel(getContext());
            }
            mLoanee = mDb.getLoaneeByBookId(mBookId);
        } else {
            mAuthorName = savedInstanceState.getString(DBDefinitions.KEY_FK_AUTHOR);
            mLoanee = savedInstanceState.getString(DBDefinitions.KEY_LOANEE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_edit_loan, null);

        TextView titleView = root.findViewById(R.id.title);
        titleView.setText(mTitle);

        TextView authorView = root.findViewById(R.id.author);
        authorView.setText(getString(R.string.lbl_by_author_s, mAuthorName));

        mLoaneeView = root.findViewById(R.id.loaned_to);
        if (mLoanee != null) {
            mLoaneeView.setText(mLoanee);
        }

        setPhoneContactsAdapter();

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.lbl_lend_to)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setNeutralButton(R.string.btn_loan_returned, (dialog, which) -> {
                    // the book was returned (inspect it for sub-nano damage),
                    // remove the loan data
                    dismiss();
                    mDb.deleteLoan(mBookId);
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener
                                .get()
                                .onBookChanged(mBookId, BookChangedListener.BOOK_LOANEE, null);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onBookChanged" + Logger.WEAK_REFERENCE_DEAD);
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = mLoaneeView.getText().toString().trim();
                    if (newName.isEmpty()) {
                        UserMessage.show(mLoaneeView, R.string.warning_missing_name);
                        return;
                    }
                    dismiss();

                    // check if there was something changed at all.
                    if (newName.equals(mLoanee)) {
                        return;
                    }
                    mLoanee = newName;

                    // lend book, reluctantly...
                    mDb.updateOrInsertLoan(mBookId, mLoanee);

                    Bundle data = new Bundle();
                    data.putString(DBDefinitions.KEY_LOANEE, mLoanee);
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener
                                .get()
                                .onBookChanged(mBookId, BookChangedListener.BOOK_LOANEE, data);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onBookChanged" + Logger.WEAK_REFERENCE_DEAD);
                        }
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_FK_AUTHOR, mAuthorName);
        outState.putString(DBDefinitions.KEY_LOANEE, mLoanee);
    }

    @Override
    public void onDestroyView() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroyView();
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    /**
     * Auto complete list comes from your Contacts.
     */
    private void setPhoneContactsAdapter() {
        //noinspection ConstantConditions
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            //noinspection ConstantConditions
            ActivityCompat.requestPermissions(getActivity(),
                                              new String[]{Manifest.permission.READ_CONTACTS},
                                              UniqueId.REQ_ANDROID_PERMISSIONS);
            return;
        }

        // call secured method
        ArrayList<String> contacts = getPhoneContacts();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line,
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
        @SuppressWarnings("ConstantConditions")
        ContentResolver cr = getContext().getContentResolver();
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
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_ANDROID_PERMISSIONS:
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setPhoneContactsAdapter();
                }
                break;

            default:
                //noinspection ConstantConditions
                Logger.warnWithStackTrace(getContext(), TAG, "requestCode=" + requestCode);
                break;
        }
    }

    @Override
    public void onPause() {
        mLoanee = mLoaneeView.getText().toString().trim();
        super.onPause();
    }
}
