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
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.Series.SeriesDetails;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ManagedTask;
import com.eleybourn.bookcatalogue.utils.TaskManager;

import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_SERIES_DETAILS;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_TITLE;

abstract public class SearchThread extends ManagedTask {
    protected static boolean mFetchThumbnail;
    protected final String mAuthor;
    protected final String mTitle;
    protected final String mIsbn;
    // Accumulated book info.
    protected Bundle mBookData = new Bundle();

    /**
     * Constructor. Will search according to passed parameters. If an ISBN
     * is provided that will be used to the exclusion of all others.
     *
     * @param manager TaskHandler implementation
     * @param author  Author to search for
     * @param title   Title to search for
     * @param isbn    ISBN to search for.
     */
    protected SearchThread(TaskManager manager, String author, String title, String isbn, boolean fetchThumbnail) {
        super(manager);
        mAuthor = author;
        mTitle = title;
        mIsbn = isbn;
        mFetchThumbnail = fetchThumbnail;

        //mBookData.putString(DatabaseDefinitions.KEY_AUTHOR_FORMATTED, mAuthor);
        //mBookData.putString(DatabaseDefinitions.KEY_TITLE, mTitle);
        //mBookData.putString(DatabaseDefinitions.KEY_ISBN, mIsbn);
        //getMessageSwitch().addListener(getSenderId(), taskHandler, false);
    }

    public abstract int getSearchId();

    @Override
    protected void onThreadFinish() {
        doProgress(getString(R.string.done), 0);
    }

    /**
     * Look in the data for a title, if present try to get a series name from it.
     * In any case, clear the title (and save if none saved already) so that the
     * next lookup will overwrite with a possibly new title.
     */
    protected void checkForSeriesName() {
        try {
            if (mBookData.containsKey(KEY_TITLE)) {
                String thisTitle = mBookData.getString(KEY_TITLE);
                if (thisTitle != null) {
                    SeriesDetails details = Series.findSeries(thisTitle);
                    if (details != null && !details.name.isEmpty()) {
                        ArrayList<Series> sl;
                        if (mBookData.containsKey(KEY_SERIES_DETAILS)) {
                            sl = ArrayUtils.getSeriesUtils().decodeList('|', mBookData.getString(KEY_SERIES_DETAILS), false);
                        } else {
                            sl = new ArrayList<>();
                        }
                        sl.add(new Series(details.name, details.position));
                        mBookData.putString(KEY_SERIES_DETAILS, ArrayUtils.getSeriesUtils().encodeList('|', sl));
                        mBookData.putString(KEY_TITLE, thisTitle.substring(0, details.startChar - 1));
                    }
                }
            }
        } catch (Exception ignore) {
            Logger.logError(ignore);
        }
    }

    protected void showException(int id, Exception e) {
        String s;
        try {
            s = e.getMessage();
        } catch (Exception e2) {
            s = e2.getClass().getCanonicalName();
        }
        String msg = String.format(getString(R.string.search_exception), getString(id), s);
        doToast(msg);
    }

    /**
     * Accessor, so when thread has finished, data can be retrieved.
     */
    public Bundle getBookData() {
        return mBookData;
    }
}
