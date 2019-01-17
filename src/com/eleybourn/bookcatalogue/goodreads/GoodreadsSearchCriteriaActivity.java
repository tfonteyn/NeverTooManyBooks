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
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

/**
 * Activity to handle searching Goodreads for books that did not automatically convert.
 * These are typically books with no ISBN.
 * <p>
 * The search criteria is setup to contain the book author, title and ISBN.
 * The user can edit these, search Goodreads, and then review the results.
 * <p>
 * See {@link SendBookEvents} where the use of this activity is commented out.
 *
 * @author Philip Warner
 */
public class GoodreadsSearchCriteriaActivity
        extends BaseActivity {

    private DBA mDb;
    private long mBookId;

    private TextView mCriteriaView;

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_search_criteria;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DBA(this);

        mCriteriaView = findViewById(R.id.search_text);

        Bundle extras = this.getIntent().getExtras();

        // Look for a book ID
        if (extras != null && extras.containsKey(UniqueId.KEY_ID)) {
            mBookId = extras.getLong(UniqueId.KEY_ID);
        }

        // If we have a book, fill in criteria AND try a search
        if (mBookId != 0) {
            // Initial value; try to build from passed book, if available.
            StringBuilder criteria = new StringBuilder();

            findViewById(R.id.original_details).setVisibility(View.VISIBLE);

            try (BookCursor cursor = mDb.fetchBookById(mBookId)) {
                if (!cursor.moveToFirst()) {
                    StandardDialogs.showUserMessage(this,
                                                    R.string.warning_book_no_longer_exists);
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                final BookRowView bookCursorRow = cursor.getCursorRow();
                String s;

                s = bookCursorRow.getPrimaryAuthorNameFormattedGivenFirst();
                ((TextView) findViewById(R.id.author)).setText(s);
                criteria.append(s).append(' ');

                s = bookCursorRow.getTitle();
                ((TextView) findViewById(R.id.title)).setText(s);
                criteria.append(s).append(' ');

                s = bookCursorRow.getIsbn();
                ((TextView) findViewById(R.id.isbn)).setText(s);
                criteria.append(s).append(' ');
            }

            mCriteriaView.setText(criteria.toString().trim());
            doSearch();
        } else {
            findViewById(R.id.original_details).setVisibility(View.GONE);
        }

        findViewById(R.id.search).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                doSearch();
            }
        });
    }

    /**
     * Start the search results activity.
     */
    private void doSearch() {
        String criteria = mCriteriaView.getText().toString().trim();
        if (criteria.isEmpty()) {
            StandardDialogs.showUserMessage(this, R.string.please_enter_search_criteria);
            return;
        }

        Intent intent = new Intent(this, GoodreadsSearchResultsActivity.class);
        intent.putExtra(GoodreadsSearchResultsActivity.BKEY_SEARCH_CRITERIA, criteria);
        startActivity(intent);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
