package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.scanner.Scanner;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SoundManager;

import java.util.Objects;

// Try stopping the soft input keyboard to pop up at all cost when entering isbn....

//  field gets focus, up it pops
//        mIsbnView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(final View v, final boolean hasFocus) {
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
// InputMethodManager imm = (InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
// imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0, null);

// field gets focus, up it pops
// mIsbnView.setShowSoftInputOnFocus(false);
// field gets focus, up it pops
// getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
// hide on entry, field gets focus, up it pops
//  getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);


public class BookSearchByIsbnFragment extends BookSearchBaseFragment {

    private static final String BKEY_SCANNER_STARTED = "ScannerStarted";

    protected boolean scanMode = false;

    protected boolean mScannerStarted = false;
    /**
     * Options to indicate the Activity should not 'finish()' because an alert is being displayed.
     * The alert will call finish().
     */
    protected boolean mDisplayingAlert = false;

    /** The preferred (or found) scanner */
    @Nullable
    protected Scanner mScanner;
    /** */
    @Nullable
    protected EditText mIsbnView;
    @NonNull
    protected String mIsbnSearchText = "";
    @Nullable
    private CompoundButton mAllowAsinCb;

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {

        //noinspection ConstantConditions
        scanMode = BookSearchActivity.BY_SCAN.equals(getArguments().getString(BookSearchActivity.REQUEST_BKEY_BY));
        if (scanMode) {
            return null;
        }
        return inflater.inflate(R.layout.booksearch_by_isbn, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mIsbnSearchText = savedInstanceState.getString(UniqueId.KEY_BOOK_ISBN, "");
            mScannerStarted = savedInstanceState.getBoolean(BKEY_SCANNER_STARTED, false);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mIsbnSearchText = args.getString(UniqueId.KEY_BOOK_ISBN, "");
        }

        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_isbn_search);
            actionBar.setSubtitle(null);
        }

        // setup the UI if we have one.
        View root = getView();
        if (root != null) {
            initUI(root);
        } else {
            // otherwise we're scanning
            scanMode = true;

            mScanner = ScannerManager.getScanner(mActivity);
            if (mScanner == null) {
                ScannerManager.promptForScannerInstallAndFinish(mActivity, true);
                // Prevent our activity to finish.
                mDisplayingAlert = true;
            } else {
                // we have a scanner, but first check if we already have an isbn from somewhere
                if (!mIsbnSearchText.isEmpty()) {
                    prepareSearch();
                } else {
                    // let's scan....
                    try {
                        startScannerActivity();
                    } catch (Exception e) {
                        // we had a scanner setup, but something went wrong starting it.
                        ScannerManager.promptForScannerInstallAndFinish(mActivity, false);
                        // Prevent our activity to finish.
                        mDisplayingAlert = true;
                    }
                }
            }
        }
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Setup the UI e.g. the keypad etc....
     */
    private void initUI(final View root) {
        mIsbnView = root.findViewById(R.id.isbn);
        mAllowAsinCb = root.findViewById(R.id.allow_asin);
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

        root.findViewById(R.id.isbn_del).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    //noinspection ConstantConditions
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
                } catch (StringIndexOutOfBoundsException ignore) {
                    //do nothing - empty string
                }
            }
        });

        root.findViewById(R.id.search).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                //noinspection ConstantConditions
                mIsbnSearchText = mIsbnView.getText().toString().trim();
                prepareSearch();
            }
        });

        if (!mIsbnSearchText.isEmpty()) {
            // ISBN has been passed by another component, kick of a search.
            //noinspection ConstantConditions
            mIsbnView.setText(mIsbnSearchText);
            prepareSearch();
        }
    }

    /**
     * Setup the 'Allow ASIN' button
     */
    private void initAsin() {
        //noinspection ConstantConditions
        mAllowAsinCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    //noinspection ConstantConditions
                    mIsbnView.setKeyListener(DigitsKeyListener.getInstance("1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"));
                    mIsbnView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                } else {
                    mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                    //noinspection ConstantConditions
                    mIsbnView.setKeyListener(DigitsKeyListener.getInstance("0123456789xX"));
                    mIsbnView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                    String txt = mIsbnView.getText().toString(); // leave xX
                    mIsbnView.setText(txt.replaceAll("[qwertyuiopasdfghjklzcvbnmQWERTYUIOPASDFGHJKLZCVBNM]", ""));
                }
            }
        });
    }

    /**
     * Add an onClickListener to the individual key of the keypad.
     *
     * @param id   of key
     * @param text which the key will 'generate' upon being pressed.
     */
    private void initKeypadButton(final @IdRes int id, final @NonNull String text) {
        //noinspection ConstantConditions
        getView().findViewById(id).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                handleIsbnKey(text);
            }
        });
    }

    /**
     * Handle character insertion at cursor position in EditText
     */
    private void handleIsbnKey(final @NonNull String key) {
        //noinspection ConstantConditions
        int start = mIsbnView.getSelectionStart();
        int end = mIsbnView.getSelectionEnd();
        mIsbnView.getText().replace(start, end, key);
        mIsbnView.setSelection(start + 1, start + 1);
    }

    /**
     * Search with ISBN
     */
    protected void prepareSearch() {
        // sanity check
        if (mIsbnSearchText.isEmpty()) {
            return;
        }

        // intercept UPC numbers
        mIsbnSearchText = IsbnUtils.upc2isbn(mIsbnSearchText);
        if (mIsbnSearchText.isEmpty()) {
            return;
        }

        // If the layout has an 'Allow ASIN' checkbox, see if it is checked.
        final boolean allowAsin = mAllowAsinCb != null && mAllowAsinCb.isChecked();

        // not a valid ISBN/ASIN ?
        if (!IsbnUtils.isValid(mIsbnSearchText) && (!allowAsin || !IsbnUtils.isValidAsin(mIsbnSearchText))) {
            if (scanMode) {
                // Optionally beep if scan failed.
                SoundManager.beepLow(mActivity);
            }
            int msg;
            if (allowAsin) {
                msg = R.string.warning_x_is_not_a_valid_isbn_or_asin;
            } else {
                msg = R.string.warning_x_is_not_a_valid_isbn;
            }
            StandardDialogs.showUserMessage(mActivity, getString(msg, mIsbnSearchText));
            if (scanMode) {
                // reset the now-discarded details
                mIsbnSearchText = "";
                startScannerActivity();
            }
            return;
        }
        // at this point, we have a valid isbn/asin.
        if (scanMode) {
            // Optionally beep if scan was valid.
            SoundManager.beepHigh(mActivity);
        }

        // See if ISBN already exists in our database, if not then start the search and get details.
        final long existingId = mDb.getIdFromIsbn(mIsbnSearchText, true);
        if (existingId == 0) {
            startSearch();
        } else {
            isbnAlreadyPresent(existingId);
        }
    }

    /**
     * the ISBN was already present, Verify what the user wants - this can be a dangerous operation
     *
     * @param existingId of the book we already have in the database
     */
    private void isbnAlreadyPresent(final long existingId) {
        AlertDialog dialog = new AlertDialog.Builder(mActivity)
                .setMessage(R.string.warning_duplicate_book_message)
                .setTitle(R.string.title_duplicate_book)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        // User wants to add regardless, doSearch get details and eventually doSearch to edit
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.btn_confirm_add),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        startSearch();
                    }
                });

        // User wants to review the existing book, doSearch to edit
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.menu_edit_book),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        EditBookActivity.startActivityForResult(BookSearchByIsbnFragment.this, /* 9e2c0b04-8217-4b49-9937-96d160104265 */
                                existingId, EditBookFragment.TAB_EDIT);
                    }
                });

        // User aborts this isbn
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        // reset the now-discarded details
                        mIsbnSearchText = "";
                        if (scanMode) {
                            startScannerActivity();
                        }
                    }
                });
        dialog.show();
    }

    /**
     * Start the actual search with the {@link SearchManager} in the background.
     * Or restart the scanner if applicable.
     *
     * The results will arrive in {@link #onSearchFinished}
     */
    private void startSearch() {
        // check if we have an active search, if so, quit.
        if (mSearchManagerId != 0) {
            return;
        }

        //sanity check
        if (!mIsbnSearchText.isEmpty()) {
            // we have an isbn, kick of search
            if (super.startSearch("", "", mIsbnSearchText)) {
                // reset the details so we don't restart the search unnecessarily
                mIsbnSearchText = "";
            }
        } else if (scanMode) {
            // we don't have an isbn, but we're scanning. Restart scanner.
            startScannerActivity();
        }
        //ENHANCE else: the user will surely realise he cannot search without entering something?
    }

    /**
     * results of search started by {@link #startSearch}
     *
     * The details will get sent to {@link EditBookActivity}
     */
    @SuppressWarnings("SameReturnValue")
    public boolean onSearchFinished(final boolean wasCancelled, final @NonNull Bundle bookData) {
        Tracker.handleEvent(this, Tracker.States.Running, "onSearchFinished|SearchManagerId=" + mSearchManagerId);
        try {
            if (!wasCancelled) {
                mActivity.getTaskManager().sendHeaderTaskProgressMessage(getString(R.string.progress_msg_adding_book));
                Intent intent = new Intent(mActivity, EditBookActivity.class);
                intent.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* 341ace23-c2c8-42d6-a71e-909a3a19ba99 */

                // Clear the data entry fields ready for the next one (scanMode has no view)
                if (mIsbnView != null) {
                    mIsbnView.setText("");
                }
            } else {
                if (scanMode) {
                    startScannerActivity();
                }
            }
            return true;
        } finally {
            // Clean up
            mSearchManagerId = 0;
            // Make sure the base message will be empty.
            mActivity.getTaskManager().sendHeaderTaskProgressMessage(null);
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

        if (!mScannerStarted) {
            mScannerStarted = true;
            mScanner.startActivityForResult(mActivity, Scanner.REQUEST_CODE); /* 4f410d34-dc9c-4ee2-903e-79d69a328517, c2c28575-5327-40c6-827a-c7973bd24d12 */
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this, outState);

        outState.putString(UniqueId.KEY_BOOK_ISBN, mIsbnSearchText);
        outState.putBoolean(BKEY_SCANNER_STARTED, mScannerStarted);

        super.onSaveInstanceState(outState);
        Tracker.exitOnSaveInstanceState(this, outState);
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            case Scanner.REQUEST_CODE: {/* 4f410d34-dc9c-4ee2-903e-79d69a328517, c2c28575-5327-40c6-827a-c7973bd24d12*/
                mScannerStarted = false;
                switch (resultCode) {
                    case Activity.RESULT_OK: {
                        Objects.requireNonNull(data);
                        //noinspection ConstantConditions
                        mIsbnSearchText = mScanner.getBarcode(data);
                        prepareSearch();
                        break;
                    }
                    default: {
                        // Scanner Cancelled/failed. Pass the last book we got to our caller and finish here.
                        mActivity.setResult(mLastBookData != null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, mLastBookData);
                        // and exit if no dialog present.
                        if (!mDisplayingAlert) {
                            mActivity.finish();
                        }
                        break;
                    }
                }
                // go scan next book until the user cancels scanning.
                startScannerActivity();
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }
}
