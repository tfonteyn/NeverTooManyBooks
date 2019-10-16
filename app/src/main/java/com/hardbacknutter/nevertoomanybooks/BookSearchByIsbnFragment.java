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
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

// UI mode: try stopping the soft input keyboard to pop up at all cost when entering isbn....
//
//  field gets focus, up it pops
//        mIsbnView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(@NonNull final View v, final boolean hasFocus) {
//                if (v.equals(mIsbnView)) {
//                    InputMethodManager imm = (InputMethodManager)
//                            getSystemService(Context.INPUT_METHOD_SERVICE);
//                    imm.showSoftInput(mIsbnView, InputMethodManager.HIDE_IMPLICIT_ONLY);
//                }
//            }
//        });


// works but prevents the user from select/copy/past
// mIsbnView.setInputType(InputType.TYPE_NULL);
// mIsbnView.setTextIsSelectable(true);
// mIsbnView.setCursorVisible(true); // no effect

// field gets focus, up it pops
// InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
// imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0, null);

// field gets focus, up it pops
// mIsbnView.setShowSoftInputOnFocus(false);
// field gets focus, up it pops
// getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
// hide on entry, field gets focus, up it pops
//  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
// field gets focus, up it pops
// getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

public class BookSearchByIsbnFragment
        extends BookSearchBaseFragment {

    /** Fragment manager tag. */
    public static final String TAG = "BookSearchByIsbnFragment";

    /** option to start in scan mode (versus manual entry). */
    public static final String BKEY_IS_SCAN_MODE = TAG + ":isScanMode";

    /** all digits allowed in ASIN strings. */
    private static final String ASIN_DIGITS =
            "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** all digits in ISBN strings. */
    private static final String ISBN_DIGITS = "0123456789xX";
    /** listener/acceptor for all ISBN digits. */
    private static final DigitsKeyListener ISBN_LISTENER =
            DigitsKeyListener.getInstance(ISBN_DIGITS);
    /** filter to remove all ASIN digits from ISBN strings (leave xX!). */
    private static final Pattern ISBN_PATTERN =
            Pattern.compile("[abcdefghijklmnopqrstuvwyz]",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** wait for the scanner; milliseconds. */
    private static final long SCANNER_WAIT = 1_000;
    /** repeat waiting for the scanner 3 times before reporting failure. */
    private static final int SCANNER_RETRY = 3;
    /**
     * Flag to indicate the Activity should not call 'finish()' after a failure.
     */
    private boolean mKeepAlive;

    @Nullable
    private EditText mIsbnView;
    @Nullable
    private CompoundButton mAllowAsinCb;

    private ScannerViewModel mScannerModel;

    private final SearchCoordinator.SearchFinishedListener mSearchFinishedListener =
            new SearchCoordinator.SearchFinishedListener() {
                /**
                 * results of search.
                 * <p>
                 * The details will get sent to {@link EditBookActivity}
                 * <p>
                 * <br>{@inheritDoc}
                 */
                @Override
                public void onSearchFinished(final boolean wasCancelled,
                                             @NonNull final Bundle bookData) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
                        Logger.debugEnter(this, "onSearchFinished",
                                          "SearchCoordinatorId="
                                          + mBookSearchBaseModel.getSearchCoordinatorId());
                    }
                    try {
                        if (!wasCancelled) {
                            mTaskManager.sendHeaderUpdate(R.string.progress_msg_adding_book);
                            Intent intent = new Intent(getContext(), EditBookActivity.class)
                                                    .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                            startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);

                            // Clear the data entry fields ready for the next one
                            // (mScanMode has no view)
                            if (mIsbnView != null) {
                                mIsbnView.setText("");
                            }
                        } else {
                            // the search was cancelled, resume scanning
                            if (mScannerModel.isScanMode()) {
                                startScannerActivity();
                            }
                        }
                    } finally {
                        // Clean up
                        mBookSearchBaseModel.setSearchCoordinator(0);
                        // Make sure the base message will be empty.
                        mTaskManager.sendHeaderUpdate(null);
                    }
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mScannerModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        mScannerModel.init(getArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        // If we're in scan mode, we don't have a UI.
        if (mScannerModel.isScanMode()) {
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAllowAsinCb = view.findViewById(R.id.cbx_allow_asin);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // setup the UI if we have one.
        if (getView() != null) {
            initUI();
        }

        // first check if we already have an isbn from somewhere
        if (!mBookSearchBaseModel.getIsbnSearchText().isEmpty()) {
            if (mIsbnView != null) {
                mIsbnView.setText(mBookSearchBaseModel.getIsbnSearchText());
            }
            prepareSearch();
            return;
        }

        // scan mode ?
        if (mScannerModel.isScanMode()) {
            if (mScannerModel.isFirstStart()) {
                mScannerModel.clearFirstStart();
                startScannerActivity();
            }
        } else {
            // we're in ISBN keypad mode.
            //FIXME: find a solution for showing reg dialog AND starting a scan
            //noinspection ConstantConditions
            SearchSites.alertRegistrationBeneficial(getContext(), "search",
                                                    mBookSearchBaseModel.getSearchSites());
        }
    }

    //URGENT:  move asin to options menu + add to preferences
    // problem: asin not visible -> options menu itself not visible,
    // user enables amazon -> options menu itself still invisible.
//    @Override
//    @CallSuper
//    public void onCreateOptionsMenu(@NonNull final Menu menu,
//                                    @NonNull final MenuInflater inflater) {
//
//        menu.add(Menu.NONE, R.id.MENU_PREFS_ASIN,
//                 MenuHandler.ORDER_ASIN, R.string.lbl_allow_asin)
//            .setCheckable(true)
//            .setIcon(R.drawable.ic_check)
//            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
//
//        super.onCreateOptionsMenu(menu, inflater);
//    }
//
//    @Override
//    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
//        // if Amazon is enabled, we show the ASIN option; else make sure it's disabled.
//        boolean amazon = (mBookSearchBaseModel.getSearchSites() & SearchSites.AMAZON) != 0;
//        MenuItem asin = menu.findItem(R.id.MENU_PREFS_ASIN);
//        asin.setVisible(amazon);
//        if (!amazon) {
//            asin.setChecked(false);
//        }
//
//        super.onPrepareOptionsMenu(menu);
//    }
//
//    @Override
//    @CallSuper
//    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
//        //noinspection SwitchStatementWithTooFewBranches
//        switch (item.getItemId()) {
//            case R.id.MENU_PREFS_ASIN:
//                item.setChecked(!item.isChecked());
//                handleAsinClick(item.isChecked());
//                return true;
//
//            default:
//                return super.onOptionsItemSelected(item);
//        }
//    }

    /**
     * Setup the UI.
     */
    private void initUI() {

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_isbn);

        View view = getView();

        //noinspection ConstantConditions
        view.findViewById(R.id.isbn_0).setOnClickListener(v -> handleIsbnKey("0"));
        view.findViewById(R.id.isbn_1).setOnClickListener(v -> handleIsbnKey("1"));
        view.findViewById(R.id.isbn_2).setOnClickListener(v -> handleIsbnKey("2"));
        view.findViewById(R.id.isbn_3).setOnClickListener(v -> handleIsbnKey("3"));
        view.findViewById(R.id.isbn_4).setOnClickListener(v -> handleIsbnKey("4"));
        view.findViewById(R.id.isbn_5).setOnClickListener(v -> handleIsbnKey("5"));
        view.findViewById(R.id.isbn_6).setOnClickListener(v -> handleIsbnKey("6"));
        view.findViewById(R.id.isbn_7).setOnClickListener(v -> handleIsbnKey("7"));
        view.findViewById(R.id.isbn_8).setOnClickListener(v -> handleIsbnKey("8"));
        view.findViewById(R.id.isbn_9).setOnClickListener(v -> handleIsbnKey("9"));
        view.findViewById(R.id.isbn_X).setOnClickListener(v -> handleIsbnKey("X"));

        view.findViewById(R.id.isbn_del).setOnClickListener(v -> handleDeleteButton());
        view.findViewById(R.id.btn_search).setOnClickListener(v -> {
            //noinspection ConstantConditions
            mBookSearchBaseModel.setIsbnSearchText(mIsbnView.getText().toString().trim());
            prepareSearch();
        });

        if (mAllowAsinCb != null) {
            mAllowAsinCb.setOnCheckedChangeListener((v, isChecked) -> handleAsinClick(isChecked));
        }
    }

    /**
     * Handle character insertion at cursor position in EditText.
     *
     * @param keyChar the character generated by the virtual keypad.
     */
    private void handleIsbnKey(@NonNull final String keyChar) {
        @SuppressWarnings("ConstantConditions")
        int start = mIsbnView.getSelectionStart();
        int end = mIsbnView.getSelectionEnd();
        mIsbnView.getText().replace(start, end, keyChar);
        mIsbnView.setSelection(start + 1, start + 1);
    }

    private void handleDeleteButton() {
        try {
            @SuppressWarnings("ConstantConditions")
            int start = mIsbnView.getSelectionStart();
            int end = mIsbnView.getSelectionEnd();
            if (start < end) {
                // We have a selection. Delete it.
                mIsbnView.getText().replace(start, end, "");
                mIsbnView.setSelection(start, start);
            } else {
                // Delete char before cursor
                if (start > 0) {
                    mIsbnView.getText().replace(start - 1, start, "");
                    mIsbnView.setSelection(start - 1, start - 1);
                }
            }
        } catch (@NonNull final StringIndexOutOfBoundsException ignore) {
            //do nothing - empty string
        }
    }

    private void handleAsinClick(final boolean isChecked) {
        if (isChecked) {
            // over-optimisation... asin is used less than ISBN
            DigitsKeyListener asinListener = DigitsKeyListener.getInstance(ASIN_DIGITS);
            //noinspection ConstantConditions
            mIsbnView.setKeyListener(asinListener);
        } else {
            //noinspection ConstantConditions
            mIsbnView.setKeyListener(ISBN_LISTENER);
            // remove invalid digits
            String txt = mIsbnView.getText().toString().trim();
            mIsbnView.setText(ISBN_PATTERN.matcher(txt).replaceAll(""));
        }

        mIsbnView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    }

    @Override
    SearchCoordinator.SearchFinishedListener getSearchFinishedListener() {
        return mSearchFinishedListener;
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        switch (requestCode) {
            case UniqueId.REQ_IMAGE_FROM_SCANNER: {
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    //reminder: data will be null if we get a full size pic.

                    if (BuildConfig.DEBUG) {
                        // detect emulator for testing
                        if (Build.PRODUCT.startsWith("sdk")) {
                            // when used, the file must be in the root external app dir.
                            //noinspection ConstantConditions
                            File file = new File(StorageUtils.getRootDir(getContext()),
                                                 "barcode.jpg");
                            if (file.exists()) {
                                Bitmap dummy = BitmapFactory.decodeFile(file.getAbsolutePath());
                                if (data != null) {
                                    data.putExtra("data", dummy);
                                } else {
                                    try {
                                        StorageUtils.copyFile(file, CameraHelper.getDefaultFile());
                                    } catch (@NonNull final IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    Scanner scanner = mScannerModel.getScanner();
                    //noinspection ConstantConditions
                    String barCode = scanner.getBarcode(data);
                    if (barCode != null) {
                        mBookSearchBaseModel.setIsbnSearchText(barCode);
                        prepareSearch();
                        return;
                    }
                }

                scanFailedOrCancelled();
                return;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                if (mScannerModel.isScanMode()) {
                    // go scan next book until the user cancels scanning.
                    startScannerActivity();
                }
                break;
            }
            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
                // Make sure the scanner gets re-initialised.
                mScannerModel.setScanner(null);
                startScannerActivity();
                break;
            }
            case UniqueId.REQ_UPDATE_GOOGLE_PLAY_SERVICES: {
                if (resultCode == Activity.RESULT_OK) {
                    // go scan next book until the user cancels scanning.
                    startScannerActivity();
                } else {
                    scanFailedOrCancelled();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }

        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_ISBN, mBookSearchBaseModel.getIsbnSearchText());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mIsbnView != null) {
            mBookSearchBaseModel.setIsbnSearchText(mIsbnView.getText().toString().trim());
        }
    }

    /**
     * Search with ISBN.
     * <p>
     * mIsbnSearchText must be 10 characters (or more) to even consider a search.
     */
    private void prepareSearch() {
        String isbn = mBookSearchBaseModel.getIsbnSearchText();
        // sanity check
        if (isbn.length() < 10) {
            return;
        }

        // intercept UPC numbers
        isbn = ISBN.upc2isbn(isbn);
        if (isbn.length() < 10) {
            return;
        }

        // If the layout has an 'Allow ASIN' checkbox, see if it is checked.
        final boolean allowAsin = mAllowAsinCb != null && mAllowAsinCb.isChecked();

        // not a valid ISBN/ASIN ?
        if (!ISBN.isValid(isbn) && (!allowAsin || !ISBN.isValidAsin(isbn))) {
            isbnInvalid(isbn, allowAsin);
            return;
        }
        // at this point, we have a valid isbn/asin.
        if (mScannerModel.isScanMode()) {
            // Optionally beep if scan was valid.
            //noinspection ConstantConditions
            SoundManager.beepHigh(getContext());
        }

        // See if ISBN already exists in our database, if not then start the search and get details.
        final long existingId = mBookSearchBaseModel.getDb().getBookIdFromIsbn(isbn, true);
        if (existingId != 0) {
            isbnAlreadyPresent(existingId);
        } else {
            startSearch();
        }
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * Or restart the scanner if applicable.
     */
    boolean startSearch() {
        if (!super.startSearch()) {
            if (mScannerModel.isScanMode()) {
                // we don't have an isbn, but we're scanning. Restart scanner.
                startScannerActivity();
            }
        }
        return true;
    }

    /**
     * ISBN was already present, Verify what the user wants - this can be a dangerous operation.
     *
     * @param existingId of the book we already have in the database
     */
    private void isbnAlreadyPresent(final long existingId) {
        @SuppressWarnings("ConstantConditions")
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                                     .setTitle(R.string.title_duplicate_book)
                                     .setIconAttribute(android.R.attr.alertDialogIcon)
                                     .setMessage(R.string.confirm_duplicate_book_message)
                                     .create();

        // User wants to add regardless
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.btn_confirm_add),
                         (d, which) -> startSearch());

        // User wants to review the existing book
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.menu_edit),
                         (d, which) -> {
                             Intent intent = new Intent(getContext(), EditBookActivity.class)
                                                     .putExtra(DBDefinitions.KEY_PK_ID, existingId);
                             startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                         });

        // User aborts this isbn
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                         (d, which) -> {
                             // reset the now-discarded details
                             mBookSearchBaseModel.clearSearchText();
                             if (mScannerModel.isScanMode()) {
                                 startScannerActivity();
                             }
                         });
        dialog.show();
    }

    /**
     * ISBN was invalid. Inform the user and go back to either the UI or the scanner.
     *
     * @param isbn      the isbn which was invalid
     * @param allowAsin whether ASIN was enabled or not
     */
    private void isbnInvalid(@NonNull final String isbn,
                             final boolean allowAsin) {
        if (mScannerModel.isScanMode()) {
            // Optionally beep if scan failed.
            //noinspection ConstantConditions
            SoundManager.beepLow(getContext());
        }
        int msg;
        if (allowAsin) {
            msg = R.string.warning_x_is_not_a_valid_isbn_or_asin;
        } else {
            msg = R.string.warning_x_is_not_a_valid_isbn;
        }

        if (mScannerModel.isScanMode()) {
            //noinspection ConstantConditions
            UserMessage.show(getActivity(), getString(msg, isbn));
            // reset the now-discarded details
            mBookSearchBaseModel.clearSearchText();
            startScannerActivity();
        } else {
            //noinspection ConstantConditions
            UserMessage.show(mIsbnView, getString(msg, isbn));
        }
    }

    /**
     * Start scanner activity.
     */
    private void startScannerActivity() {
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
                UserMessage.show(getActivity(), R.string.info_waiting_for_scanner);

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
                if (!scanner.startActivityForResult(this, UniqueId.REQ_IMAGE_FROM_SCANNER)) {
                    // the scanner was loaded, but (still) refuses to start.
                    scannerStartFailed();
                    return;
                }
                mScannerModel.setScannerStarted(true);
            }

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), this, e);
            noScanner();
        }
    }

    private void scannerStartFailed() {
        String msg = getString(R.string.warning_scanner_failed_to_start)
                     + "\n\n"
                     + getString(R.string.warning_scanner_retry_install);

        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setNeutralButton(R.string.lbl_settings, (d, w) -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                                            .putExtra(BaseSettingsFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .setPositiveButton(R.string.retry, (d, w) -> {

                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * None of the scanners was available or working correctly.
     * Tell the user, and take them to the preferences to select a scanner.
     */
    private void noScanner() {
        String msg = getString(R.string.info_bad_scanner) + '\n'
                     + getString(R.string.info_install_scanner_recommendation);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.pg_barcode_scanner)
                .setMessage(msg)
                .setOnDismissListener(d -> {
                    Intent intent = new Intent(mHostActivity, SettingsActivity.class)
                                            .putExtra(BaseSettingsFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                                      Prefs.psk_barcode_scanner);

                    mHostActivity.startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
                })
                .create()
                .show();
        mKeepAlive = true;
    }

    /**
     * Scanner Cancelled/failed.
     * Pass the last book we got back to our caller and finish here.
     */
    private void scanFailedOrCancelled() {
        Intent lastBookData = mBookSearchBaseModel.getLastBookData();
        if (lastBookData != null) {
            mHostActivity.setResult(Activity.RESULT_OK, lastBookData);
        }

        // and exit if no dialog present.
        if (!mKeepAlive) {
            mHostActivity.finish();
        }
    }
}
