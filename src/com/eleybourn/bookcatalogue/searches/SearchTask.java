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
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.Series.SeriesDetails;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

abstract public class SearchTask extends ManagedTask {
    protected static boolean mFetchThumbnail;
    protected String mAuthor;
    protected String mTitle;
    protected String mIsbn;

    /** Accumulated book info. */
    @NonNull
    protected Bundle mBookData = new Bundle();

    /**
     * Constructor. Will search according to passed parameters. If an ISBN
     * is provided that will be used to the exclusion of all others.
     *
     * @param name    of this thread
     * @param manager TaskHandler implementation
     */
    protected SearchTask(final @NonNull String name,
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
        doProgress(getString(R.string.done), 0);
    }

    /**
     * Look in the data for a title, if present try to get a series name from it.
     * In any case, clean the title (and save if none saved already) so that the
     * next lookup will overwrite with a possibly new title.
     */
    protected void checkForSeriesName() {
        if (mBookData.containsKey(UniqueId.KEY_TITLE)) {
            String thisTitle = mBookData.getString(UniqueId.KEY_TITLE);
            if (thisTitle != null) {
                SeriesDetails details = Series.findSeriesFromBookTitle(thisTitle);
                if (details != null && !details.name.isEmpty()) {
                    List<Series> list;
                    if (mBookData.containsKey(UniqueId.BKEY_SERIES_STRING_LIST)) {
                        list = ArrayUtils.getSeriesUtils().decodeList(mBookData.getString(UniqueId.BKEY_SERIES_STRING_LIST), false);
                    } else {
                        list = new ArrayList<>();
                    }
                    list.add(new Series(details.name, details.position));
                    mBookData.putString(UniqueId.BKEY_SERIES_STRING_LIST, ArrayUtils.getSeriesUtils().encodeList(list));
                    mBookData.putString(UniqueId.KEY_TITLE, thisTitle.substring(0, details.startChar - 1).trim());
                }
            }
        }
    }

    /**
     * Show an unexpected exception message
     */
    protected void showException(final @StringRes int id, final @NonNull Exception e) {
        String s;
        try {
            s = e.getLocalizedMessage();
        } catch (Exception e2) {
            s = e2.getClass().getCanonicalName();
        }
        String msg = String.format(getString(R.string.error_search_exception), getString(id), s);
        showUserMessage(msg);
    }

    /**
     * Show a 'known' error, without the dreaded exception message
     */
    protected void showError(final @StringRes int id, final @StringRes int error) {
        String msg = String.format(getString(R.string.error_search_exception), getString(id), error);
        showUserMessage(msg);
    }

    /**
     * Accessor, so when thread has finished, data can be retrieved.
     */
    @NonNull
    Bundle getBookData() {
        return mBookData;
    }
}
