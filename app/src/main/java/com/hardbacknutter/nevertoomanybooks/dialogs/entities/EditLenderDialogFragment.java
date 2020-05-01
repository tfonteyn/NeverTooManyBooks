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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to create a new loan, or edit an existing one.
 */
public class EditLenderDialogFragment
        extends DialogFragment
        implements BookChangedListenerOwner {

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
    @Nullable
    private String mDialogTitle;
    /** View Binding. */
    private DialogEditLoanBinding mVb;

    /** The book we're lending. */
    private long mBookId;
    /** Displayed for info. */
    private String mTitle;

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
     * Constructor.
     *
     * @param bookId to lend
     * @param title  informational display only
     *
     * @return instance
     */
    public static DialogFragment newInstance(final long bookId,
                                             @NonNull final String title) {
        final DialogFragment frag = new EditLenderDialogFragment();
        final Bundle args = new Bundle(2);
        args.putLong(DBDefinitions.KEY_PK_ID, bookId);
        args.putString(DBDefinitions.KEY_TITLE, title);
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
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mDialogTitle = args.getString(StandardDialogs.BKEY_DIALOG_TITLE,
                                      getString(R.string.lbl_lend_to));

        mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
        mTitle = args.getString(DBDefinitions.KEY_TITLE);

        if (savedInstanceState == null) {
            mOriginalLoanee = mDb.getLoaneeByBookId(mBookId);
            mLoanee = mOriginalLoanee;
        } else {
            mOriginalLoanee = savedInstanceState.getString(DBDefinitions.KEY_LOANEE);
            mLoanee = savedInstanceState.getString(BKEY_NEW_LOANEE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = DialogEditLoanBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setTitle(mDialogTitle);
        mVb.toolbar.inflateMenu(R.menu.toolbar_save);
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mVb.title.setText(mTitle);
        mVb.loanedTo.setText(mLoanee);

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
        mVb.loanedTo.setAdapter(adapter);
    }

    private boolean saveChanges() {
        mLoanee = mVb.loanedTo.getText().toString().trim();

        final Bundle data;
        if (!mLoanee.isEmpty()) {
            // lend book, reluctantly...
            if (!mLoanee.equalsIgnoreCase(mOriginalLoanee)) {
                mDb.lendBook(mBookId, mLoanee);
            }
            data = new Bundle();
            data.putString(DBDefinitions.KEY_LOANEE, mLoanee);

        } else {
            // return the book
            mDb.lendBook(mBookId, null);
            data = null;
        }

        if (mListener != null && mListener.get() != null) {
            mListener.get().onBookChanged(mBookId, BookChangedListener.BOOK_LOANEE, data);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onBookChanged|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
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
        mLoanee = mVb.loanedTo.getText().toString().trim();
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
