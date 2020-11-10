/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookSearchByIsbnViewModel;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByIsbnFrag";

    public static final String BKEY_SCAN_MODE = TAG + ":scanMode";
    private static final String BKEY_FIRST_START = TAG + ":firstStart";
    private static final String BKEY_STARTED = TAG + ":started";

    /** {@code true} if we are in scan mode. */
    private boolean mInScanMode;
    /** Only start the scanner automatically upon the very first start of the fragment. */
    private boolean mFirstStart = true;
    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;
    /** The scanner. */
    private ActivityResultLauncher<Fragment> mScannerLauncher;
    /** After a successful scan/search, the data is offered for editing. */
    private final ActivityResultLauncher<Long> mEditExistingBookLauncher =
            registerForActivityResult(new EditBookByIdContract(), this::onBookEditingDone);
    /** View Binding. */
    private FragmentBooksearchByIsbnBinding mVb;
    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;

    private BookSearchByIsbnViewModel mVm;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        final Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mInScanMode = args.getBoolean(BKEY_SCAN_MODE, false);
            mFirstStart = args.getBoolean(BKEY_FIRST_START, true);
            mScannerStarted = args.getBoolean(BKEY_STARTED, false);
        }

        mScannerLauncher = registerForActivityResult(new ScannerContract(), barCode -> {
            mScannerStarted = false;
            if (barCode != null) {
                mVb.isbn.setText(barCode);
                prepareSearch(barCode);
            }
        });
    }

    @NonNull
    @Override
    public ActivityResultViewModel getActivityResultViewModel() {
        return mVm;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentBooksearchByIsbnBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVm = new ViewModelProvider(this).get(BookSearchByIsbnViewModel.class);
        mVm.init();

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_search_isbn);

        mVb.isbn.setText(mCoordinator.getIsbnSearchText());

        mVb.key0.setOnClickListener(v -> mVb.isbn.onKey('0'));
        mVb.key1.setOnClickListener(v -> mVb.isbn.onKey('1'));
        mVb.key2.setOnClickListener(v -> mVb.isbn.onKey('2'));
        mVb.key3.setOnClickListener(v -> mVb.isbn.onKey('3'));
        mVb.key4.setOnClickListener(v -> mVb.isbn.onKey('4'));
        mVb.key5.setOnClickListener(v -> mVb.isbn.onKey('5'));
        mVb.key6.setOnClickListener(v -> mVb.isbn.onKey('6'));
        mVb.key7.setOnClickListener(v -> mVb.isbn.onKey('7'));
        mVb.key8.setOnClickListener(v -> mVb.isbn.onKey('8'));
        mVb.key9.setOnClickListener(v -> mVb.isbn.onKey('9'));
        mVb.keyX.setOnClickListener(v -> mVb.isbn.onKey('X'));

        mVb.isbnDel.setOnClickListener(v -> mVb.isbn.onKey(KeyEvent.KEYCODE_DEL));
        mVb.isbnDel.setOnLongClickListener(v -> {
            mVb.isbn.setText("");
            return true;
        });

        // The search preference determines the level here; NOT the 'edit book'
        final int isbnValidityCheck = mCoordinator.isStrictIsbn() ? ISBN.VALIDITY_STRICT
                                                                  : ISBN.VALIDITY_NONE;

        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn, isbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
        mIsbnValidationTextWatcher = new ISBN.ValidationTextWatcher(
                mVb.lblIsbn, mVb.isbn, isbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);

        //noinspection ConstantConditions
        mVb.btnSearch.setOnClickListener(v -> prepareSearch(mVb.isbn.getText().toString().trim()));

        //noinspection VariableNotUsedInsideIf
        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            Site.promptToRegister(getContext(), mCoordinator.getSiteList(),
                                  "searchByIsbn", this::afterOnViewCreated);
        } else {
            afterOnViewCreated();
        }
    }

    private void afterOnViewCreated() {
        // only auto-start scanner the first time this fragment starts
        if (mInScanMode && mFirstStart) {
            mFirstStart = false;
            scan();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_SCAN_BARCODE, 0, R.string.btn_scan_barcode)
            .setIcon(R.drawable.ic_barcode)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_ISBN_VALIDITY_STRICT, 0, R.string.lbl_strict_isbn)
            .setCheckable(true)
            .setChecked(mCoordinator.isStrictIsbn())
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_SCAN_BARCODE) {
            mInScanMode = true;
            scan();
            return true;

        } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
            final boolean checked = !item.isChecked();
            item.setChecked(checked);
            mCoordinator.setStrictIsbn(checked);

            final int validity = checked ? ISBN.VALIDITY_STRICT : ISBN.VALIDITY_NONE;
            mIsbnCleanupTextWatcher.setValidityLevel(validity);
            mIsbnValidationTextWatcher.setValidityLevel(validity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        mCoordinator.setIsbnSearchText(mVb.isbn.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_SCAN_MODE, mInScanMode);
        outState.putBoolean(BKEY_FIRST_START, mFirstStart);
        outState.putBoolean(BKEY_STARTED, mScannerStarted);
    }

    /**
     * Start scanner activity.
     */
    public void scan() {
        if (!mScannerStarted) {
            mScannerStarted = true;
            mScannerLauncher.launch(this);
        }
    }

    /**
     * Search with ISBN or, if allowed, with a generic code.
     *
     * @param userEntry text to search for.
     */
    private void prepareSearch(@NonNull final String userEntry) {

        final boolean strictIsbn = mCoordinator.isStrictIsbn();
        final ISBN code = new ISBN(userEntry, strictIsbn);

        // not a valid code ?
        if (!code.isValid(strictIsbn)) {
            if (mInScanMode) {
                onInvalidBarcodeBeep();
            }

            showError(mVb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code, userEntry));

            if (mInScanMode) {
                scan();
            }
            return;
        }

        // at this point, we know we have a searchable code
        mCoordinator.setIsbnSearchText(code.asText());

        if (strictIsbn && mInScanMode) {
            onValidBarcodeBeep();
        }

        // See if ISBN already exists in our database, if not then start the search.
        final ArrayList<Long> existingIds = mVm.getBookIdsByIsbn(code);
        if (existingIds.isEmpty()) {
            startSearch();
        } else {
            // we always use the first one... really should offer the user a choice.
            final long firstFound = existingIds.get(0);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(R.string.confirm_duplicate_book_message)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {
                        onClearPreviousSearchCriteria();
                        if (mInScanMode) {
                            scan();
                        }
                    })
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w)
                            -> mEditExistingBookLauncher.launch(firstFound))
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> startSearch())
                    .create()
                    .show();
        }
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBDefinitions.KEY_TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            Snackbar.make(mVb.isbn, R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    void onSearchCancelled() {
        super.onSearchCancelled();
        // Leave scan mode until the user manually starts it again
        mInScanMode = false;
    }

    @Override
    void onClearPreviousSearchCriteria() {
        super.onClearPreviousSearchCriteria();
        mVb.isbn.setText("");
    }

    @Override
    void onBookEditingDone(@Nullable final Bundle data) {
        super.onBookEditingDone(data);
        // go scan next book until the user cancels scanning.
        if (mInScanMode) {
            scan();
        }
    }

    /**
     * Optionally beep if the scan succeeded.
     */
    private void onValidBarcodeBeep() {
        //noinspection ConstantConditions
        if (PreferenceManager.getDefaultSharedPreferences(getContext())
                             .getBoolean(Prefs.pk_sounds_scan_isbn_valid, false)) {
            SoundManager.playFile(getContext(), R.raw.beep_high);
        }
    }

    /**
     * Optionally beep if the scan failed.
     */
    private void onInvalidBarcodeBeep() {
        //noinspection ConstantConditions
        if (PreferenceManager.getDefaultSharedPreferences(getContext())
                             .getBoolean(Prefs.pk_sounds_scan_isbn_invalid, true)) {
            SoundManager.playFile(getContext(), R.raw.beep_low);
        }
    }
}
