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

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 /**
 *  Goodreads ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}
 *
 * @author Philip Warner
 */
public class SearchGoodreadsTask extends ManagedSearchTask {

    public SearchGoodreadsTask(final @NonNull String name,
                               final @NonNull TaskManager manager) {
        super(name, manager);
    }

    /**
     * Get the global ID for the Goodreads search manager
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_GOODREADS;
    }

    @Override
    protected void runTask() {
        final @StringRes int R_ID_SEARCHING = R.string.searching_goodreads;
        doProgress(getString(R_ID_SEARCHING), 0);

        GoodreadsManager grMgr = new GoodreadsManager();
        if (!grMgr.isAvailable()) {
            return;
        }

        try {
            // manager checks the arguments
            mBookData = grMgr.search(mIsbn, mAuthor, mTitle);

        } catch (BookNotFoundException ignore) {
            // ignore, to bad.
        } catch (GoodreadsManager.Exceptions.NotAuthorizedException |
                OAuthMessageSignerException | OAuthExpectationFailedException | OAuthCommunicationException
                e) {
            // not actually sure if any of these will ever surface here?
            // but for completeness/curiosity/paranoia sake
            // see if we can capture any in our logs and fix anything obvious
            Logger.error(e);
            showError(R_ID_SEARCHING, R.string.gr_auth_failed);

        } catch (java.net.SocketTimeoutException e) {
            showError(R_ID_SEARCHING, R.string.error_network_timeout);

        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            showError(R_ID_SEARCHING, R.string.error_search_configuration);

        } catch (GoodreadsManager.Exceptions.NetworkException | IOException e) { // added NetworkException
            showError(R_ID_SEARCHING, R.string.error_search_failed);
            Logger.error(e);

        } catch (Exception e) {
            Logger.error(e);
            showException(R_ID_SEARCHING, e);
        }
    }
}
