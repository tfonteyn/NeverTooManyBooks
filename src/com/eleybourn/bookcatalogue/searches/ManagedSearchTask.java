/*
 * @copyright 2011 Philip Warner
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

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.Series.SeriesDetails;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.ArrayList;

abstract public class ManagedSearchTask extends ManagedTask {
    protected static boolean mFetchThumbnail;
    protected String mAuthor;
    protected String mTitle;
    protected String mIsbn;

    /**
     * Accumulated book info.
     *
     * The Bundle will contain String or {@link StringList} based entries by default
     *
     * NEWKIND: if you add a new Search task that adds non-string based data, {@link SearchManager#accumulateData(int)} must be able to handle it.
     */
    @NonNull
    protected Bundle mBookData = new Bundle();

    /**
     * Constructor. Will search according to passed parameters. If an ISBN
     * is provided that will be used to the exclusion of all others.
     *
     * @param name    of this thread
     * @param manager TaskHandler implementation
     */
    protected ManagedSearchTask(final @NonNull String name,
                                final @NonNull TaskManager manager) {
        super(name, manager);
    }

    /**
     * @param isbn to search for
     */
    public void setIsbn(final @NonNull String isbn) {
        // trims might not be needed, but heck.
        mIsbn = isbn.trim();
    }

    /**
     * @param author to search for
     */
    public void setAuthor(final @NonNull String author) {
        // trims might not be needed, but heck.
        mAuthor = author.trim();
    }

    /**
     * @param title to search for
     */
    public void setTitle(final @NonNull String title) {
        // trims might not be needed, but heck.
        mTitle = title.trim();
    }

    /**
     * @param fetchThumbnail to search for
     */
    public void setFetchThumbnail(final boolean fetchThumbnail) {
        mFetchThumbnail = fetchThumbnail;
    }

    public abstract int getSearchId();

    @Override
    protected void onTaskFinish() {
        mTaskManager.sendTaskProgressMessage(this, R.string.done, 0);
    }

    /**
     * Look for a title; if present try to get a series name from it and clean the title
     */
    protected void checkForSeriesNameInTitle() {
        String bookTitle = mBookData.getString(UniqueId.KEY_TITLE);
        if (bookTitle != null) {
            SeriesDetails details = Series.findSeriesFromBookTitle(bookTitle);
            if (details != null && !details.name.isEmpty()) {
                ArrayList<Series> list = mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.add(new Series(details.name, details.position));
                // store Series back
                mBookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
                // remove series info from the book title.
                bookTitle = bookTitle.substring(0, details.startChar - 1).trim();
                // and store title back
                mBookData.putString(UniqueId.KEY_TITLE, bookTitle);
            }
        }
    }

    /**
     * Show an unexpected exception message after task finish
     */
    protected void setFinalError(final @StringRes int id, final @NonNull Exception e) {
        String s;
        try {
            s = e.getLocalizedMessage();
        } catch (Exception e2) {
            s = e2.getClass().getCanonicalName();
        }
        mFinalMessage = String.format(getString(R.string.error_search_exception), getString(id), s);
    }

    /**
     * Show a 'known' error after task finish, without the dreaded exception message
     */
    protected void setFinalError(final @StringRes int id, final @StringRes int error) {
       mFinalMessage = String.format(getString(R.string.error_search_exception), getString(id), error);
    }

    /**
     * Accessor, so when thread has finished, data can be retrieved.
     *
     * The Bundle will contain String or {@link StringList} based entries by default
     */
    @NonNull
    Bundle getBookData() {
        return mBookData;
    }
}
