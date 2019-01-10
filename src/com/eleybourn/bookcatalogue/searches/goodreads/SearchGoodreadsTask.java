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

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 * /**
 * Goodreads ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}.
 *
 * @author Philip Warner
 */
public class SearchGoodreadsTask
        extends ManagedSearchTask {

    /** progress title. */
    @StringRes
    private static final int R_ID_SEARCHING = R.string.searching_goodreads;

    public SearchGoodreadsTask(@NonNull final String name,
                               @NonNull final TaskManager manager) {
        super(name, manager);
    }

    /**
     * @return the global ID for the Goodreads search manager
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_GOODREADS;
    }

    @Override
    protected void runTask() {

        mTaskManager.sendTaskProgressMessage(this, R_ID_SEARCHING, 0);

        GoodreadsManager grMgr = new GoodreadsManager();
        if (!grMgr.isAvailable()) {
            return;
        }

        try {
            // manager checks the arguments
            mBookData = grMgr.search(mIsbn, mAuthor, mTitle);

        } catch (BookNotFoundException ignore) {
            // ignore, to bad.
        } catch (GoodreadsExceptions.NotAuthorizedException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.gr_auth_failed);

        } catch (java.net.SocketTimeoutException e) {
            Logger.info(this, e.getLocalizedMessage());
            setFinalError(R_ID_SEARCHING, R.string.error_network_timeout);
        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_configuration);
        } catch (IOException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_failed);
        } catch (RuntimeException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, e);
        }
    }
}
