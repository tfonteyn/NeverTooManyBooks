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

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;

import com.eleybourn.bookcatalogue.baseactivity.ActivityWithTasks;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.scanner.Scanner;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.AsinUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SoundManager;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * This class will search the internet for
 * book details based on either a typed in or scanned ISBN.
 *
 * ISBN stands for International Standard Book Number.
 * Every book is assigned a unique ISBN-10 and ISBN-13 when published.
 *
 * ASIN stands for Amazon Standard Identification Number.
 * Almost every product on Amazon has its own ASIN, a unique code used to identify it.
 * For books, the ASIN is the same as the ISBN number, but for all other products a new ASIN
 * is created when the item is uploaded to their catalogue.
 */
public class BookISBNSearchActivity extends ActivityWithTasks {
    public static final String BKEY_BY = "by";
    public static final String BY_ISBN = "isbn";
    public static final String BY_NAME = "name";
    public static final String BY_SCAN = "scan";

    private static final String SEARCH_MANAGER_ID = "SearchManagerId";
    private static final String SCANNER_STARTED = "mScannerStarted";
    private static final String LAST_BOOK_INTENT = "LastBookIntent";

    /**
     * Mode this activity is in:
     * MANUAL = data entry
     * SCAN = data from scanner.
     * For SCAN, it loops repeatedly starting the scanner.
     */
    private static final int MODE_MANUAL = 1;
    private static final int MODE_SCAN = 2;


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
    private int mMode;

    /**
     * Options to indicate the Activity should not 'finish()' because an alert is being displayed.
     * The alert will call finish().
     */
    private boolean mDisplayingAlert = false;
    /** Object to manage preferred (or found) scanner */
    private Scanner mScanner = null;
    /** The last Intent returned as a result of creating a book. */
    private Intent mLastBookIntent = null;
    /** Object managing current search. */
    private long mSearchManagerId = 0;
    private final SearchManager.SearchListener mSearchHandler = new SearchManager.SearchListener() {
        @Override
        public boolean onSearchFinished(@NonNull final Bundle bookData, final boolean cancelled) {
            return BookISBNSearchActivity.this.onSearchFinished(bookData, cancelled);
        }
    };

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
                case BY_NAME:
                    return R.layout.booksearch_by_name;
                case BY_SCAN:
                    return R.layout.booksearch_by_scan;
            }
        }
        return R.layout.booksearch_by_isbn;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        try {
            boolean network_available = Utils.isNetworkAvailable(this);
            if (!network_available) {
                StandardDialogs.showQuickNotice(this, R.string.no_connection);
                finish();
                return;
            }

            // Must do this before super.onCreate as getLayoutId() needs them
            Bundle extras = getIntent().getExtras();
            mIsbn = extras.getString(UniqueId.KEY_ISBN);
            mBy = extras.getString(BKEY_BY);

            super.onCreate(savedInstanceState);
            this.setTitle(R.string.title_isbn_search);

            LibraryThingManager.showLtAlertIfNecessary(this, false, "search");

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            if (savedInstanceState != null) {
                mSearchManagerId = savedInstanceState.getLong(SEARCH_MANAGER_ID);

                if (savedInstanceState.containsKey(SCANNER_STARTED)) {
                    mScannerStarted = savedInstanceState.getBoolean(SCANNER_STARTED);
                }
            }

            /* BUG NOTE 1:
             *
             * There is a bizarre bug that seems to only affect some users in which this activity
             * is called AFTER the user has finished and the passed Intent has neither a ISBN nor a
             * "BKEY_BY" in the Extras. Following all the code that starts this activity suggests that
             * the activity is ALWAYS started with the intent data. The problems always occur AFTER
             * adding a book, which confirms that the activity has been started correctly.
             *
             * In order to avoid this problem, we just check for nulls and finish().
             * THIS IS NOT A FIX it is a MESSY WORK-AROUND.
             *
             * TODO: Find out why BookISBNSearchActivity gets restarted with no data
             *
             * So...we save the extras in savedInstanceState, and look for it when missing
             */
            if (mIsbn == null && (mBy == null || mBy.isEmpty())) {
                Logger.error("Empty args for BookISBNSearchActivity");
                if (savedInstanceState != null) {
                    if (mIsbn == null && savedInstanceState.containsKey(UniqueId.KEY_ISBN)) {
                        mIsbn = savedInstanceState.getString(UniqueId.KEY_ISBN);
                    }
                    if (savedInstanceState.containsKey(BKEY_BY)) {
                        mBy = savedInstanceState.getString(BKEY_BY);
                    }
                }
                // If they are still null, we can't proceed.
                if (mIsbn == null && (mBy == null || mBy.isEmpty())) {
                    finish();
                    return;
                }
            }

            // Default to MANUAL
            mMode = MODE_MANUAL;

            if (mIsbn != null) {
                onCreateWithISBN();
            } else if (BY_ISBN.equals(mBy)) {
                onCreateByISBN();
            } else if (BY_NAME.equals(mBy)) {
                onCreateByName();
            } else if (BY_SCAN.equals(mBy)) {
                onCreateByScan(savedInstanceState);
            }
        } finally {
            Tracker.exitOnCreate(this);
        }
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
        final CheckBox allowAsinCb = this.findViewById(R.id.asinCheckbox);
        allowAsinCb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                    mIsbnText.setKeyListener(DigitsKeyListener.getInstance("1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM"));
                    mIsbnText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                } else {
                    getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
                    mIsbnText.setKeyListener(DigitsKeyListener.getInstance("0123456789xX"));
                    mIsbnText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

                    String txt = mIsbnText.getText().toString(); // leave xX
                    mIsbnText.setText(txt.replaceAll("[qwertyuiopasdfghjklzcvbnmQWERTYUIOPASDFGHJKLZCVBNM]", ""));
                }
            }
        });

        setupNumberButton(R.id.isbn_0, "0");
        setupNumberButton(R.id.isbn_1, "1");
        setupNumberButton(R.id.isbn_2, "2");
        setupNumberButton(R.id.isbn_3, "3");
        setupNumberButton(R.id.isbn_4, "4");
        setupNumberButton(R.id.isbn_5, "5");
        setupNumberButton(R.id.isbn_6, "6");
        setupNumberButton(R.id.isbn_7, "7");
        setupNumberButton(R.id.isbn_8, "8");
        setupNumberButton(R.id.isbn_9, "9");
        setupNumberButton(R.id.isbn_X, "X");

        ImageButton buttonDel = findViewById(R.id.isbn_del);
        buttonDel.setOnClickListener(new View.OnClickListener() {
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

        Button confirmButton = findViewById(R.id.search);
        confirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String mIsbn = mIsbnText.getText().toString().trim();
                go(mIsbn, "", "");
            }
        });
    }

    private void setupNumberButton(@IdRes final int id, final String text) {
        Button button = findViewById(id);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                handleIsbnKey(text);
            }
        });
    }

    /*
     * Handle character insertion at cursor position in EditText
     */
    private void handleIsbnKey(@NonNull final String key) {
        int start = mIsbnText.getSelectionStart();
        int end = mIsbnText.getSelectionEnd();
        mIsbnText.getText().replace(start, end, key);
        mIsbnText.setSelection(start + 1, start + 1);
    }

    private void onCreateByScan(@Nullable final Bundle savedInstanceState) {
        mMode = MODE_SCAN;
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
                String isbn = savedInstanceState.getString(UniqueId.KEY_ISBN);
                if (isbn != null && !isbn.isEmpty()) {
                    go(isbn, "", "");
                }
            }
        } catch (SecurityException e) {
            AlertDialog dialog = new AlertDialog.Builder(BookISBNSearchActivity.this)
                    .setMessage(R.string.bad_scanner)
                    .setTitle(R.string.install_scan_title)
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
                            finish();
                        }
                    });
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            //do nothing
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
                AlertDialog dialog = new AlertDialog.Builder(BookISBNSearchActivity.this)
                        .setMessage(R.string.install_scan)
                        .setTitle(R.string.install_scan_title)
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
                                finish();
                            }
                        });
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                //do nothing
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
        this.setTitle(R.string.search_hint);
        this.initAuthorList();

        mTitleText = findViewById(R.id.title);

        Button mConfirmButton = findViewById(R.id.search);
        mConfirmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                String author = mAuthorText.getText().toString().trim();
                String title = mTitleText.getText().toString().trim();

                ArrayAdapter<String> adapter = mAuthorAdapter;
                if (adapter.getPosition(author) < 0) {
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
                            adapter.add(author);
                        }
                    }
                }
                go("", author, title);
            }
        });
    }


    // - MAJOR DATABASE ISSUES FOR THIS TO WORK!!!
//    protected void checkISBN(final String isbn) {
//        // If the book already exists, ask if the user wants to continue
//        try {
//            if (!isbn.isEmpty()) {
//                long bookId = mDb.getIdFromIsbn(isbn, false);
//                if (bookId > 0) {
//                    AlertDialog dialog = new AlertDialog.Builder(this)
//                            .setMessage("R.string.duplicate_alert")
//                            .setTitle("R.string.duplicate_title")
//                            .setIconAttribute(android.R.attr.alertDialogIcon)
//                            .create();
//
//                    dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok),
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(final DialogInterface dialog, final int which) {
//                                    go(isbn, "", "");
//                                }
//                            });
//                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel),
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(final DialogInterface dialog, final int which) {
//                                    finish();
//                                }
//                            });
//                    dialog.show();
//                } else {
//                    go(isbn, "", "");
//                }
//            } else {
//                go(isbn, "", "");
//            }
//        } catch (Exception e) {
//            Logger.error(e);
//        }
//    }


    /*
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
     * The details will then get sent to the {@link BookDetailsActivity} activity
     *
     * Either the isbn or the author/title needs to be specified.
     */
    private void go(@NonNull final String isbn, @NonNull final String author, @NonNull final String title) {
        if (BuildConfig.DEBUG) {
            Logger.debug("BookISBNSearchActivity.go: isbn=" + isbn + ", author=" + author + ", title=" + title);
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
                final Checkable allowAsinCb = findViewById(R.id.asinCheckbox);
                final boolean allowAsin = allowAsinCb != null && allowAsinCb.isChecked();

                if (!IsbnUtils.isValid(mIsbn) && (!allowAsin || !AsinUtils.isValid(mIsbn))) {
                    int msg;
                    if (allowAsin) {
                        msg = R.string.x_is_not_a_valid_isbn_or_asin;
                    } else {
                        msg = R.string.x_is_not_a_valid_isbn;
                    }
                    StandardDialogs.showQuickNotice(this, getString(msg, mIsbn));
                    if (mMode == MODE_SCAN) {
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
                    if (mMode == MODE_SCAN) {
                        // Optionally beep if scan was valid.
                        SoundManager.beepHigh();
                    }
                    // See if ISBN exists in catalogue TODO: we check again when user clicks 'save book' .. maybe remove the check here ?
                    final long existingId = mDb.getIdFromIsbn(mIsbn, true);
                    if (existingId > 0) {
                        // Verify - this can be a dangerous operation
                        AlertDialog dialog = new AlertDialog.Builder(this)
                                .setMessage(R.string.duplicate_book_message)
                                .setTitle(R.string.duplicate_book_title)
                                .setIconAttribute(android.R.attr.alertDialogIcon)
                                .create();

                        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.add),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        doSearchBook();
                                    }
                                });
                        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.edit_book),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        BookDetailsActivity.startEditMode(BookISBNSearchActivity.this,
                                                existingId, BookDetailsActivity.TAB_EDIT);
                                    }
                                });
                        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        //do nothing
                                        if (mMode == MODE_SCAN) {
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

    private void doSearchBook() {
        // Delete any hanging around temporary thumbs
        StorageUtils.deleteFile(StorageUtils.getTempCoverFile());

        // need at least one of the three. Normally none of these will be null ... paranoia strikes again...
        if ((mAuthor != null && !mAuthor.isEmpty()) || (mTitle != null && !mTitle.isEmpty()) || (mIsbn != null && !mIsbn.isEmpty())) {
            /* Get the book */
            try {
                // Start the lookup in background.
                final SearchManager searchManager = new SearchManager(getTaskManager(), mSearchHandler);
                mSearchManagerId = searchManager.getSenderId();
                Tracker.handleEvent(this, "Searching" + mSearchManagerId, Tracker.States.Running);

                getTaskManager().doProgress(getString(R.string.searching_ellipsis));
                searchManager.search(mAuthor, mTitle, mIsbn, true, SearchManager.SEARCH_ALL);
                // reset the details so we don't restart the search unnecessarily
                mAuthor = "";
                mTitle = "";
                mIsbn = "";
            } catch (Exception e) {
                Logger.error(e);
                StandardDialogs.showQuickNotice(this, R.string.search_fail);
                finish();
            }
        } else {
            if (mMode == MODE_SCAN) {
                startScannerActivity();
            }
        }
    }

    @SuppressWarnings("SameReturnValue")
    private boolean onSearchFinished(@NonNull final Bundle bookData, final boolean cancelled) {
        Tracker.handleEvent(this, "onSearchFinished" + mSearchManagerId, Tracker.States.Running);
        try {
            if (cancelled) {
                if (mMode == MODE_SCAN) {
                    startScannerActivity();
                }
            } else {
                getTaskManager().doProgress(getString(R.string.adding_book_ellipsis));
                createBook(bookData);
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
    protected void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().removeListener(mSearchManagerId, mSearchHandler);
        }
        Tracker.exitOnPause(this);
    }

    @Override
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().addListener(mSearchManagerId, mSearchHandler, true);
        }
        Tracker.exitOnResume(this);
    }

    @Override
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        if (mDb != null) {
            mDb.close();
        }
        Tracker.exitOnDestroy(this);
    }

    /**
     * Load the {@link BookDetailsActivity} Activity with the new book
     */
    private void createBook(@NonNull final Bundle bookData) {
        Intent i = new Intent(this, BookDetailsActivity.class);
        i.putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
        startActivityForResult(i, UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_SCAN: {
                mScannerStarted = false;
                try {
                    if (resultCode == RESULT_OK) {
                        String isbn = mScanner.getBarcode(intent);
                        mIsbnText.setText(isbn);
                        go(isbn, "", "");
                    } else {
                        // Scanner Cancelled/failed. Exit if no dialog present.
                        this.setResult(mLastBookIntent != null ? RESULT_OK : RESULT_CANCELED, mLastBookIntent);

                        if (!mDisplayingAlert) {
                            finish();
                        }
                    }
                } catch (NullPointerException e) {
                    Logger.error(e);
                    finish();
                }
                break;
            }
            case UniqueId.ACTIVITY_REQUEST_CODE_EDIT_BOOK: {
                if (intent != null) {
                    mLastBookIntent = intent;
                }
                if (mMode == MODE_SCAN) {
                    // Created a book; save the intent and restart scanner if necessary.
                    startScannerActivity();
                } else {
                    // If the 'Back' button is pressed on a normal activity, set the default result to cancelled by setting it here.
                    this.setResult(RESULT_CANCELED, mLastBookIntent);
                }
                break;
            }
        }

        // No matter what the activity was, rebuild the author list in case a new author was added.
        initAuthorList();

    }

    private void initAuthorList() {
        // Get the author field, if present
        mAuthorText = findViewById(R.id.author);
        if (mAuthorText != null) {
            // Get all known authors and build a hash of the names
            final ArrayList<String> authors = mDb.getAuthors();
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

    /*
     * Start scanner activity.
     */
    private void startScannerActivity() {
        if (mScanner == null) {
            mScanner = ScannerManager.getScanner();
        }
        if (!mScannerStarted) {
            mScannerStarted = true;
            mScanner.startActivityForResult(this, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_SCAN);
        }
    }

    /**
     * Ensure the TaskManager is restored.
     */
    @Override
    protected void onRestoreInstanceState(Bundle instanceState) {

        mSearchManagerId = instanceState.getLong(SEARCH_MANAGER_ID);

        // Now do 'standard' stuff
        mLastBookIntent = instanceState.getParcelable(LAST_BOOK_INTENT);

        // Call the super method only after we have the searchManager set up
        super.onRestoreInstanceState(instanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle instanceState) {
        super.onSaveInstanceState(instanceState);

        // Saving intent data is a kludge due to an apparent Android bug in some
        // handsets. Search for "BUG NOTE 1" in this source file for a discussion
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(UniqueId.KEY_ISBN)) {
                instanceState.putString(UniqueId.KEY_ISBN, extras.getString(UniqueId.KEY_ISBN));
            }
            if (extras.containsKey(BKEY_BY)) {
                instanceState.putString(BKEY_BY, extras.getString(BKEY_BY));
            }
        }

        // standard stuff we need
        if (mSearchManagerId != 0) {
            instanceState.putLong(SEARCH_MANAGER_ID, mSearchManagerId);
        }
        instanceState.putParcelable(LAST_BOOK_INTENT, mLastBookIntent);
        instanceState.putBoolean(SCANNER_STARTED, mScannerStarted);

        // Save the current search details as this may be called as a result of a rotate during an alert dialog.
        // note: these don't actually are getting read ? TODO: make 100% sure, then delete
        instanceState.putString(UniqueId.KEY_AUTHOR_NAME, mAuthor);
        instanceState.putString(UniqueId.KEY_ISBN, mIsbn);
        instanceState.putString(UniqueId.KEY_TITLE, mTitle);
    }
}
