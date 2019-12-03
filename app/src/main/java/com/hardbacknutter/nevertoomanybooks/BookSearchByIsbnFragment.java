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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.AltIsbnTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn;
import com.hardbacknutter.nevertoomanybooks.widgets.IsbnValidationTextWatcher;

/**
 * The input field is not being limited in length. This is to allow entering UPC numbers.
 */
public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment {

    /** log tag. */
    public static final String TAG = "BookSearchByIsbnFrag";

    static final String BKEY_SCAN_MODE = TAG + ":scanMode";
    static final String BKEY_STRICT_ISBN = TAG + ":strictIsbn";
    static final String BKEY_ISBN = TAG + ":isbn";

    /** wait for the scanner; milliseconds. */
    private static final long SCANNER_WAIT = 1_000;
    /** repeat waiting for the scanner 3 times before reporting failure. */
    private static final int SCANNER_RETRY = 3;

    /** User input field. */
    @Nullable
    private EditIsbn mIsbnView;

    @Nullable
    private Button mAltIsbnButton;

    private boolean mScanMode;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;
    private boolean mStrictIsbn = true;
    /** The current ISBN text. */
    private String mIsbn;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mandatory
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAltIsbnButton = view.findViewById(R.id.altIsbn);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // stop lint being very annoying...
        Objects.requireNonNull(mIsbnView);
        Objects.requireNonNull(mAltIsbnButton);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_isbn);

        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mStrictIsbn = args.getBoolean(BKEY_STRICT_ISBN, true);
            mScanMode = args.getBoolean(BKEY_SCAN_MODE, false);
            mIsbnView.setText(args.getString(BKEY_ISBN, ""));
        }

        mScannerModel = new ViewModelProvider(this).get(ScannerViewModel.class);

        View view = getView();

        //noinspection ConstantConditions
        view.findViewById(R.id.key_0).setOnClickListener(v -> mIsbnView.onKey("0"));
        view.findViewById(R.id.key_1).setOnClickListener(v -> mIsbnView.onKey("1"));
        view.findViewById(R.id.key_2).setOnClickListener(v -> mIsbnView.onKey("2"));
        view.findViewById(R.id.key_3).setOnClickListener(v -> mIsbnView.onKey("3"));
        view.findViewById(R.id.key_4).setOnClickListener(v -> mIsbnView.onKey("4"));
        view.findViewById(R.id.key_5).setOnClickListener(v -> mIsbnView.onKey("5"));
        view.findViewById(R.id.key_6).setOnClickListener(v -> mIsbnView.onKey("6"));
        view.findViewById(R.id.key_7).setOnClickListener(v -> mIsbnView.onKey("7"));
        view.findViewById(R.id.key_8).setOnClickListener(v -> mIsbnView.onKey("8"));
        view.findViewById(R.id.key_9).setOnClickListener(v -> mIsbnView.onKey("9"));
        view.findViewById(R.id.key_X).setOnClickListener(v -> mIsbnView.onKey("X"));

        Button delBtn = view.findViewById(R.id.isbn_del);
        delBtn.setOnClickListener(v -> mIsbnView.onKey(KeyEvent.KEYCODE_DEL));
        delBtn.setOnLongClickListener(v -> {
            mIsbnView.setText("");
            return true;
        });

        mIsbnView.addTextChangedListener(new IsbnValidationTextWatcher(mIsbnView));
        mIsbnView.addTextChangedListener(new AltIsbnTextWatcher(mIsbnView, mAltIsbnButton));

        view.findViewById(R.id.btn_scan).setOnClickListener(v -> {
            mScanMode = true;
            startScan();
        });

        view.findViewById(R.id.btn_search).setOnClickListener(v -> {
            //noinspection ConstantConditions
            prepareSearch(mIsbnView.getText().toString().trim());
        });

        // auto-start scanner first time.
        if (mScanMode && mScannerModel.isFirstStart()) {
            startScan();
        }

//        if (savedInstanceState == null) {
//            //noinspection ConstantConditions
//            mSearchCoordinator.getSiteList().promptToRegister(getContext(), false, "search");
//        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_STRICT_ISBN, 0, R.string.menu_strict_isbn)
            .setCheckable(true)
            .setChecked(mStrictIsbn)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_STRICT_ISBN) {
            mStrictIsbn = !item.isChecked();
            item.setChecked(mStrictIsbn);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        mIsbn = mIsbnView.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(BKEY_ISBN, mIsbn);
        outState.putBoolean(BKEY_STRICT_ISBN, mStrictIsbn);
        outState.putBoolean(BKEY_SCAN_MODE, mScanMode);
    }

    @Override
    void onCancelled() {
        super.onCancelled();

        if (mScanMode) {
            startScan();
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
            case UniqueId.REQ_SCAN_BARCODE: {
                //noinspection ConstantConditions
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {

                    if (BuildConfig.DEBUG) {
                        // detect emulator for testing
                        if (Build.PRODUCT.startsWith("sdk")) {
                            // when used, the file must be in the root external app dir.
                            //noinspection ConstantConditions
                            File file = new File(StorageUtils.getRootDir(getContext()),
                                                 "barcode.jpg");
                            if (file.exists()) {
                                Bitmap dummy = BitmapFactory.decodeFile(file.getAbsolutePath());
                                if (data != null
                                    && data.getExtras() != null
                                    && data.getExtras().containsKey("data")) {
                                    data.putExtra("data", dummy);
                                } else {
                                    try {
                                        StorageUtils.copyFile(file, CameraHelper.getDefaultFile());
                                    } catch (@NonNull final IOException e) {
                                        Log.d(TAG, "onActivityResult", e);
                                    }
                                }
                            }
                        }
                    }
                    Scanner scanner = mScannerModel.getScanner();
                    //noinspection ConstantConditions
                    String barCode = scanner.getBarcode(data);
                    if (barCode != null) {
                        //noinspection ConstantConditions
                        mIsbnView.setText(barCode);
                        prepareSearch(barCode);
                        return;
                    }
                }

                mScanMode = false;
                return;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                // go scan next book until the user cancels scanning.
                if (mScanMode) {
                    startScan();
                }
                break;
            }
            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
                // Make sure the scanner gets re-initialised.
                if (mScanMode) {
                    //noinspection ConstantConditions
                    mScannerModel.setScanner(null);
                    startScan();
                }
                break;
            }
            case UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES: {
                if (mScanMode) {
                    if (resultCode == Activity.RESULT_OK) {
                        // go scan next book until the user cancels scanning.
                        startScan();
                    } else {
                        mScanMode = false;
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
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        //noinspection ConstantConditions
        mIsbnView.setText("");
    }

    /**
     * Search with ISBN.
     * <p>
     *
     * @param isbnSearchText isbn text to search for.
     *                       Must be 10 characters (or more) to even consider a search.
     */
    private void prepareSearch(@NonNull final String isbnSearchText) {

        // ALWAYS try to convert UPC numbers.
        String isbn = ISBN.upc2isbn(isbnSearchText);

        // sanity check
        if (mStrictIsbn && isbn.length() < 10) {
            return;
        }

        // not a valid ISBN and we're in strict mode ?
        if (mStrictIsbn && !ISBN.isValid(isbn)) {
            if (mScanMode) {
                //noinspection ConstantConditions
                mScannerModel.onInvalidBeep(getContext());
            }

            String msg = getString(R.string.warning_x_is_not_a_valid_isbn, isbn);
            //noinspection ConstantConditions
            Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).show();

            if (mScanMode) {
                startScan();
            }
            return;
        }

        // valid or not, this is the ISBN we will be searching for.
        mIsbn = isbn;

        // at this point, we have a valid isbn (if strict)
        if (mStrictIsbn && mScanMode) {
            //noinspection ConstantConditions
            mScannerModel.onValidBeep(getContext());
        }

        // See if ISBN already exists in our database, if not then start the search.
        final long existingId = mDb.getBookIdFromIsbn(isbn, true);
        if (existingId != 0) {
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.title_duplicate_book)
                    .setMessage(R.string.confirm_duplicate_book_message)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        clearPreviousSearchCriteria();
                        if (mScanMode) {
                            startScan();
                        }
                    })
                    // User wants to review the existing book
                    .setNeutralButton(R.string.edit, (dialog, which) -> {
                        Intent intent = new Intent(getContext(), EditBookActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, existingId);
                        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                    })
                    // User wants to add regardless
                    .setPositiveButton(R.string.btn_confirm_add, (dialog, which) -> startSearch())
                    .create()
                    .show();
        } else {
            startSearch();
        }
    }

    @Override
    protected boolean onSearch() {
        mSearchCoordinator.setIsbnSearchText(mIsbn, mStrictIsbn);
        return mSearchCoordinator.searchByText();
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title or at least 3 fields.
        // The isbn field will always be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a
        // third field.
        String title = bookData.getString(DBDefinitions.KEY_TITLE);
        if ((title != null && !title.isEmpty()) || bookData.size() > 2) {
            Intent intent = new Intent(getContext(), EditBookActivity.class)
                    .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
            startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
            clearPreviousSearchCriteria();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_no_matching_book_found,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Start scanner activity.
     */
    private void startScan() {
        //noinspection ConstantConditions
        Scanner scanner = mScannerModel.getScanner();
        try {
            if (scanner == null) {
                scanner = ScannerManager.getScanner(mHostActivity);
                if (scanner == null) {
                    noScanner();
                    return;
                }
                mScannerModel.setScanner(scanner);
            }

            // this is really for the Google barcode library which is loaded on first access.
            // We're very conservative/paranoid here, as we already triggered a load during startup.
            int retry = SCANNER_RETRY;
            while (!scanner.isOperational() && retry > 0) {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.info_waiting_for_scanner,
                              Snackbar.LENGTH_LONG).show();

                try {
                    Thread.sleep(SCANNER_WAIT);
                } catch (@NonNull final InterruptedException ignore) {
                }
                if (scanner.isOperational()) {
                    break;
                }
                retry--;
            }

            // ready or not, go scan
            if (!mScannerModel.isScannerStarted()) {
                if (!scanner.startActivityForResult(this, UniqueId.REQ_SCAN_BARCODE)) {

                    mScanMode = false;

                    // the scanner was loaded, but (still) refuses to start.
                    String msg = getString(R.string.warning_scanner_failed_to_start)
                                 + "\n\n"
                                 + getString(R.string.warning_scanner_retry_install);

                    //noinspection ConstantConditions
                    new AlertDialog.Builder(getContext())
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setTitle(R.string.pg_barcode_scanner)
                            .setMessage(msg)
                            .setNeutralButton(R.string.lbl_settings, (dialog, which) -> {
                                Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                                        .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                                  Prefs.psk_barcode_scanner);

                                mHostActivity.startActivityForResult(
                                        intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                            })
                            .setPositiveButton(R.string.retry, (d, w) -> {
                                // yes, nothing to do.
                                // Closing this dialog should bring up the scanner again
                            })
                            .create()
                            .show();
                    return;
                }
                mScannerModel.setScannerStarted(true);
            }

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            noScanner();
        }
    }

    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     */
    private void noScanner() {
        mScanMode = false;

        String msg = getString(R.string.info_bad_scanner) + '\n'
                     + getString(R.string.info_install_scanner_recommendation);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                            .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .create()
                .show();
    }
}
