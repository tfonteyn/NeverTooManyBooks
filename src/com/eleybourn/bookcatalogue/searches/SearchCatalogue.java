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

package com.eleybourn.bookcatalogue.searches;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Catalogue search based on the SQLite FTS engine. Due to the speed of FTS it updates the
 * number of hits more or less in real time. The user can choose to see a full list at any
 * time.
 *
 * ENHANCE: Finish ! FTS activity.
 *
 * @author Philip Warner
 */
public class SearchCatalogue extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_SEARCH;

    /** Handle inter-thread messages */
    private final Handler mSCHandler = new Handler();
    /** Handle the 'Search' button. */
    private final OnClickListener mShowResultsListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent data = new Intent();
            data.putExtra(UniqueId.BKEY_BOOK_ID_LIST, mBookIdsFound);
            setResult(Activity.RESULT_OK, data);
            finish();
        }
    };
    private EditText mAuthorTextView;
    private EditText mTitleTextView;
    private EditText mCSearchTextView;

    private Button mShowResultsBtn;
    private Button mFtsRebuildBtn;
    private CatalogueDBAdapter mDb;
    /** Handle the 'FTS Rebuild' button. */
    private final OnClickListener mFtsRebuildListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mDb.rebuildFts();
        }
    };
    private TextView mBooksFound;
    private ArrayList<Integer> mBookIdsFound;
    /** Indicates user has changed something since the last search. */
    private boolean mSearchDirty = false;
    /** Timer reset each time the user clicks, in order to detect an idle time */
    private long mIdleStart = 0;
    /** Timer object for background idle searches */
    @Nullable
    private Timer mTimer;

    /** Detect when user touches something, just so we know they are 'busy'. */
    private final OnTouchListener mOnTouchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            userIsActive(false);
            return false;
        }
    };
    /**
     * Detect text changes and call userIsActive(...).
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
            userIsActive(true);
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_search_catalogue;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuthorTextView = this.findViewById(R.id.author);
        mTitleTextView = this.findViewById(R.id.title);
        mCSearchTextView = this.findViewById(R.id.criteria);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String author = extras.getString(UniqueId.KEY_AUTHOR_NAME);
            if (author != null) {
                mAuthorTextView.setText(author);
            }
            String title = extras.getString(UniqueId.KEY_TITLE);
            if (title != null) {
                mTitleTextView.setText(title);
            }
            String text = extras.getString(UniqueId.BKEY_SEARCH_TEXT);
            if (text != null) {
                mCSearchTextView.setText(text);
            }
        }

        // Get the DB and setup the layout.
        mDb = new CatalogueDBAdapter(this);
        mDb.open();

        mBooksFound = this.findViewById(R.id.books_found);
        mShowResultsBtn = this.findViewById(R.id.search);
        mFtsRebuildBtn = this.findViewById(R.id.rebuild);

        // If the user touches anything, it's not idle
        findViewById(R.id.root).setOnTouchListener(mOnTouchListener);

        // If the user changes any text, it's not idle
        mAuthorTextView.addTextChangedListener(mTextWatcher);
        mTitleTextView.addTextChangedListener(mTextWatcher);
        mCSearchTextView.addTextChangedListener(mTextWatcher);

        // Handle button presses
        mFtsRebuildBtn.setOnClickListener(mFtsRebuildListener);
        mShowResultsBtn.setOnClickListener(mShowResultsListener);

        // Note: Timer will be started in OnResume().
    }

    /**
     * start the idle timer
     */
    private void startIdleTimer() {
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (SearchCatalogue.this) {
            if (mTimer != null) {
                return;
            }
            mTimer = new Timer();
            mIdleStart = System.currentTimeMillis();
        }
        //create timer to tick every 200ms
        mTimer.schedule(new SearchUpdateTimer(), 0, 250);
    }

    /**
     * Stop the timer.
     */
    private void stopIdleTimer() {
        Timer tmr;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (SearchCatalogue.this) {
            tmr = mTimer;
            mTimer = null;
        }
        if (tmr != null) {
            tmr.cancel();
        }
    }

    /**
     * Called in the timer thread, this code will run the search then queue the UI
     * updates to the main thread.
     */
    private void doSearch() {
        // Get search criteria
        String author = mAuthorTextView.getText().toString().trim();
        String title = mTitleTextView.getText().toString().trim();
        String criteria = mCSearchTextView.getText().toString().trim();

        // Save time to log how long query takes.
        long t0 = System.currentTimeMillis();

        // Get the cursor
        String tmpMsg = null;
        try (Cursor cursor = mDb.searchFts(author, title, criteria)) {
            if (cursor != null) {
                int count = cursor.getCount();
                t0 = System.currentTimeMillis() - t0;
                tmpMsg = "(" + count + " books found in " + t0 + "ms)";
                mBookIdsFound = new ArrayList<>();
                while (cursor.moveToNext()) {
                    mBookIdsFound.add(cursor.getInt(0));
                }

            } else if (BuildConfig.DEBUG) {
                // Null return means searchFts thought parameters were effectively blank
                tmpMsg = "(enter search criteria)";
            }
        } catch (Exception e) {
            Logger.error(e);
        }

        final String message = (tmpMsg != null ? tmpMsg : "");

        // Update the UI in main thread.
        mSCHandler.post(new Runnable() {
            @Override
            public void run() {
                mBooksFound.setText(message);
            }
        });
    }

    /**
     * Called when a UI element detects the user doing something
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    @SuppressLint("SetTextI18n")
    private void userIsActive(final boolean dirty) {
        synchronized (SearchCatalogue.this) {
            // Mark search dirty if necessary
            mSearchDirty = mSearchDirty || dirty;
            // Reset the idle timer since the user did something
            mIdleStart = System.currentTimeMillis();
            // If the search is dirty, make sure idle timer is running and update UI
            if (mSearchDirty) {
                if (BuildConfig.DEBUG) {
                    mBooksFound.setText("(waiting for idle)");
                }
                startIdleTimer(); // (if not started)
            }
        }
    }

    /**
     * When activity pauses, stop timer.
     */
    @Override
    @CallSuper
    protected void onPause() {
        super.onPause();
        stopIdleTimer();
    }

    /**
     * When activity resumes, set search as dirty
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        userIsActive(true);
    }

    /**
     * Cleanup
     */
    @Override
    @CallSuper
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mDb != null) {
                mDb.close();
            }
        } catch (Exception ignored) {
        }
        try {
            stopIdleTimer();
        } catch (Exception ignored) {
        }
    }

    /**
     * Class to implement a timer task and do a search when necessary, if idle.
     * <p>
     * If a search happens, we stop the idle timer.
     *
     * @author Philip Warner
     */
    private class SearchUpdateTimer extends TimerTask {
        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize since this is relevant to more than 1 thread.
            synchronized (SearchCatalogue.this) {
                long timeNow = System.currentTimeMillis();
                boolean idle = (timeNow - mIdleStart) > 1000;
                if (idle) {
                    // Stop the timer, it will be restarted if the user changes something
                    stopIdleTimer();
                    if (mSearchDirty) {
                        doSearch = true;
                        mSearchDirty = false;
                    }
                }
            }
            if (doSearch) {
                doSearch();
            }
        }
    }
}
