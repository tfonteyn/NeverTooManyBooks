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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAdvancedSearchBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Search based on the SQLite FTS engine. Due to the speed of FTS it updates the
 * number of hits more or less in real time. The user can choose to see a full list at any time.
 * ENHANCE: SHOW the list, just like the system search does?
 * <p>
 * The form allows entering free text, author, title, series.
 * <p>
 * The search gets the ID's of matching books, and returns this list when the 'show' button
 * is tapped. <strong>Only this list is returned</strong>; the original fields are not.
 *
 * <strong>Note:</strong> when the fab is clicked, we <strong>RETURN</strong>
 * to the {@link BooksOnBookshelf} Activity.
 * This is intentionally different from the behaviour of {@link AuthorWorksFragment}.
 */
public class SearchFtsFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "SearchFtsFragment";

    /** create timer to tick every 250ms. */
    private static final int TIMER_TICK_MS = 250;
    /** 1 second idle trigger. */
    private static final int NANO_TO_SECONDS = 1_000_000_000;
    /** The maximum number of suggestions we'll show during a live search. */
    private static final int MAX_SUGGESTIONS = 20;

    /** Handle inter-thread messages. */
    private final Handler mHandler = new Handler();
    /** The results book id list. For sending back to the caller. */
    private final ArrayList<Long> mBookIdList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /** User entered search text. */
    @Nullable
    private String mAuthorSearchText;
    /** User entered search text. */
    @Nullable
    private String mTitleSearchText;
    /** User entered search text. */
    @Nullable
    private String mSeriesTitleSearchText;
    /** User entered search text. */
    @Nullable
    private String mPublisherNameSearchText;
    /** User entered search text. */
    @Nullable
    private String mKeywordsSearchText;
    /** Indicates user has changed something since the last search. */
    private boolean mSearchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long mIdleStart;
    /** Timer object for background idle searches. */
    @Nullable
    private Timer mTimer;
    /** Detect text changes and call userIsActive(...). */
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
        public void afterTextChanged(@NonNull final Editable editable) {
            // we're not going to change the Editable, no need to toggle this listener
            userIsActive(true);
        }
    };

    /** View Binding. */
    private FragmentAdvancedSearchBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        if (args != null) {
            mTitleSearchText = args.getString(DBDefinitions.KEY_TITLE);
            mSeriesTitleSearchText = args.getString(DBDefinitions.KEY_SERIES_TITLE);
            mAuthorSearchText = args.getString(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR);
            mPublisherNameSearchText = args.getString(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER);
            mKeywordsSearchText = args.getString(SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentAdvancedSearchBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_local_search);

        if (mTitleSearchText != null) {
            mVb.title.setText(mTitleSearchText);
        }
        if (mSeriesTitleSearchText != null) {
            mVb.seriesTitle.setText(mSeriesTitleSearchText);
        }

        if (mAuthorSearchText != null) {
            mVb.author.setText(mAuthorSearchText);
        }
        if (mPublisherNameSearchText != null) {
            mVb.publisher.setText(mPublisherNameSearchText);
        }
        if (mKeywordsSearchText != null) {
            mVb.keywords.setText(mKeywordsSearchText);
        }

        // Detect when user touches something.
        mVb.content.setOnTouchListener((v, event) -> {
            userIsActive(false);
            return false;
        });

        // Detect when user types something.
        mVb.title.addTextChangedListener(mTextWatcher);
        mVb.seriesTitle.addTextChangedListener(mTextWatcher);
        mVb.author.addTextChangedListener(mTextWatcher);
        mVb.publisher.addTextChangedListener(mTextWatcher);
        mVb.keywords.addTextChangedListener(mTextWatcher);

        // When the show results buttons is tapped, go show the resulting booklist.
        mVb.btnSearch.setOnClickListener(v -> {
            final Intent resultIntent = new Intent()
                    // pass these for displaying to the user
                    .putExtra(DBDefinitions.KEY_TITLE, mTitleSearchText)
                    .putExtra(DBDefinitions.KEY_SERIES_TITLE, mSeriesTitleSearchText)

                    .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                              mAuthorSearchText)
                    .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER,
                              mPublisherNameSearchText)
                    .putExtra(SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS,
                              mKeywordsSearchText)
                    // pass the book ID's for the list
                    .putExtra(Book.BKEY_BOOK_ID_LIST, mBookIdList);
            getActivity().setResult(Activity.RESULT_OK, resultIntent);
            getActivity().finish();
        });

        // Timer will be started in OnResume().
    }

    /**
     * When activity resumes, set search as dirty + start the timer.
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        userIsActive(true);
    }

    /**
     * When activity pauses, stop timer and get the search fields.
     */
    @Override
    @CallSuper
    public void onPause() {
        stopIdleTimer();
        // Get search criteria
        viewToModel();

        super.onPause();
    }

    private void updateUi(final int count) {
        final String s = getResources().getQuantityString(R.plurals.n_books_found, count, count);
        mVb.booksFound.setText(s);
    }

    private void viewToModel() {

        //noinspection ConstantConditions
        mTitleSearchText = mVb.title.getText().toString().trim();
        //noinspection ConstantConditions
        mSeriesTitleSearchText = mVb.seriesTitle.getText().toString().trim();
        //noinspection ConstantConditions
        mAuthorSearchText = mVb.author.getText().toString().trim();
        //noinspection ConstantConditions
        mPublisherNameSearchText = mVb.publisher.getText().toString().trim();
        //noinspection ConstantConditions
        mKeywordsSearchText = mVb.keywords.getText().toString().trim();
    }

    /**
     * Called when a UI element detects the user doing something.
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    private void userIsActive(final boolean dirty) {
        synchronized (this) {
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

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(DBDefinitions.KEY_TITLE, mTitleSearchText);
        outState.putString(DBDefinitions.KEY_SERIES_TITLE, mSeriesTitleSearchText);
        outState.putString(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, mAuthorSearchText);
        outState.putString(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, mPublisherNameSearchText);
        outState.putString(SearchCriteria.BKEY_SEARCH_TEXT_KEYWORDS, mKeywordsSearchText);
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
        synchronized (this) {
            if (mTimer != null) {
                return;
            }
            mTimer = new Timer();
            mIdleStart = System.nanoTime();
        }

        mTimer.schedule(new SearchUpdateTimer(), 0, TIMER_TICK_MS);
    }

    /**
     * Stop the timer.
     */
    private void stopIdleTimer() {
        final Timer timer;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            timer = mTimer;
            mTimer = null;
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    public static class ResultContract
            extends ActivityResultContract<SearchCriteria, Bundle> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final SearchCriteria criteria) {
            final Intent intent = new Intent(context, HostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, SearchFtsFragment.TAG);
            criteria.to(intent);
            return intent;
        }

        @Override
        @Nullable
        public Bundle parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return null;
            }
            return intent.getExtras();
        }
    }

    /**
     * Implements a timer task (Runnable) and does a search when the user is idle.
     * <p>
     * If a search happens, we stop the idle timer.
     */
    class SearchUpdateTimer
            extends TimerTask {

        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize since this is relevant to more than 1 thread.
            synchronized (this) {
                final boolean idle = (System.nanoTime() - mIdleStart) > NANO_TO_SECONDS;
                if (idle) {
                    // Stop the timer, it will be restarted when the user changes something
                    stopIdleTimer();
                    if (mSearchIsDirty) {
                        doSearch = true;
                        mSearchIsDirty = false;
                    }
                }
            }

            if (doSearch) {
                // we CAN actually read the Views here ?!
                viewToModel();

                int count = 0;
                try (Cursor cursor = mDb.fetchSearchSuggestionsAdv(mAuthorSearchText,
                                                                   mTitleSearchText,
                                                                   mSeriesTitleSearchText,
                                                                   mPublisherNameSearchText,
                                                                   mKeywordsSearchText,
                                                                   MAX_SUGGESTIONS)) {
                    // Null return means searchFts thought the parameters were effectively blank.
                    if (cursor != null) {
                        count = cursor.getCount();

                        mBookIdList.clear();
                        while (cursor.moveToNext()) {
                            mBookIdList.add(cursor.getLong(0));
                        }
                    }
                }

                // Update the UI in main thread.
                final int bookCount = count;
                mHandler.post(() -> updateUi(bookCount));
            }
        }
    }
}
