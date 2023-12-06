/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.LoaneeDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Dialog to create a new loan, edit an existing one or remove it (book is returned).
 * <p>
 * Note the special treatment of the Book's current/original loanee.
 * This is done to minimize trips to the database.
 */
public class EditLenderDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "LendBookDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    /** savedInstanceState key for the newly entered loanee name. */
    private static final String SIS_NEW_LOANEE = TAG + ':' + DBKey.LOANEE_NAME;
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** View Binding. */
    private DialogEditLoanContentBinding vb;
    /** The book we're lending. */
    private long bookId;
    /** Displayed for info. */
    private String bookTitle;

    /**
     * The person who currently has the book.
     * Will be {@code null} if the book is available.
     * <p>
     * {@link DBKey#LOANEE_NAME} in savedInstanceState.
     */
    @Nullable
    private String loanee;

    /**
     * The loanee being edited.
     * <p>
     * {@link #SIS_NEW_LOANEE} in savedInstanceState.
     */
    @Nullable
    private String currentEdit;

    private List<String> people;
    private ExtArrayAdapter<String> adapter;

    /**
     * See <a href="https://developer.android.com/training/permissions/requesting">
     * developer.android.com</a>
     */
    @SuppressLint("MissingPermission")
    private final ActivityResultLauncher<String> requestPermissionLauncher =
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
        super(R.layout.dialog_edit_loan, R.layout.dialog_edit_loan_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final LoaneeDao loaneeDao = ServiceLocator.getInstance().getLoaneeDao();
        // get previously used lender names
        people = loaneeDao.getList();

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        bookId = args.getLong(DBKey.FK_BOOK);
        bookTitle = Objects.requireNonNull(args.getString(DBKey.TITLE), DBKey.TITLE);

        if (savedInstanceState == null) {
            loanee = loaneeDao.findLoaneeByBookId(bookId);
            currentEdit = loanee;
        } else {
            loanee = savedInstanceState.getString(DBKey.LOANEE_NAME);
            currentEdit = savedInstanceState.getString(SIS_NEW_LOANEE);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditLoanContentBinding.bind(view.findViewById(R.id.dialog_content));
        setSubtitle(bookTitle);

        //noinspection DataFlowIssue
        adapter = new ExtArrayAdapter<>(getContext(), R.layout.popup_dropdown_menu_item,
                                        ExtArrayAdapter.FilterType.Diacritic, people);
        vb.lendTo.setAdapter(adapter);
        vb.lendTo.setText(currentEdit);

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            addContacts();
            // } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            // FIXME: implement shouldShowRequestPermissionRationale
            //  but without using a dialog box inside a dialog box
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }

        vb.lendTo.requestFocus();
    }

    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    private void addContacts() {
        // LinkedHashSet to remove duplicates
        final Set<String> contacts = new LinkedHashSet<>(people);
        //noinspection DataFlowIssue
        final ContentResolver cr = getContext().getContentResolver();
        try (Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI,
                                      new String[]{ContactsContract.Contacts.LOOKUP_KEY,
                                              ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                                      null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    final String name = cursor.getString(cursor.getColumnIndexOrThrow(
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                    contacts.add(name);
                }
            }
        }

        final List<String> sorted = new ArrayList<>(contacts);
        Collections.sort(sorted);
        adapter.clear();
        adapter.addAll(sorted);
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        // anything actually changed ?
        //noinspection DataFlowIssue
        if (currentEdit.equalsIgnoreCase(loanee)) {
            return true;
        }

        if (ServiceLocator.getInstance().getLoaneeDao().setLoanee(bookId, currentEdit)) {
            Launcher.setResult(this, requestKey, bookId, currentEdit);
            return true;
        }
        return false;
    }

    private void viewToModel() {
        currentEdit = vb.lendTo.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        // store the original loanee to avoid a trip to the database
        outState.putString(DBKey.LOANEE_NAME, loanee);
        outState.putString(SIS_NEW_LOANEE, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public static class Launcher
            implements FragmentResultListener {

        @NonNull
        private final String requestKey;
        @NonNull
        private final ResultListener resultListener;
        private FragmentManager fragmentManager;

        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            this.requestKey = requestKey;
            this.resultListener = resultListener;
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IntRange(from = 1) final long bookId,
                              @NonNull final String loanee) {
            final Bundle result = new Bundle(2);
            result.putLong(DBKey.FK_BOOK, bookId);
            result.putString(DBKey.LOANEE_NAME, loanee);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param book to lend
         */
        public void launch(@NonNull final Book book) {
            launch(book.getId(), book.getTitle());
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
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putLong(DBKey.FK_BOOK, bookId);
            args.putString(DBKey.TITLE, bookTitle);

            final DialogFragment frag = new EditLenderDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            final long value = result.getLong(DBKey.FK_BOOK);
            if (value <= 0) {
                throw new IllegalArgumentException(DBKey.FK_BOOK);
            }
            resultListener.onResult(value,
                                    Objects.requireNonNull(result.getString(DBKey.LOANEE_NAME),
                                                           DBKey.LOANEE_NAME));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler.
             *
             * @param bookId the id of the updated book
             * @param loanee the name of the loanee, or {@code ""} for a returned book
             */
            void onResult(@IntRange(from = 1) long bookId,
                          @NonNull String loanee);
        }
    }
}
