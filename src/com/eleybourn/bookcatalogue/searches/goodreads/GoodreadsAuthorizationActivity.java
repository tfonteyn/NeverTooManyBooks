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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;

/**
 * Trivial Activity to handle the callback URI; while using a broadcast receiver would be nicer, 
 * it does not seem to be possible to get them to work from web browser callbacks. So, we just
 * do the necessary processing here and exit.
 * 
 * TODO: This activity should bring the app to the foreground.
 * 
 * @author Philip Warner
 */
public class GoodreadsAuthorizationActivity extends BaseActivity {
	@Override
	@CallSuper
	public void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Get the payload and make sure it is what we expect
		Intent intent = this.getIntent();
		android.net.Uri uri = intent.getData();
		if (uri != null) {// && uri.toString().startsWith("BookCatalogue")) {  
			// GoodReads does not set the verifier...but we may as well check for it.
			// The verifier was added in API version 1.0A, and GoodReads seems to 
			// implement 1.0.

		    //String verifier = uri.getQueryParameter("oauth_verifier");

		    // Handle the auth response by passing it off to a background task to check.
		    GoodreadsAuthorizationResultCheck task = new GoodreadsAuthorizationResultCheck();
		    QueueManager.getQueueManager().enqueueTask(task, BCQueueManager.QUEUE_SMALL_JOBS);
		}

		// Bring the main app task back to the top
		Intent bcTop = new Intent(this, StartupActivity.class);
		bcTop.setAction("android.intent.action.MAIN");
		bcTop.addCategory(Intent.CATEGORY_LAUNCHER);

		startActivity(bcTop);

		this.finish();
	} 

}
