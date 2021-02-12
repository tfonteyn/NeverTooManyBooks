/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to create a new loan, edit an existing one or remove it (book is returned).
 * <p>
 * Note the special treatment of the Book's current/original loanee.
 * This is done to minimize trips to the database.
 */
public class EditLenderDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "LendBookDialogFrag";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";
    /** savedInstanceState key for the newly entered loanee name. */
    private static final String SIS_NEW_LOANEE = TAG + ':' + DBDefinitions.KEY_LOANEE;
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    /** Database Access. */
    private DAO mDb;
    /** View Binding. */
    private DialogEditLoanBinding mVb;
    /** The book we're lending. */
    private long mBookId;
    /** Displayed for info. */
    private String mBookTitle;
    /**
     * The person who currently has the book.
     * Will be {@code null} if the book is available.
     * <p>
     * {@link DBDefinitions#KEY_LOANEE} in savedInstanceState.
     */
    @Nullable
    private String mOriginalLoanee;
    /**
     * The loanee being edited.
     * <p>
     * {@link #SIS_NEW_LOANEE} in savedInstanceState.
     */
    @Nullable
    private String mLoanee;
    private ArrayList<String> mPeople;
    private ExtArrayAdapter<String> mAdapter;

    /**
     * See <a href="https://developer.android.com/training/permissions/requesting">
     * developer.android.com</a>
     */
    @SuppressLint("MissingPermission")
    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (isGranted) {
                            addContacts();
                        }
                    });

    /**
     * No-arg constructor for OS use.
     */
    public EditLenderDialogFragment() {
        super(R.layout.dialog_edit_loan);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);
        // get previously used lender names
        mPeople = mDb.getLoanees();

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mBookId = args.getLong(DBDefinitions.KEY_PK_ID);
        mBookTitle = Objects.requireNonNull(args.getString(DBDefinitions.KEY_TITLE), "KEY_TITLE");

        if (savedInstanceState == null) {
            mOriginalLoanee = mDb.getLoaneeByBookId(mBookId);
            mLoanee = mOriginalLoanee;
        } else {
            mOriginalLoanee = savedInstanceState.getString(DBDefinitions.KEY_LOANEE);
            mLoanee = savedInstanceState.getString(SIS_NEW_LOANEE);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditLoanBinding.bind(view);

        mVb.toolbar.setSubtitle(mBookTitle);

        //noinspection ConstantConditions
        mAdapter = new ExtArrayAdapter<>(getContext(), R.layout.dropdown_menu_popup_item,
                                         ExtArrayAdapter.FilterType.Diacritic, mPeople);
        mVb.lendTo.setAdapter(mAdapter);
        mVb.lendTo.setText(mLoanee);

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            addContacts();
            // } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            // FIXME: implement shouldShowRequestPermissionRationale
            //  but without using a dialog box inside a dialog box
        } else {
            mRequestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    private void addContacts() {
        // LinkedHashSet to remove duplicates
        final Set<String> contacts = new LinkedHashSet<>(mPeople);
        //noinspection ConstantConditions
        final ContentResolver cr = getContext().getContentResolver();
        try (Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI,
                                      new String[]{ContactsContract.Contacts._ID,
                                                   ContactsContract.Contacts.LOOKUP_KEY,
                                                   ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                                      null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    final String name = cursor.getString(cursor.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                    contacts.add(name);
                }
            }
        }

        final List<String> sorted = new ArrayList<>(contacts);
        Collections.sort(sorted);
        mAdapter.clear();
        mAdapter.addAll(sorted);
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        // anything actually changed ?
        //noinspection ConstantConditions
        if (mLoanee.equalsIgnoreCase(mOriginalLoanee)) {
            return true;
        }

        final boolean success;
        if (!mLoanee.isEmpty()) {
            // lend book, reluctantly...
            success = mDb.setLoanee(mBookId, mLoanee);
        } else {
            // return the book
            success = mDb.setLoanee(mBookId, null);
        }

        if (success) {
            Launcher.setResult(this, mRequestKey, mBookId, mLoanee);
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
        outState.putString(SIS_NEW_LOANEE, mLoanee);
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

    public abstract static class Launcher
            extends FragmentLauncherBase {

        public Launcher(@NonNull final String requestKey) {
            super(requestKey);
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IntRange(from = 1) final long bookId,
                              @NonNull final String loanee) {
            final Bundle result = new Bundle(2);
            result.putLong(DBDefinitions.KEY_FK_BOOK, bookId);
            result.putString(DBDefinitions.KEY_LOANEE, loanee);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param book to lend
         */
        public void launch(@NonNull final Book book) {

            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putLong(DBDefinitions.KEY_PK_ID, book.getId());
            args.putString(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE));

            final DialogFragment frag = new EditLenderDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        /**
         * Launch the dialog.
         *
         * @param bookId    to lend
         * @param bookTitle displayed for info only
         */
        public void launch(@IntRange(from = 1) final long bookId,
                           @NonNull final String bookTitle) {

            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putLong(DBDefinitions.KEY_PK_ID, bookId);
            args.putString(DBDefinitions.KEY_TITLE, bookTitle);

            final DialogFragment frag = new EditLenderDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(SanityCheck.requirePositiveValue(result.getLong(DBDefinitions.KEY_FK_BOOK)),
                     Objects.requireNonNull(result.getString(DBDefinitions.KEY_LOANEE)));
        }

        /**
         * Callback handler.
         *
         * @param bookId the id of the updated book
         * @param loanee the name of the loanee, or {@code ""} for a returned book
         */
        public abstract void onResult(@IntRange(from = 1) long bookId,
                                      @NonNull String loanee);
    }
}
