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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

/**
 * Activity to handle searching Goodreads for books that did not automatically convert. These
 * are typically books with no ISBN.
 *
 * The search criteria is setup to contain the book author, title and ISBN. The user can edit
 * these and search Goodreads, then review the results.
 *
 * @author Philip Warner
 */
public class GoodreadsSearchCriteriaActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_GOODREADS_SEARCH_CRITERIA;

    private CatalogueDBAdapter mDb;
    private long mBookId = 0;

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_search_criteria;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        mDb = new CatalogueDBAdapter(this);

        Bundle extras = this.getIntent().getExtras();

        // Look for a book ID
        if (extras != null && extras.containsKey(UniqueId.KEY_ID)) {
            mBookId = extras.getLong(UniqueId.KEY_ID);
        }

        // If we have a book, fill in criteria AND try a search
        if (mBookId != 0) {
            // Initial value; try to build from passed book, if available.
            StringBuilder criteria = new StringBuilder();

            setViewVisibility(R.id.original_details, true);

            try (BookCursor cursor = mDb.fetchBookById(mBookId)){
                if (!cursor.moveToFirst()) {
                    StandardDialogs.showUserMessage(this, R.string.warning_book_no_longer_exists);
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                final BookRowView bookCursorRow = cursor.getCursorRow();
                {
                    String s = bookCursorRow.getPrimaryAuthorNameFormattedGivenFirst();
                    setViewText(R.id.author, s);
                    criteria.append(s).append(" ");
                }
                {
                    String s = bookCursorRow.getTitle();
                    setViewText(R.id.title, s);
                    criteria.append(s).append(" ");
                }
                {
                    String s = bookCursorRow.getIsbn();
                    setViewText(R.id.isbn, s);
                    criteria.append(s).append(" ");
                }
            }

            setViewText(R.id.search_text, criteria.toString().trim());
            doSearch();
        } else {
            setViewVisibility(R.id.original_details, false);
        }

        setClickListener(R.id.search, new OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });
        Tracker.exitOnCreate(this);
    }

    /**
     * Set the visibility of the passed view.
     */
    private void setViewVisibility(@SuppressWarnings("SameParameterValue") final @IdRes int id, final boolean visible) {
        this.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Set the text of the passed view
     */
    private void setViewText(final @IdRes int id, final @NonNull String s) {
        ((TextView) this.findViewById(id)).setText(s);
    }

    /**
     * Get the text of the passed view
     */
    private String getViewText(@SuppressWarnings("SameParameterValue") final @IdRes int id) {
        return ((TextView) this.findViewById(id)).getText().toString().trim();
    }

    /**
     * Set the OnClickListener for the passed view
     */
    private void setClickListener(@SuppressWarnings("SameParameterValue") final @IdRes int id, final @NonNull OnClickListener listener) {
        this.findViewById(id).setOnClickListener(listener);
    }


    /**
     * Start the search results activity.
     */
    private void doSearch() {
        String criteria = getViewText(R.id.search_text);

        if (criteria.isEmpty()) {
            StandardDialogs.showUserMessage(this, R.string.please_enter_search_criteria);
            return;
        }

        Intent i = new Intent(this, GoodreadsSearchResultsActivity.class);
        i.putExtra(GoodreadsSearchResultsActivity.BKEY_SEARCH_CRITERIA, criteria);
        this.startActivity(i);
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
}
