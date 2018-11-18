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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Search goodreads for a book and display the list of results.
 * Use background tasks to get thumbnails and update when retrieved.
 *
 * @author Philip Warner
 */
public class GoodreadsSearchResultsActivity extends BaseListActivity {

    public static final String BKEY_SEARCH_CRITERIA = "criteria";
    private final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("gr-covers");
    private CatalogueDBAdapter mDb;
    private List<GoodreadsWork> mList = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_work_list;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new CatalogueDBAdapter(this);

        // Look for search criteria
        String criteria = getIntent().getStringExtra(BKEY_SEARCH_CRITERIA);

        // If we have criteria, do a search. Otherwise complain and finish.
        if (criteria != null && !criteria.isEmpty()) {
            doSearch(criteria);
        } else {
            StandardDialogs.showUserMessage(this, R.string.please_enter_search_criteria);
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    /**
     * Perform the search.
     */
    private void doSearch(final @NonNull String criteria) {
        // Get the GR stuff we need
        GoodreadsManager grMgr = new GoodreadsManager();
        SearchBooksApiHandler searcher = new SearchBooksApiHandler(grMgr);

        // Run the search
        List<GoodreadsWork> works;
        try {
            works = searcher.search(criteria.trim());
        } catch (Exception e) {
            Logger.error(e, "Failed when searching goodreads");
            StandardDialogs.showUserMessage(this,
                    getString(R.string.gr_error_while_searching) + " " + getString(R.string.error_if_the_problem_persists));
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        // Finish if no results, otherwise display them
        if (works.size() == 0) {
            StandardDialogs.showUserMessage(this, R.string.warning_no_matching_book_found);
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        mList = works;
        ArrayAdapter<GoodreadsWork> adapter = new ResultsAdapter();
        setListAdapter(adapter);
    }

    /**
     * Handle user clicking on a book. This should show editions and allow the user to select a specific edition.
     * Waiting on approval for API access.
     *
     * @param view View that was clicked.
     */
    private void doItemClick(final @NonNull View view) {
        ListHolder holder = ViewTagger.getTag(view);
        Objects.requireNonNull(holder);

        // TODO: Implement edition lookup - requires access to work.editions API from GR
        Logger.debug("Not implemented: see " + holder.title + " by " + holder.author);
        StandardDialogs.showUserMessage(this, "Not implemented: see " + holder.title + " by " + holder.author);
        //Intent i = new Intent(this, GoodreadsW)
    }

    @Override
    @CallSuper
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    /**
     * Class used in implementing holder pattern for search results.
     *
     * cover made final for use as lock
     *
     * @author Philip Warner
     */
    private class ListHolder {
        @NonNull
        final ImageView cover;
        GoodreadsWork work;
        TextView title;
        TextView author;
        ListHolder(final @NonNull ImageView cover) {
            this.cover = cover;
        }
    }

    /**
     * ArrayAdapter that uses holder pattern to display goodreads books and allows for background image retrieval.
     *
     * @author Philip Warner
     */
    private class ResultsAdapter extends ArrayAdapter<GoodreadsWork> {
        /** Used in building views when needed */
        @NonNull
        final LayoutInflater mInflater;

        ResultsAdapter() {
            super(GoodreadsSearchResultsActivity.this, 0, mList);
            // Save Inflater for later use
            //noinspection ConstantConditions
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        public View getView(final int position, @Nullable View convertView, final @NonNull ViewGroup parent) {
            ListHolder holder;
            if (convertView == null) {
                // Not recycling
                try {
                    // Get a new View and make the holder for it.
                    convertView = mInflater.inflate(R.layout.goodreads_work_item, parent, false);

                    holder = new ListHolder((ImageView) convertView.findViewById(R.id.coverImage));
                    holder.author = convertView.findViewById(R.id.author);
                    holder.title = convertView.findViewById(R.id.title);

                    // Save the holder
                    ViewTagger.setTag(convertView, holder);

                    // Set the click listener
                    convertView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(@NonNull View v) {
                            doItemClick(v);
                        }
                    });

                } catch (Exception e) {
                    Logger.error(e);
                    throw new RuntimeException(e);
                }
            } else {
                // Recycling: just get the holder
                holder = ViewTagger.getTagOrThrow(convertView);
            }

            synchronized (convertView) {
                synchronized (holder.cover) {
                    // Save the work details
                    holder.work = mList.get(position);
                    // get the cover (or put it in background task)
                    holder.work.fillImageView(mTaskQueue, holder.cover);

                    // Update the views based on the work
                    holder.author.setText(holder.work.authorName);
                    holder.title.setText(holder.work.title);
                }
            }

            return convertView;
        }
    }
}
