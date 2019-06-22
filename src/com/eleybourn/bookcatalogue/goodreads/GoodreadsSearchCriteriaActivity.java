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
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Activity to handle searching Goodreads for books that did not automatically convert.
 * These are typically books without an ISBN.
 * <p>
 * The search criteria is setup to contain the book author, title and ISBN.
 * The user can edit these, search Goodreads, and then review the results.
 * <p>
 * See @link GrSendBooksTaskBase where the use of this activity is commented out.
 *
 * @author Philip Warner
 */
public class GoodreadsSearchCriteriaActivity
        extends BaseActivity {

    /** Database access. */
    private DAO mDb;

    private TextView mCriteriaView;

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_search_criteria;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        mCriteriaView = findViewById(R.id.search_text);

        // Look for a book ID
        long bookId = getIntent().getLongExtra(DBDefinitions.KEY_ID, 0);
        // If we have a book, fill in criteria AND try a search
        if (bookId != 0) {
            // Initial value; try to build from passed book, if available.
            StringBuilder criteria = new StringBuilder();

            findViewById(R.id.original_details).setVisibility(View.VISIBLE);

            try (BookCursor cursor = mDb.fetchBookById(bookId)) {
                if (!cursor.moveToFirst()) {
                    UserMessage.showUserMessage(mCriteriaView,
                                                R.string.warning_book_no_longer_exists);
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                final BookCursorRow bookCursorRow = cursor.getCursorRow();
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

        findViewById(R.id.btn_search).setOnClickListener(v -> doSearch());
    }

    /**
     * Start the search results activity.
     */
    private void doSearch() {
        String criteria = mCriteriaView.getText().toString().trim();
        if (criteria.isEmpty()) {
            UserMessage.showUserMessage(mCriteriaView, R.string.warning_please_enter_search_criteria);
            return;
        }

        Intent intent = new Intent(this, GoodreadsSearchResultsActivity.class)
                .putExtra(GoodreadsSearchResultsActivity.BKEY_SEARCH_CRITERIA, criteria);
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
