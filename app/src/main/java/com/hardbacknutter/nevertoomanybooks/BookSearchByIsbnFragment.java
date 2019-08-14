/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.scanner.Scanner;
import com.hardbacknutter.nevertoomanybooks.scanner.ScannerManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookSearchByScanModel;

// Try stopping the soft input keyboard to pop up at all cost when entering isbn....

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
            "1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    /** all digits in ISBN strings. */
    private static final String ISBN_DIGITS = "0123456789xX";
    /** listener/acceptor for all ISBN digits. */
    private static final DigitsKeyListener ISBN_LISTENER =
            DigitsKeyListener.getInstance(ISBN_DIGITS);
    /** filter to remove all ASIN digits from ISBN strings (leave xX!). */
    private static final Pattern ISBN_PATTERN =
            Pattern.compile("[qwertyuiopasdfghjklzcvbnmQWERTYUIOPASDFGHJKLZCVBNM]");

    /**
     * Flag to indicate the Activity should not 'finish()' because an alert is being displayed.
     * The alert will call finish().
     */
    private boolean mDisplayingAlert;
    /** The preferred (or found) scanner. */
    @Nullable
    private Scanner mScanner;
    @Nullable
    private EditText mIsbnView;
    @Nullable
    private CompoundButton mAllowAsinCb;

    private BookSearchByScanModel mModel;

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
                            if (mModel.isScanMode()) {
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

        mModel = new ViewModelProvider(this).get(BookSearchByScanModel.class);
        mModel.init(getArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        // If we're in scan mode, we don't have a UI.
        if (mModel.isScanMode()) {
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_booksearch_by_isbn, container, false);
        mIsbnView = view.findViewById(R.id.isbn);
        mAllowAsinCb = view.findViewById(R.id.allow_asin);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // setup the UI if we have one.
        View root = getView();
        if (root != null) {
            initUI(root);
        } else {
            initScanner();
        }
    }

    private void initScanner() {
        //noinspection ConstantConditions
        mScanner = ScannerManager.getScanner(getContext());
        if (mScanner == null) {
            ScannerManager.promptForScannerInstallAndFinish(mActivity, true);
            // Prevent our activity to finish.
            //mDisplayingAlert = true;
        } else {
            // we have a scanner, but first check if we already have an isbn from somewhere
            if (!mBookSearchBaseModel.getIsbnSearchText().isEmpty()) {
                prepareSearch();
            } else {
                // let's scan....
                if (mModel.isFirstStart()) {
                    mModel.setFirstStart(false);
                    try {
                        startScannerActivity();
                    } catch (@NonNull final RuntimeException e) {
                        // we had a scanner setup, but something went wrong starting it.
                        ScannerManager.promptForScannerInstallAndFinish(mActivity, false);
                        // Prevent our activity to finish.
                        //mDisplayingAlert = true;
                    }
                }
            }
        }
    }

    /**
     * Setup the UI.
     */
    private void initUI(@NonNull final View root) {

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_isbn);

        if (mAllowAsinCb != null) {
            initAsin();
        }

        initKeypadButton(R.id.isbn_0, "0");
        initKeypadButton(R.id.isbn_1, "1");
        initKeypadButton(R.id.isbn_2, "2");
        initKeypadButton(R.id.isbn_3, "3");
        initKeypadButton(R.id.isbn_4, "4");
        initKeypadButton(R.id.isbn_5, "5");
        initKeypadButton(R.id.isbn_6, "6");
        initKeypadButton(R.id.isbn_7, "7");
        initKeypadButton(R.id.isbn_8, "8");
        initKeypadButton(R.id.isbn_9, "9");
        initKeypadButton(R.id.isbn_X, "X");

        root.findViewById(R.id.isbn_del).setOnClickListener(v -> handleDeleteButton());
        root.findViewById(R.id.btn_search).setOnClickListener(v -> {
            //noinspection ConstantConditions
            mBookSearchBaseModel.setIsbnSearchText(mIsbnView.getText().toString().trim());
            prepareSearch();
        });

        if (!mBookSearchBaseModel.getIsbnSearchText().isEmpty()) {
            // ISBN has been passed by another component, kick of a search.
            //noinspection ConstantConditions
            mIsbnView.setText(mBookSearchBaseModel.getIsbnSearchText());
            prepareSearch();
        }
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

    /**
     * Setup the 'Allow ASIN' button.
     */
    private void initAsin() {
        //noinspection ConstantConditions
        mAllowAsinCb.setOnCheckedChangeListener((v, isChecked) -> handleAsinClick(isChecked));
    }

    private void handleAsinClick(final boolean isChecked) {
        if (isChecked) {
            // over-optimisation... asin is used less then ISBN
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

    /**
     * Add an onClickListener to the individual key of the keypad.
     *
     * @param id   of key
     * @param text which the key will 'generate' upon being pressed.
     */
    private void initKeypadButton(@IdRes final int id,
                                  @NonNull final String text) {
        //noinspection ConstantConditions
        getView().findViewById(id).setOnClickListener(v -> handleIsbnKey(text));
    }

    /**
     * Handle character insertion at cursor position in EditText.
     */
    private void handleIsbnKey(@NonNull final String key) {
        @SuppressWarnings("ConstantConditions")
        int start = mIsbnView.getSelectionStart();
        int end = mIsbnView.getSelectionEnd();
        mIsbnView.getText().replace(start, end, key);
        mIsbnView.setSelection(start + 1, start + 1);
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
                mModel.setScannerStarted(false);
                //noinspection SwitchStatementWithTooFewBranches
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // Got a scan result.
                        Objects.requireNonNull(data);
                        //noinspection ConstantConditions
                        mBookSearchBaseModel.setIsbnSearchText(mScanner.getBarcode(data));
                        prepareSearch();
                        return;

                    default:
                        // Scanner Cancelled/failed.
                        // Pass the last book we got back to our caller and finish here.
                        Intent lastBookData = mBookSearchBaseModel.getLastBookData();
                        mActivity.setResult(lastBookData != null ? Activity.RESULT_OK
                                                                 : Activity.RESULT_CANCELED,
                                            lastBookData);
                        // and exit if no dialog present.
                        if (!mDisplayingAlert) {
                            mActivity.finish();
                            return;
                        }
                        break;
                }
            }

            case UniqueId.REQ_BOOK_EDIT:
                // first do the common action when the user has saved the data for the book.
                super.onActivityResult(requestCode, resultCode, data);
                if (mModel.isScanMode()) {
                    // go scan next book until the user cancels scanning.
                    startScannerActivity();
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
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
        if (mModel.isScanMode()) {
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
            if (mModel.isScanMode()) {
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
                             if (mModel.isScanMode()) {
                                 startScannerActivity();
                             }
                         });
        dialog.show();
    }

    /**
     * ISBN was invalid. Inform the user and go back to either the UI or the scanner.
     */
    private void isbnInvalid(@NonNull final String isbn,
                             final boolean allowAsin) {
        if (mModel.isScanMode()) {
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
        //noinspection ConstantConditions
        UserMessage.show(mIsbnView, getString(msg, isbn));
        if (mModel.isScanMode()) {
            // reset the now-discarded details
            mBookSearchBaseModel.clearSearchText();
            startScannerActivity();
        }
    }

    /**
     * Start scanner activity if we have a scanner.
     */
    private void startScannerActivity() {
        // sanity check.
        if (mScanner == null) {
            return;
        }

        if (!mModel.isScannerStarted()) {
            mModel.setScannerStarted(true);
            mScanner.startActivityForResult(this, UniqueId.REQ_IMAGE_FROM_SCANNER);
        }
    }
}
