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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Search goodreads for a book and display the list of results.
 * Use background tasks to get thumbnails and update when retrieved.
 * <p>
 * Used by {@link GoodreadsSearchCriteriaActivity} which is currently
 * commented out in {@link SendBooksTask}
 *
 * @author Philip Warner
 */
public class GoodreadsSearchResultsActivity
        extends BaseActivity {

    private static final String TAG = GoodreadsSearchResultsActivity.class.getSimpleName();

    public static final String BKEY_SEARCH_CRITERIA = TAG + ":criteria";

    private DBA mDb;

    /** The View for the list. */
    private ListView mListView;

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_work_list;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DBA(this);

        mListView = findViewById(android.R.id.list);

        // Look for search criteria
        String criteria = getIntent().getStringExtra(BKEY_SEARCH_CRITERIA);

        // If we have criteria, do a search. Otherwise complain and finish.
        if (criteria != null && !criteria.isEmpty()) {
            doSearch(criteria);
        } else {
            UserMessage.showUserMessage(this, R.string.search_please_enter_criteria);
            setResult(Activity.RESULT_CANCELED);
            finish();
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
    private void doSearch(@NonNull final String criteria) {
        // Get the GR stuff we need
        GoodreadsManager grMgr = new GoodreadsManager();
        SearchBooksApiHandler searcher = new SearchBooksApiHandler(grMgr);

        // Run the search
        List<GoodreadsWork> works;
        try {
            works = searcher.search(criteria.trim());
        } catch (BookNotFoundException
                | AuthorizationException
                | IOException
                | RuntimeException e) {
            Logger.error(this, e, "Failed when searching Goodreads");
            String msg = getString(R.string.gr_error_while_searching)
                    + ' ' + getString(R.string.error_if_the_problem_persists);
            UserMessage.showUserMessage(this, msg);
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        // Finish if no results, otherwise display them
        if (works.isEmpty()) {
            UserMessage.showUserMessage(this, R.string.warning_no_matching_book_found);
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        ArrayAdapter<GoodreadsWork> adapter = new ResultsAdapter(this, works);
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
        UserMessage.showUserMessage(this, msg);
        //Intent i = new Intent(this, GoodreadsW)
    }

    /**
     * Holder pattern for search results.
     *
     * @author Philip Warner
     */
    private static class Holder {

        @NonNull
        final ImageView coverView;
        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        GoodreadsWork work;

        Holder(@NonNull final View rowView) {
            coverView = rowView.findViewById(R.id.coverImage);
            authorView = rowView.findViewById(R.id.author);
            titleView = rowView.findViewById(R.id.title);

            rowView.setTag(this);
        }
    }

    /**
     * ArrayAdapter that uses holder pattern to display goodreads books and
     * allows for background image retrieval.
     *
     * @author Philip Warner
     */
    private class ResultsAdapter
            extends ArrayAdapter<GoodreadsWork> {

        @NonNull
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context caller context
         * @param objects the list
         */
        ResultsAdapter(@NonNull final Context context,
                       @NonNull final List<GoodreadsWork> objects) {
            super(context, 0, objects);
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        public View getView(final int position,
                            @Nullable View convertView,
                            @NonNull final ViewGroup parent) {
            Holder holder;
            if (convertView != null) {
                // Recycling: just get the holder
                holder = (Holder) convertView.getTag();
            } else {
                // Not recycling, get a new View and make the holder for it.
                convertView = mInflater.inflate(R.layout.goodreads_work_item, parent, false);

                holder = new Holder(convertView);
                convertView.setOnClickListener(v -> doItemClick((Holder) v.getTag()));
            }

            synchronized (holder.coverView) {
                // Save the work details
                holder.work = getItem(position);
                // get the cover (or put it in background task)
                //noinspection ConstantConditions
                holder.work.fillImageView(holder.coverView);

                // Update the views based on the work
                holder.authorView.setText(holder.work.authorName);
                holder.titleView.setText(holder.work.title);
            }

            return convertView;
        }
    }
}
