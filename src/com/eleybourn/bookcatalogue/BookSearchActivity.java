/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.scanner.Scanner;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.AsinUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This class will search the internet for book details based on either
 * a manually provided ISBN, or a scanned ISBN.
 * Alternatively, it will search based on Author/Title.
 *
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 *
 * ASIN stands for Amazon Standard Identification Number.
 * Every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN-10 number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class BookSearchActivity extends BaseActivityWithTasks implements SearchManager.SearchListener {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK;

    public static final int RESULT_CHANGES_MADE = UniqueId.ACTIVITY_RESULT_CHANGES_MADE_ADD_BOOK_BY_SEARCH;

    public static final String REQUEST_BKEY_BY = "by";
    public static final String BY_ISBN = "isbn";
    public static final String BY_TEXT = "text";
    public static final String BY_SCAN = "scan";

    /** optionally limit the sites to search on. By default uses {@link SearchSites#SEARCH_ALL} */
    private static final String REQUEST_BKEY_SEARCH_SITES = "SearchSites";
    /** */
    private static final String SEARCH_MANAGER_ID = "SearchManagerId";
    private static final String SCANNER_STARTED = "ScannerStarted";
    private static final String LAST_BOOK_INTENT = "LastBookIntent";
    /** A list of author names we have already searched for in this session */
    private final ArrayList<String> mAuthorNames = new ArrayList<>();
    private boolean mScannerStarted = false;
    private EditText mIsbnText;
    private EditText mTitleText;
    private AutoCompleteTextView mAuthorText;
    private ArrayAdapter<String> mAuthorAdapter = null;
    private CatalogueDBAdapter mDb;
    private String mAuthor;
    private String mTitle;
    private String mIsbn;

    /**
     * Mode this activity is in:
     * false = data entry, when done exit Activity
     * true = data from scanner. Loop repeatedly starting the scanner until cancelled.
     */
    private boolean mLoopMode = false;

    /**
     * Options to indicate the Activity should not 'finish()' because an alert is being displayed.
     * The alert will call {@link #finish()}.
     */
    private boolean mDisplayingAlert = false;

    /** Object to manage preferred (or found) scanner */
    private Scanner mScanner = null;

    /** The last Intent returned as a result of creating a book. */
    @Nullable
    private Intent mLastBookData = null;

    /** sites to search on. Can be overridden by the user (option menu) */
    private int mSearchSites = SearchSites.SEARCH_ALL;

    /** Objects managing current search. */
    private long mSearchManagerId = 0;

    private String mBy;

    /**
     * Return the layout to use for this subclass
     */
    @Override
    public int getLayoutId() {
        if (mIsbn == null) {
            switch (mBy) {
                case BY_ISBN:
                    return R.layout.booksearch_by_isbn;

                case BY_TEXT:
                    return R.layout.booksearch_by_name;

                case BY_SCAN:
                    return R.layout.booksearch_by_scan;
            }
        }
        return R.layout.booksearch_by_isbn;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            boolean network_available = Utils.isNetworkAvailable(this);
            if (!network_available) {
                StandardDialogs.showUserMessage(this, R.string.error_no_internet_connection);
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            }

            // Must do this before super.onCreate as getLayoutId() needs them
            Bundle extras = getIntent().getExtras();
            Objects.requireNonNull(extras);
            mIsbn = extras.getString(UniqueId.KEY_BOOK_ISBN);
            mBy = extras.getString(REQUEST_BKEY_BY);

            super.onCreate(savedInstanceState);
            this.setTitle(R.string.title_isbn_search);

            mSearchSites = extras.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.SEARCH_ALL);

            LibraryThingManager.showLtAlertIfNecessary(this, false, "search");

            mDb = new CatalogueDBAdapter(this)
                    .open();

            if (savedInstanceState != null) {
                // the search at the moment the activity went to sleep
                mSearchManagerId = savedInstanceState.getLong(SEARCH_MANAGER_ID);

                if (savedInstanceState.containsKey(SCANNER_STARTED)) {
                    mScannerStarted = savedInstanceState.getBoolean(SCANNER_STARTED);
                }
            }

            /* BUG NOTE 1:
             *
             * There is a bizarre bug that seems to only affect some users in which this activity
             * is called AFTER the user has finished and the passed Intent has neither a ISBN nor a
             * "REQUEST_BKEY_BY" in the Extras. Following all the code that starts this activity suggests that
             * the activity is ALWAYS started with the intent data. The problems always occur AFTER
             * adding a book, which confirms that the activity has been started correctly.
             *
             * In order to avoid this problem, we just check for nulls and finish().
             * THIS IS NOT A FIX it is a MESSY WORK-AROUND.
             *
             * TODO: Find out why BookSearchActivity gets restarted with no data
             *
             * So...we save the extras in savedInstanceState, and look for it when missing
             */
            if (mIsbn == null && (mBy == null || mBy.isEmpty())) {
                Logger.error("Empty args for BookSearchActivity");
                if (savedInstanceState != null) {
                    if (mIsbn == null && savedInstanceState.containsKey(UniqueId.KEY_BOOK_ISBN)) {
                        mIsbn = savedInstanceState.getString(UniqueId.KEY_BOOK_ISBN);
                    }
                    if (savedInstanceState.containsKey(REQUEST_BKEY_BY)) {
                        mBy = savedInstanceState.getString(REQUEST_BKEY_BY);
                    }
                }
                // If they are still null, we can't proceed.
                if (mIsbn == null && (mBy == null || mBy.isEmpty())) {
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
            }

            // Default to MANUAL
            mLoopMode = false;

            if (mIsbn != null) {
                onCreateWithISBN();
            } else if (BY_ISBN.equals(mBy)) {
                onCreateByISBN();
            } else if (BY_TEXT.equals(mBy)) {
                onCreateByName();
            } else if (BY_SCAN.equals(mBy)) {
                onCreateByScan(savedInstanceState);
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @return super.onCreateOptionsMenu(menu);
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(final @NonNull Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.tab_lbl_search_sites)
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(this, SearchAdminActivity.class);
                intent.putExtra(SearchAdminActivity.REQUEST_BKEY_TAB, SearchAdminActivity.TAB_SEARCH_COVER_ORDER);
                startActivityForResult(intent, SearchAdminActivity.REQUEST_CODE); /* 1b923299-d966-4ed5-8230-c5a7c491053b */
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * ISBN has been passed by another component
     */
    private void onCreateWithISBN() {
        mIsbnText = findViewById(R.id.isbn);
        mIsbnText.setText(mIsbn);
        go(mIsbn, "", "");
    }

    /**
     * present keypad to enter an ISBN
     */
    private void onCreateByISBN() {
        mIsbnText = findViewById(R.id.isbn);

        // Try stopping the soft input keyboard to pop up at all cost when entering isbn....

        // doesn't work, as soon as field gets focus, up it pops
//        InputMethodManager imm = (InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
//        imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0, null);

        // doesn't work, as soon as field gets focus, up it pops
//        mIsbnText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(final View v, final boolean hasFocus) {
//                if (v.equals(mIsbnText)) {
//                    InputMethodManager imm = (InputMethodManager)
//                            getSystemService(Context.INPUT_METHOD_SERVICE);
//                    imm.showSoftInput(mIsbnText, InputMethodManager.HIDE_IMPLICIT_ONLY);
//                }
//            }
//        });

        // doesn't work, as soon as field gets focus, up it pops
//        mIsbnText.setShowSoftInputOnFocus(false);
        // works but prevents the user from select/copy/past
//        mIsbnText.setInputType(InputType.TYPE_NULL);
//        mIsbnText.setTextIsSelectable(true);
//        mIsbnText.setCursorVisible(true); // no effect
        // doesn't work, as soon as field gets focus, up it pops
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // hide on entry, but it will still pop up when focused
//        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);


        // Setup the 'Allow ASIN' button
        final CompoundButton allowAsinCb = this.findViewById(R.id.allow_asin);
        allowAsinCb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    mIsbnText.setKeyListener(DigitsKeyListener.getInstance("1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"));
                    mIsbnText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                } else {
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                    mIsbnText.setKeyListener(DigitsKeyListener.getInstance("0123456789xX"));
                    mIsbnText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                    String txt = mIsbnText.getText().toString(); // leave xX
                    mIsbnText.setText(txt.replaceAll("[qwertyuiopasdfghjklzcvbnmQWERTYUIOPASDFGHJKLZCVBNM]", ""));
                }
            }
        });

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

        findViewById(R.id.isbn_del).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    int start = mIsbnText.getSelectionStart();
                    int end = mIsbnText.getSelectionEnd();
                    if (start < end) {
                        // We have a selection. Delete it.
                        mIsbnText.getText().replace(start, end, "");
                        mIsbnText.setSelection(start, start);
                    } else {
                        // Delete char before cursor
                        if (start > 0) {
                            mIsbnText.getText().replace(start - 1, start, "");
                            mIsbnText.setSelection(start - 1, start - 1);
                        }
                    }
                } catch (StringIndexOutOfBoundsException ignore) {
                    //do nothing - empty string
                }
            }
        });

        findViewById(R.id.search).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String mIsbn = mIsbnText.getText().toString().trim();
                go(mIsbn, "", "");
            }
        });
    }

    private void initKeypadButton(final @IdRes int id, final @NonNull String text) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                handleIsbnKey(text);
            }
        });
    }

    /**
     * Handle character insertion at cursor position in EditText
     */
    private void handleIsbnKey(final @NonNull String key) {
        int start = mIsbnText.getSelectionStart();
        int end = mIsbnText.getSelectionEnd();
        mIsbnText.getText().replace(start, end, key);
        mIsbnText.setSelection(start + 1, start + 1);
    }

    private void onCreateByScan(final @Nullable Bundle savedInstanceState) {
        mLoopMode = true;
        mIsbnText = findViewById(R.id.isbn);

        /*
         * Use the preferred barcode scanner to search for a isbn
         * Prompt users to install the application if they do not have it installed.
         */
        try {
            // Start the scanner IF this is a real 'first time' call.
            if (savedInstanceState == null) {
                startScannerActivity();
            } else {
                // It's a saved state, so see if we have an ISBN
                String isbn = savedInstanceState.getString(UniqueId.KEY_BOOK_ISBN);
                if (isbn != null && !isbn.isEmpty()) {
                    go(isbn, "", "");
                }
            }
        } catch (SecurityException e) {
            AlertDialog dialog = new AlertDialog.Builder(BookSearchActivity.this)
                    .setMessage(R.string.warning_bad_scanner)
                    .setTitle(R.string.title_install_scan)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .create();

            dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                    /* text hardcoded as a it is a product name */
                    "ZXing",
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.zxing.client.android"));
                            startActivity(marketIntent);
                            setResult(Activity.RESULT_CANCELED);
                            finish();
                        }
                    });
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            //do nothing
                            setResult(Activity.RESULT_CANCELED);
                            finish();
                        }
                    });

            // Prevent the activity result from closing this activity.
            mDisplayingAlert = true;
            dialog.show();
        } catch (ActivityNotFoundException e) {
            // Verify - this can be a dangerous operation
            // -> yes, it threw another ActivityNotFoundException if Cancel is used
            // so now enclosed in another try.
            try {
                AlertDialog dialog = new AlertDialog.Builder(BookSearchActivity.this)
                        .setMessage(R.string.install_scan)
                        .setTitle(R.string.title_install_scan)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .create();

                dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                        /* text hardcoded as a it is a product name */
                        "pic2shop",
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=com.visionsmarts.pic2shop"));
                                startActivity(marketIntent);
                                setResult(Activity.RESULT_CANCELED);
                                finish();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL,
                        /* text hardcoded as a it is a product name */
                        "ZXing",
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                Intent marketIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=com.google.zxing.client.android"));
                                startActivity(marketIntent);
                                setResult(Activity.RESULT_CANCELED);
                                finish();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                //do nothing
                                setResult(Activity.RESULT_CANCELED);
                                finish();
                            }
                        });
                // Prevent the activity result from closing this activity.
                mDisplayingAlert = true;
                dialog.show();
            } catch (ActivityNotFoundException ignore) {
                // give up....
            }
        }
    }

    private void onCreateByName() {
        setTitle(R.string.search_hint);
        initAuthorList();

        mTitleText = findViewById(R.id.title);

        findViewById(R.id.search).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String author = mAuthorText.getText().toString().trim();
                String title = mTitleText.getText().toString().trim();

                if (mAuthorAdapter.getPosition(author) < 0) {
                    // Based on code from filipeximenes we also need to update the adapter here in
                    // case no author or book is added, but we still want to see 'recent' entries.
                    if (!author.isEmpty()) {
                        boolean found = false;
                        for (String s : mAuthorNames) {
                            if (s.equalsIgnoreCase(author)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            // Keep a list of names as typed to use when we recreate list
                            mAuthorNames.add(author);
                            // Add to adapter, in case search produces no results
                            mAuthorAdapter.add(author);
                        }
                    }
                }
                go("", author, title);
            }
        });
    }

    /**
     * Clear any data-entry fields that have been set.
     * Used when a book has been successfully added as we want to get ready for another.
     */
    private void clearFields() {
        if (mIsbnText != null) {
            mIsbnText.setText("");
        }
        if (mAuthorText != null) {
            mAuthorText.setText("");
        }
        if (mTitleText != null) {
            mTitleText.setText("");
        }
    }

    /**
     * This function searches the internet to extract the details of the book.
     * The details will then get sent to {@link EditBookActivity}
     *
     * Either the isbn or the author/title needs to be specified.
     */
    private void go(final @NonNull String isbn, final @NonNull String author, final @NonNull String title) {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, " go: isbn=" + isbn + ", author=" + author + ", title=" + title);
        }

        if (isbn.isEmpty() && author.isEmpty() && title.isEmpty()) {
            return;
        }

        // Save the details because we will do some async processing or an alert
        mAuthor = author;
        mTitle = title;
        // intercept UPC numbers
        mIsbn = IsbnUtils.upc2isbn(isbn);

        try {
            if (!mIsbn.isEmpty()) {
                // If the layout has an 'Allow ASIN' checkbox, see if it is checked.
                final Checkable allowAsinCb = findViewById(R.id.allow_asin);
                final boolean allowAsin = allowAsinCb != null && allowAsinCb.isChecked();

                if (!IsbnUtils.isValid(mIsbn) && (!allowAsin || !AsinUtils.isValid(mIsbn))) {
                    int msg;
                    if (allowAsin) {
                        msg = R.string.warning_x_is_not_a_valid_isbn_or_asin;
                    } else {
                        msg = R.string.warning_x_is_not_a_valid_isbn;
                    }
                    StandardDialogs.showUserMessage(this, getString(msg, mIsbn));
                    if (mLoopMode) {
                        // Optionally beep if scan failed.
                        SoundManager.beepLow();
                        // reset the now-discarded details
                        mIsbn = "";
                        mAuthor = "";
                        mTitle = "";
                        startScannerActivity();
                    }
                    return;
                } else {
                    if (mLoopMode) {
                        // Optionally beep if scan was valid.
                        SoundManager.beepHigh();
                    }
                    // See if ISBN exists in our database
                    final long existingId = mDb.getIdFromIsbn(mIsbn, true);
                    if (existingId > 0) {
                        // Verify - this can be a dangerous operation
                        AlertDialog dialog = new AlertDialog.Builder(this)
                                .setMessage(R.string.warning_duplicate_book_message)
                                .setTitle(R.string.title_duplicate_book)
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .create();

                        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.btn_confirm_add),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        doSearchBook();
                                    }
                                });
                        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.menu_edit_book),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        EditBookActivity.startActivityForResult(BookSearchActivity.this, /* 9e2c0b04-8217-4b49-9937-96d160104265 */
                                                existingId, EditBookFragment.TAB_EDIT);
                                    }
                                });
                        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        //do nothing
                                        if (mLoopMode) {
                                            // reset the now-discarded details
                                            mIsbn = "";
                                            mAuthor = "";
                                            mTitle = "";
                                            startScannerActivity();
                                        }
                                    }
                                });
                        dialog.show();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(e);
        }

        if (mSearchManagerId == 0) {
            doSearchBook();
        }
    }

    /**
     * Start the actual search with the {@link SearchManager} in the background.
     * The results will arrive in {@link #onSearchFinished(Bundle, boolean)}
     */
    private void doSearchBook() {
        // Delete any hanging around temporary thumbs
        StorageUtils.deleteFile(StorageUtils.getTempCoverFile());

        // need at least one of the three. Normally none of these will be null ... paranoia strikes again...
        if ((mAuthor != null && !mAuthor.isEmpty()) || (mTitle != null && !mTitle.isEmpty()) || (mIsbn != null && !mIsbn.isEmpty())) {
            /* Get the book */
            try {
                // Start the lookup in background.
                final SearchManager searchManager = new SearchManager(getTaskManager(), this);
                mSearchManagerId = searchManager.getSenderId();
                Tracker.handleEvent(this, "Searching " + mSearchManagerId, Tracker.States.Running);
                if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                    Logger.info(this, "doSearchBook, starting search with mSearchManagerId: " + mSearchManagerId);
                }

                getTaskManager().doProgress(getString(R.string.progress_msg_searching));
                searchManager.search(mSearchSites, mAuthor, mTitle, mIsbn, true);
                // reset the details so we don't restart the search unnecessarily
                mAuthor = "";
                mTitle = "";
                mIsbn = "";
            } catch (Exception e) {
                Logger.error(e);
                StandardDialogs.showUserMessage(this, R.string.error_search_failed);
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        } else {
            if (mLoopMode) {
                startScannerActivity();
            }
        }
    }

    /**
     * doSearchBook results.
     */
    @SuppressWarnings("SameReturnValue")
    public boolean onSearchFinished(final @NonNull Bundle bookData, final boolean cancelled) {
        Tracker.handleEvent(this, "onSearchFinished" + mSearchManagerId, Tracker.States.Running);
        try {
            if (cancelled) {
                if (mLoopMode) {
                    startScannerActivity();
                }
            } else {
                getTaskManager().doProgress(getString(R.string.progress_msg_adding_book));
                Intent intent = new Intent(this, EditBookActivity.class);
                intent.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
                startActivityForResult(intent, EditBookActivity.REQUEST_CODE); /* 341ace23-c2c8-42d6-a71e-909a3a19ba99 */
                // Clear the data entry fields ready for the next one
                clearFields();
            }
            return true;
        } finally {
            // Clean up
            mSearchManagerId = 0;
            // Make sure the base message will be empty.
            getTaskManager().doProgress(null);
        }
    }

    @Override
    @CallSuper
    protected void onPause() {
        Tracker.enterOnPause(this);
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().removeListener(mSearchManagerId, this);
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().addListener(mSearchManagerId, this, true);
        }
        Tracker.exitOnResume(this);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case Scanner.REQUEST_CODE: {/* 4f410d34-dc9c-4ee2-903e-79d69a328517, c2c28575-5327-40c6-827a-c7973bd24d12*/
                mScannerStarted = false;
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    String isbn = mScanner.getBarcode(data);
                    mIsbnText.setText(isbn);
                    go(isbn, "", "");
                } else {
                    // Scanner Cancelled/failed. Pass the last book we got to our caller and finish here.
                    setResult(mLastBookData != null ? RESULT_CHANGES_MADE : Activity.RESULT_CANCELED, mLastBookData);
                    // and exit if no dialog present.
                    if (!mDisplayingAlert) {
                        finish();
                        return;
                    }
                }

                initAuthorList();
                return;
            }
            case EditBookActivity.REQUEST_CODE: {/* 341ace23-c2c8-42d6-a71e-909a3a19ba99, 9e2c0b04-8217-4b49-9937-96d160104265 */
                if (resultCode == EditBookActivity.RESULT_CHANGES_MADE) {
                    // Created a book; save the intent
                    mLastBookData = data;
                    // and set that as the default result
                    setResult(mLastBookData != null ? RESULT_CHANGES_MADE : Activity.RESULT_CANCELED, mLastBookData);
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // if the edit was cancelled, set that as the default result code
                    setResult(Activity.RESULT_CANCELED);
                }

                // restart scanner if necessary.
                if (mLoopMode) {
                    startScannerActivity();
                }

                initAuthorList();
                return;
            }
            case SearchAdminActivity.REQUEST_CODE: { /* 1b923299-d966-4ed5-8230-c5a7c491053b */
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES, mSearchSites);
                }
                return;
            }
        }

        // No matter what the activity was, rebuild the author list in case a new author was added.
        //initAuthorList();

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void setActivityResult() {
        setResult(changesMade() ? RESULT_CHANGES_MADE : Activity.RESULT_CANCELED);
    }

    private void initAuthorList() {
        // Get the author field, if present
        mAuthorText = findViewById(R.id.author);
        if (mAuthorText != null) {
            // Get all known authors and build a Set of the names
            final ArrayList<String> authors = mDb.getAuthorsFormattedName();
            final Set<String> uniqueNames = new HashSet<>();
            for (String s : authors) {
                uniqueNames.add(s.toUpperCase());
            }

            // Add the names the user has already tried (to handle errors and mistakes)
            for (String s : mAuthorNames) {
                if (!uniqueNames.contains(s.toUpperCase())) {
                    authors.add(s);
                }
            }

            // Now get an adapter based on the combined names
            mAuthorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, authors);
            mAuthorText.setAdapter(mAuthorAdapter);
        }
    }

    /**
     * Start scanner activity.
     */
    private void startScannerActivity() {
        if (mScanner == null) {
            mScanner = ScannerManager.getScanner();
        }
        if (!mScannerStarted) {
            mScannerStarted = true;
            mScanner.startActivityForResult(this, Scanner.REQUEST_CODE); /* 4f410d34-dc9c-4ee2-903e-79d69a328517, c2c28575-5327-40c6-827a-c7973bd24d12 */
        }
    }

    /**
     * This method is called after {@link #onStart} when the activity is
     * being re-initialized from a previously saved state, given here in
     * <var>savedInstanceState</var>.  Most implementations will simply use {@link #onCreate}
     * to restore their state, but it is sometimes convenient to do it here
     * after all of the initialization has been done or to allow subclasses to
     * decide whether to use your default implementation.  The default
     * implementation of this method performs a restore of any view state that
     * had previously been frozen by {@link #onSaveInstanceState}.
     *
     * <p>This method is called between {@link #onStart} and {@link #onPostCreate}.
     *
     * @param savedInstanceState the data most recently supplied in {@link #onSaveInstanceState}.
     *
     * @see #onCreate
     * @see #onPostCreate
     * @see #onResume
     * @see #onSaveInstanceState
     *
     *
     *
     * Ensure the TaskManager is restored.
     */
    @Override
    @CallSuper
    protected void onRestoreInstanceState(final @NonNull Bundle savedInstanceState) {

        mSearchManagerId = savedInstanceState.getLong(SEARCH_MANAGER_ID);

        // Now do 'standard' stuff
        mLastBookData = savedInstanceState.getParcelable(LAST_BOOK_INTENT);

        // Call the super method only after we have the searchManager set up
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        // Saving intent data is a kludge due to an apparent Android bug in some
        // handsets. Search for "BUG NOTE 1" in this source file for a discussion
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(UniqueId.KEY_BOOK_ISBN)) {
                outState.putString(UniqueId.KEY_BOOK_ISBN, extras.getString(UniqueId.KEY_BOOK_ISBN));
            }
            if (extras.containsKey(REQUEST_BKEY_BY)) {
                outState.putString(REQUEST_BKEY_BY, extras.getString(REQUEST_BKEY_BY));
            }
        }

        // standard stuff we need
        if (mSearchManagerId != 0) {
            outState.putLong(SEARCH_MANAGER_ID, mSearchManagerId);
        }
        outState.putParcelable(LAST_BOOK_INTENT, mLastBookData);
        outState.putBoolean(SCANNER_STARTED, mScannerStarted);

        // Save the current search details as this may be called as a result of a rotate during an alert dialog.
        // note: these don't actually are getting read ? TODO: make 100% sure, then delete
        outState.putString(UniqueId.KEY_AUTHOR_NAME, mAuthor);
        outState.putString(UniqueId.KEY_BOOK_ISBN, mIsbn);
        outState.putString(UniqueId.KEY_TITLE, mTitle);

        super.onSaveInstanceState(outState);
    }
}
