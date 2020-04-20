/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to create a new loan, or edit an existing one.
 */
public class LendBookDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "LendBookDialogFrag";

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    /** Database Access. */
    private DAO mDb;

    private long mBookId;
    private String mAuthorName;
    private String mTitle;
    private String mLoanee;
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    /** View Binding. */
    private DialogEditLoanBinding mVb;

    /**
     * Constructor.
     *
     * @param bookId   to lend
     * @param authorId informational display only
     * @param title    informational display only
     *
     * @return instance
     */
    public static DialogFragment newInstance(final long bookId,
                                             final long authorId,
                                             @NonNull final String title) {
        final DialogFragment frag = new LendBookDialogFragment();
        final Bundle args = new Bundle(3);
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
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Context context,
                                             @NonNull final Book book) {
        final DialogFragment frag = new LendBookDialogFragment();
        final Bundle args = new Bundle(3);
        args.putLong(DBDefinitions.KEY_PK_ID, book.getId());
        args.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, book.getPrimaryAuthor(context));
        args.putString(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE));
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

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
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogEditLoanBinding.inflate(inflater);

        mVb.author.setText(getString(R.string.lbl_by_author_s, mAuthorName));

        if (mLoanee != null) {
            mVb.loanedTo.setText(mLoanee);
        }

        ArrayList<String> contacts = getPhoneContacts();
        if (contacts != null) {
            initAdapter(contacts);
        }

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(mTitle)
                .setNegativeButton(android.R.string.cancel, (d, w) -> dismiss())
                .setNeutralButton(R.string.btn_loan_returned, (d, w) -> {
                    // the book was returned (inspect it for sub-nano damage),
                    // remove the loan data
                    dismiss();
                    mDb.deleteLoan(mBookId);
                    if (mListener != null && mListener.get() != null) {
                        mListener.get()
                                 .onBookChanged(mBookId, BookChangedListener.BOOK_LOANEE, null);
                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.w(TAG, "onBookChanged|" +
                                       (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                          : ErrorMsg.LISTENER_WAS_DEAD));
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = mVb.loanedTo.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Snackbar.make(mVb.loanedTo, R.string.warning_missing_name,
                                      Snackbar.LENGTH_LONG).show();
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
                    if (mListener != null && mListener.get() != null) {
                        mListener.get()
                                 .onBookChanged(mBookId, BookChangedListener.BOOK_LOANEE, data);
                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.w(TAG, "onBookChanged|" +
                                       (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                          : ErrorMsg.LISTENER_WAS_DEAD));
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
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    private void initAdapter(@NonNull final Collection<String> contacts) {

        ArrayList<String> people = mDb.getLoanees();
        people.addAll(contacts);
        Collections.sort(people);

        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, people);
        mVb.loanedTo.setAdapter(adapter);
    }

    /**
     * Auto complete list comes from your Contacts.
     *
     * @return list of names, can be {@code null}
     */
    @Nullable
    private ArrayList<String> getPhoneContacts() {
        //noinspection ConstantConditions
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {

            ArrayList<String> list = new ArrayList<>();
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

        } else {
            //noinspection ConstantConditions
            ActivityCompat.requestPermissions(getActivity(),
                                              new String[]{Manifest.permission.READ_CONTACTS},
                                              RequestCode.ANDROID_PERMISSIONS);
            return null;
        }
    }

    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ArrayList<String> contacts = getPhoneContacts();
            if (contacts != null) {
                // the autocomplete view will be updated.
                initAdapter(contacts);
            }
        }
    }

    @Override
    public void onPause() {
        mLoanee = mVb.loanedTo.getText().toString().trim();
        super.onPause();
    }
}
