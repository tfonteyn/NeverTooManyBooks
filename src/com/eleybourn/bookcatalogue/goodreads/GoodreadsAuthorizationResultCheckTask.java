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

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.GoodreadsTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

/**
 * Simple class to run in background and verify Goodreads credentials then
 * display a notification based on the result.
 * <p>
 * This task is run as the last part of the Goodreads auth process.
 * <p>
 * Runs in background because it can take several seconds.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 * <p>
 * TOMF: convert to AsyncTask
 *
 * @author Philip Warner
 */
public class GoodreadsAuthorizationResultCheckTask
        extends GoodreadsTask {

    private static final long serialVersionUID = -5502292652351148420L;

    /**
     * Constructor sets the task description.
     */
    GoodreadsAuthorizationResultCheckTask() {
        super(BookCatalogueApp.getResString(R.string.gr_auth_check));
    }

    /**
     * @return <tt>false</tt> to requeue, <tt>true</tt> for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {

        GoodreadsManager grMgr = new GoodreadsManager();
        try {
            grMgr.handleAuthentication();

            if (grMgr.hasValidCredentials()) {
                BookCatalogueApp.showNotification(
                        context, R.string.info_authorized,
                        context.getString(R.string.gr_auth_successful));
            } else {
                BookCatalogueApp.showNotification(
                        context, R.string.info_not_authorized,
                        context.getString(R.string.error_authorization_failed,
                                          context.getString(R.string.goodreads)));
            }
        } catch (AuthorizationException e) {
            Logger.error(e);
            BookCatalogueApp.showNotification(
                    context, R.string.info_not_authorized, e.getLocalizedMessage());

        } catch (IOException e) {
            Logger.error(e);
            String msg = context.getString(R.string.gr_auth_error) + ' '
                    + context.getString(R.string.error_if_the_problem_persists);
            BookCatalogueApp.showNotification(context, R.string.info_not_authorized, msg);
        }
        return true;
    }

    @Override
    public int getCategory() {
        return Task.CAT_GOODREADS_AUTH_RESULT;
    }
}
