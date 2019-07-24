/*
 * @copyright 2012 Philip Warner
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Search based on the SQLite FTS engine. Due to the speed of FTS it updates the
 * number of hits more or less in real time. The user can choose to see a full list at any time.
 * <p>
 * The form allows entering free text, author, title.
 * <p>
 * The search gets the ID's of matching books, and returns this list when the 'show' button
 * is tapped. *Only* this list is returned; the original fields are not.
 *
 * @author Philip Warner
 */
public class FTSSearchActivity
        extends BaseActivity {

    /** create timer to tick every 250ms. */
    private static final int TIMER_TICK = 250;
    /** Handle inter-thread messages. */
    private final Handler mSCHandler = new Handler();
    /** Database access. */
    private DAO mDb;

    private String mAuthorSearchText;
    private String mTitleSearchText;
    private String mKeywordsSearchText;

    /** search field. */
    private EditText mAuthorView;
    /** search field. */
    private EditText mTitleView;
    /** search field. */
    private EditText mKeywordsView;
    /** show the number of results. */
    private TextView mBooksFound;
    /** The results list. */
    private ArrayList<Long> mBookIdsFound;
    /** Indicates user has changed something since the last search. */
    private boolean mSearchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long mIdleStart;
    /** Timer object for background idle searches. */
    @Nullable
    private Timer mTimer;

    /**
     * Detect text changes and call userIsActive(...).
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {
        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {
        }

        @Override
        public void afterTextChanged(@NonNull final Editable s) {
            userIsActive(true);
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_search_fts;
    }

    private void readArgs(@NonNull final Bundle args) {
        mAuthorSearchText = args.getString(UniqueId.BKEY_SEARCH_AUTHOR);
        mTitleSearchText = args.getString(UniqueId.BKEY_SEARCH_TITLE);
        mKeywordsSearchText = args.getString(UniqueId.BKEY_SEARCH_TEXT);
    }

    // reminder: when converting to a fragment, don't forget this:
//    @Override
//    @CallSuper
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // make sure {@link #onCreateOptionsMenu} is called
//        setHasOptionsMenu(true);
//    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                readArgs(extras);
            }
        } else {
            readArgs(savedInstanceState);
        }

        mAuthorView = findViewById(R.id.author);
        mTitleView = findViewById(R.id.title);
        mKeywordsView = findViewById(R.id.keywords);

        if (mAuthorSearchText != null) {
            mAuthorView.setText(mAuthorSearchText);
        }
        if (mTitleSearchText != null) {
            mTitleView.setText(mTitleSearchText);
        }
        if (mKeywordsSearchText != null) {
            mKeywordsView.setText(mKeywordsSearchText);
        }

        mDb = new DAO();

        mBooksFound = findViewById(R.id.books_found);

        // Detect when user touches something, just so we know they are 'busy'.
        findViewById(R.id.root).setOnTouchListener((v, event) -> {
            userIsActive(false);
            return false;
        });

        // If the user changes any text, it's not idle
        mAuthorView.addTextChangedListener(mTextWatcher);
        mTitleView.addTextChangedListener(mTextWatcher);
        mKeywordsView.addTextChangedListener(mTextWatcher);

        // Handle the 'Search' button.
        findViewById(R.id.btn_search).setOnClickListener(v -> {
            Intent data = new Intent()
                    // pass these for displaying to the user
                    .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, mAuthorSearchText)
                    .putExtra(UniqueId.BKEY_SEARCH_TITLE, mTitleSearchText)
                    .putExtra(UniqueId.BKEY_SEARCH_TEXT, mKeywordsSearchText)
                    // pass the book ID's for the list
                    .putExtra(UniqueId.BKEY_ID_LIST, mBookIdsFound);
            setResult(Activity.RESULT_OK, data);
            finish();
        });

        // Timer will be started in OnResume().
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_REBUILD_FTS, 0, R.string.rebuild_fts)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_REBUILD_FTS:
                mDb.rebuildFts();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Called in the timer thread, this code will run the search then queue the UI
     * updates to the main thread.
     */
    private void doSearch() {
        // Get search criteria
        mAuthorSearchText = mAuthorView.getText().toString().trim();
        mTitleSearchText = mTitleView.getText().toString().trim();
        mKeywordsSearchText = mKeywordsView.getText().toString().trim();

        // Get the cursor
        String tmpMsg = null;
        try (Cursor cursor = mDb.searchFts(mAuthorSearchText,
                                           mTitleSearchText,
                                           mKeywordsSearchText)) {
            // Null return means searchFts thought parameters were effectively blank
            if (cursor != null) {
                int count = cursor.getCount();
                tmpMsg = getResources().getQuantityString(R.plurals.n_books_found, count, count);
                mBookIdsFound = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    mBookIdsFound.add(cursor.getLong(0));
                }

            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
        }

        final String message = (tmpMsg != null) ? tmpMsg : "";

        // Update the UI in main thread.
        mSCHandler.post(() -> mBooksFound.setText(message));
    }

    /**
     * Called when a UI element detects the user doing something.
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    private void userIsActive(final boolean dirty) {
        synchronized (FTSSearchActivity.this) {
            // Mark search dirty if necessary
            mSearchIsDirty = mSearchIsDirty || dirty;
            // Reset the idle timer since the user did something
            mIdleStart = System.nanoTime();
            // If the search is dirty, make sure idle timer is running and update UI
            if (mSearchIsDirty) {
                startIdleTimer();
            }
        }
    }

    /**
     * When activity resumes, set search as dirty.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        userIsActive(true);
    }

    /**
     * When activity pauses, stop timer and get the search fields.
     */
    @Override
    @CallSuper
    protected void onPause() {
        stopIdleTimer();
        // Get search criteria
        mAuthorSearchText = mAuthorView.getText().toString().trim();
        mTitleSearchText = mTitleView.getText().toString().trim();
        mKeywordsSearchText = mKeywordsView.getText().toString().trim();

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, mAuthorSearchText);
        outState.putString(UniqueId.BKEY_SEARCH_TITLE, mTitleSearchText);
        outState.putString(UniqueId.BKEY_SEARCH_TEXT, mKeywordsSearchText);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        stopIdleTimer();

        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * start the idle timer.
     */
    private void startIdleTimer() {
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (FTSSearchActivity.this) {
            if (mTimer != null) {
                return;
            }
            mTimer = new Timer();
            mIdleStart = System.nanoTime();
        }

        mTimer.schedule(new SearchUpdateTimer(), 0, TIMER_TICK);
    }

    /**
     * Stop the timer.
     */
    private void stopIdleTimer() {
        Timer tmr;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (FTSSearchActivity.this) {
            tmr = mTimer;
            mTimer = null;
        }
        if (tmr != null) {
            tmr.cancel();
        }
    }

    /**
     * Implements a timer task and does a search when the user is idle.
     * <p>
     * If a search happens, we stop the idle timer.
     *
     * @author Philip Warner
     */
    private class SearchUpdateTimer
            extends TimerTask {

        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize since this is relevant to more than 1 thread.
            synchronized (FTSSearchActivity.this) {
                boolean idle = (System.nanoTime() - mIdleStart) > 1_000_000;
                if (idle) {
                    // Stop the timer, it will be restarted if the user changes something
                    stopIdleTimer();
                    if (mSearchIsDirty) {
                        doSearch = true;
                        mSearchIsDirty = false;
                    }
                }
            }
            if (doSearch) {
                doSearch();
            }
        }
    }
}
