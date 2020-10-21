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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.BarcodePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.PermissionsHelper;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment
        implements PermissionsHelper.RequestHandler {

    /** Log tag. */
    public static final String TAG = "BookSearchByIsbnFrag";

    static final String BKEY_SCAN_MODE = TAG + ":scanMode";

    /** {@code true} if we are in scan mode. */
    private boolean mInScanMode;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;
    /** View Binding. */
    private FragmentBooksearchByIsbnBinding mVb;

    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // Camera permissions
        onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        final Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mInScanMode = args.getBoolean(BKEY_SCAN_MODE, false);
        }
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

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_search_isbn);

        mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);

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
        // auto-start scanner first time.
        if (mInScanMode && mScannerModel != null && mScannerModel.isFirstStart()) {
            mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
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
            Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
            mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
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
    }

    @Override
    void onSearchCancelled() {
        super.onSearchCancelled();

        if (mInScanMode) {
            Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
            mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case RequestCode.BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                // go scan next book until the user cancels scanning.
                if (mInScanMode) {
                    Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                    mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
                }
                break;
            }
            case RequestCode.SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    if (BuildConfig.DEBUG /* always */) {
                        //noinspection ConstantConditions
                        mScannerModel.fakeScanInEmulator(getContext(), data);
                    }

                    //noinspection ConstantConditions
                    final String barCode = mScannerModel.getScanner()
                                                        .getBarcode(getContext(), data);
                    if (barCode != null) {
                        mVb.isbn.setText(barCode);
                        prepareSearch(barCode);
                        return;
                    }
                }

                mInScanMode = false;
                return;
            }

            // RequestCode.PREFERRED_SEARCH_SITES is handled in the parent class.
            case RequestCode.SETTINGS: {
                // Settings initiated from the local menu or dialog box.
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // update the search sites list.
                    final ArrayList<Site> sites =
                            data.getParcelableArrayListExtra(Site.Type.Data.getBundleKey());
                    if (sites != null) {
                        mCoordinator.setSiteList(sites);
                    }

                    // init the scanner if it was changed.
                    if (data.getBooleanExtra(BarcodePreferenceFragment.BKEY_SCANNER_MODIFIED,
                                             false)) {
                        Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                        mScannerModel.resetScanner();
                    }
                }

                if (mInScanMode) {
                    Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                    mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
                }
                break;
            }
            case RequestCode.UPDATE_GOOGLE_PLAY_SERVICES: {
                if (mInScanMode) {
                    if (resultCode == Activity.RESULT_OK) {
                        Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                        // go scan next book until the user cancels scanning.
                        mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
                    } else {
                        mInScanMode = false;
                    }
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    void onClearPreviousSearchCriteria() {
        super.onClearPreviousSearchCriteria();
        mVb.isbn.setText("");
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
                Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                //noinspection ConstantConditions
                mScannerModel.onInvalidBeep(getContext());
            }

            showError(mVb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code, userEntry));

            if (mInScanMode) {
                Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
            }
            return;
        }

        // at this point, we know we have a searchable code
        mCoordinator.setIsbnSearchText(code.asText());

        if (strictIsbn && mInScanMode) {
            Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
            //noinspection ConstantConditions
            mScannerModel.onValidBeep(getContext());
        }

        // See if ISBN already exists in our database, if not then start the search.
        final ArrayList<Long> existingIds = mDb.getBookIdsByIsbn(code);
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
                            Objects.requireNonNull(mScannerModel, ScannerViewModel.TAG);
                            mInScanMode = mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
                        }
                    })
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w) -> {
                        final Intent intent = new Intent(getContext(), EditBookActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, firstFound);
                        startActivityForResult(intent, RequestCode.BOOK_EDIT);
                    })
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
}
