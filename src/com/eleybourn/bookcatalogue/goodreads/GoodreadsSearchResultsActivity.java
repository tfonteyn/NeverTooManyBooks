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

package com.eleybourn.bookcatalogue.goodreads;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.List;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.api.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;

/**
 * Search goodreads for a book and display the list of results.
 * Use background tasks to get thumbnails and update when retrieved.
 * <p>
 * Used by {@link GoodreadsSearchCriteriaActivity} which is currently
 * commented out in @link GrSendBooksTaskBase
 *
 * @author Philip Warner
 */
public class GoodreadsSearchResultsActivity
        extends BaseActivity {

    private static final String TAG = GoodreadsSearchResultsActivity.class.getSimpleName();

    public static final String BKEY_SEARCH_CRITERIA = TAG + ":criteria";

    /** Database access. */
    private DAO mDb;

    /** The View for the list. */
    private RecyclerView mListView;

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_work_list;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        mListView = findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(linearLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        String criteria = getIntent().getStringExtra(BKEY_SEARCH_CRITERIA);

        // If we have criteria, do a search. Otherwise complain and finish.
        if (criteria != null && !criteria.isEmpty()) {
            doSearch(criteria);
        } else {
            throw new IllegalArgumentException("criteria were null/empty.");
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Perform the search.
     */
    private void doSearch(@NonNull final String query) {
        // Get the GR stuff we need
        GoodreadsManager grMgr = new GoodreadsManager();
        SearchBooksApiHandler searcher = new SearchBooksApiHandler(grMgr);

        // Run the search
        List<GoodreadsWork> works;
        try {
            works = searcher.search(query);
        } catch (BookNotFoundException
                | AuthorizationException
                | IOException
                | RuntimeException e) {
            Logger.error(this, e, "Failed when searching Goodreads");
            String msg = getString(R.string.gr_error_while_searching)
                    + ' ' + getString(R.string.error_if_the_problem_persists);
            UserMessage.showUserMessage(mListView, msg);
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        // Finish if no results, otherwise display them
        if (works.isEmpty()) {
            UserMessage.showUserMessage(mListView, R.string.warning_no_matching_book_found);
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        ResultsAdapter adapter = new ResultsAdapter(this, works);
        mListView.setAdapter(adapter);
    }

    /**
     * Handle user clicking on a book.
     * This should show editions and allow the user to select a specific edition.
     * ENHANCE: Waiting on approval for API access.
     */
    private void doItemClick(@NonNull final Holder holder) {
        // TODO: Implement edition lookup - requires access to work.editions API from GR
        String msg = "Not implemented: see " + holder.titleView + " by " + holder.authorView;
        Logger.debugWithStackTrace(this, "doItemClick", msg);
        UserMessage.showUserMessage(mListView, msg);
    }

    /**
     * Holder pattern for search results.
     *
     * @author Philip Warner
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final ImageView coverView;
        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            coverView = itemView.findViewById(R.id.coverImage);
            authorView = itemView.findViewById(R.id.author);
            titleView = itemView.findViewById(R.id.title);
        }
    }

    /**
     * Adapter that uses holder pattern to display goodreads books and
     * allows for background image retrieval.
     *
     * @author Philip Warner
     */
    private class ResultsAdapter
            extends RecyclerViewAdapterBase<GoodreadsWork, Holder> {

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   the list
         */
        ResultsAdapter(@NonNull final Context context,
                       @NonNull final List<GoodreadsWork> items) {
            super(context, items, null);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            if (BuildConfig.DEBUG) {
                debugNewViewCounter.incrementAndGet();
                Logger.debug(this, "onCreateViewHolder",
                             "debugNewViewCounter=" + debugNewViewCounter.get(),
                             "viewType=" + viewType);
            }

            View view = getLayoutInflater().inflate(R.layout.goodreads_work_item, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            GoodreadsWork work = getItem(position);

            holder.itemView.setTag(R.id.TAG_VIEW_HOLDER, this);
            holder.itemView.setOnClickListener(
                    v -> doItemClick((Holder) v.getTag(R.id.TAG_VIEW_HOLDER)));

            // get the cover (or put it in background task)
            work.fillImageView(holder.coverView);

            // Update the views based on the work
            holder.authorView.setText(work.authorName);
            holder.titleView.setText(work.title);
        }
    }
}
