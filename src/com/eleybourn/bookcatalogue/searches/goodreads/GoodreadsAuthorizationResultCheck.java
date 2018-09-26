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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;

/**
 * Simple class to run in background and verify goodreads credentials then
 * display a notification based on the result.
 * 
 * This task is run as the last part of the goodreads auth process.
 * 
 * Runs in background because it can take several seconds.
 * 
 * @author Philip Warner
 */
class GoodreadsAuthorizationResultCheck extends GenericTask {
	private static final long serialVersionUID = -5502292652351148420L;

	GoodreadsAuthorizationResultCheck() {
		super(BookCatalogueApp.getResourceString(R.string.goodreads_auth_check));
	}

	@Override
	public boolean run(@NonNull QueueManager manager, @NonNull Context context) {
		GoodreadsManager grMgr = new GoodreadsManager();
		// Bring the app to the front using the launcher intent
		Intent intent = new Intent(context, StartupActivity.class);
		intent.setAction("android.intent.action.MAIN");
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
	    try {
		    grMgr.handleAuthentication();		    	
		    if (grMgr.hasValidCredentials())
				BookCatalogueApp.showNotification(R.id.NOTIFICATION,
						context.getString(R.string.authorized),
						context.getString(R.string.goodreads_auth_successful), intent);
			else
				BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
						context.getString(R.string.not_authorized),
						context.getString(R.string.goodreads_auth_failed), intent);
	    } catch (NotAuthorizedException e) {
	    	BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
						context.getString(R.string.not_authorized),
						context.getString(R.string.goodreads_auth_failed), intent);
	    } catch (Exception e) {
	    	BookCatalogueApp.showNotification(R.id.NOTIFICATION, 
						context.getString(R.string.not_authorized),
						context.getString(R.string.goodreads_auth_error)
						+ " " + context.getString(R.string.if_the_problem_persists), intent);
	    }

		return true;
	}

	@Override
	public int getCategory() {
		return BCQueueManager.CAT_GOODREADS_AUTH;
	}

}
