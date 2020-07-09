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
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to create a new loan, edit an existing one or remove it (book is returned).
 * <p>
 * Note the special treatment of the Book's current/original loanee.
 * This is done to minimize trips to the database.
 */
public class EditLenderDialogFragment
        extends BaseDialogFragment
        implements BookChangedListener.Owner {

    /** Fragment/Log tag. */
    public static final String TAG = "LendBookDialogFrag";

    private static final String BKEY_NEW_LOANEE = TAG + ':' + DBDefinitions.KEY_LOANEE;

    private static final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            };

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    /** View Binding. */
    private DialogEditLoanBinding mVb;

    /** The book we're lending. */
    private long mBookId;
    /** Displayed for info. */
    private String mBookTitle;

    /**
     * The person who currently has the book.
     * Will be {@code null} if the book is available.
     * {@link DBDefinitions#KEY_LOANEE} in savedInstanceState.
     */
    @Nullable
    private String mOriginalLoanee;

    /**
     * The loanee being edited.
     * {@link #BKEY_NEW_LOANEE} in savedInstanceState.
     */
    @Nullable
    private String mLoanee;

    /**
     * No-arg constructor for OS use.
     */
    public EditLenderDialogFragment() {
        super(R.layout.dialog_edit_loan);
    }

    /**
     * Constructor.
     *
     * @param bookId    to lend
     * @param bookTitle displayed for info only
     *
     * @return instance
     */
    public static DialogFragment newInstance(final long bookId,
                                             @NonNull final String bookTitle) {
        final DialogFragment frag = new EditLenderDialogFragment();
        final Bundle args = new Bundle(2);
        args.putLong(DBDefinitions.KEY_PK_ID, bookId);
        args.putString(DBDefinitions.KEY_TITLE, bookTitle);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Constructor.
     *
     * @param book to lend
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Book book) {
        final DialogFragment frag = new EditLenderDialogFragment();
        final Bundle args = new Bundle(2);
        args.putLong(DBDefinitions.KEY_PK_ID, book.getId());
        args.putString(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE));
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
        mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

        if (savedInstanceState == null) {
            mOriginalLoanee = mDb.getLoaneeByBookId(mBookId);
            mLoanee = mOriginalLoanee;
        } else {
            mOriginalLoanee = savedInstanceState.getString(DBDefinitions.KEY_LOANEE);
            mLoanee = savedInstanceState.getString(BKEY_NEW_LOANEE);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditLoanBinding.bind(view);

        mVb.toolbar.setSubtitle(mBookTitle);
        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.MENU_SAVE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mVb.lendTo.setText(mLoanee);

        final ArrayList<String> contacts = getContacts();
        if (contacts != null) {
            initAdapter(contacts);
        }
    }

    /**
     * Get the device Contacts list.
     *
     * @return list of names, or {@code null} if we needed to ask for permissions
     */
    @Nullable
    private ArrayList<String> getContacts() {
        //noinspection ConstantConditions
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {

            final ArrayList<String> list = new ArrayList<>();
            final ContentResolver cr = getContext().getContentResolver();
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
            final ArrayList<String> contacts = getContacts();
            if (contacts != null) {
                initAdapter(contacts);
            }
        }
    }

    private void initAdapter(@NonNull final Collection<String> contacts) {
        // combine contacts with previously used lender names
        ArrayList<String> people = mDb.getLoanees();
        people.addAll(contacts);
        // remove duplicates
        people = new ArrayList<>(new LinkedHashSet<>(people));
        Collections.sort(people);

        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, people);
        mVb.lendTo.setAdapter(adapter);
    }

    private boolean saveChanges() {
        viewToModel();

        // anything actually changed ?
        //noinspection ConstantConditions
        if (mLoanee.equalsIgnoreCase(mOriginalLoanee)) {
            return true;
        }

        final boolean success;
        final Bundle data;
        if (!mLoanee.isEmpty()) {
            // lend book, reluctantly...
            success = mDb.lendBook(mBookId, mLoanee);
            data = new Bundle();
            data.putString(DBDefinitions.KEY_LOANEE, mLoanee);

        } else {
            // return the book
            success = mDb.lendBook(mBookId, null);
            data = null;
        }

        if (success) {
            if (mListener != null && mListener.get() != null) {
                mListener.get().onChange(mBookId, BookChangedListener.BOOK_LOANEE, data);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "onBookChanged|"
                               + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                    : ErrorMsg.LISTENER_WAS_DEAD));
                }
            }
            return true;
        }
        return false;
    }

    private void viewToModel() {
        mLoanee = mVb.lendTo.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        // store the original loanee to avoid a trip to the database
        outState.putString(DBDefinitions.KEY_LOANEE, mOriginalLoanee);
        outState.putString(BKEY_NEW_LOANEE, mLoanee);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    @Override
    public void setListener(@NonNull final BookChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
