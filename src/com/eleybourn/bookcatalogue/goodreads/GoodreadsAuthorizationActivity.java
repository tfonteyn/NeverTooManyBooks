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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;

/**
 * Trivial Activity to handle the callback URI; while using a broadcast receiver would be nicer,
 * it does not seem to be possible to get them to work from web browser callbacks. So, we just
 * do the necessary processing here and exit.
 *
 * @author Philip Warner
 */
public class GoodreadsAuthorizationActivity
        extends BaseActivity {

    /**
     * AUTHORIZATION_CALLBACK is the call back Intent URL.
     * Must match the intent filter(s) setup in the manifest with Intent.ACTION_VIEW
     * for this activity.
     * The scheme is hardcoded to avoid confusion between java and android package names.
     * <p>
     * scheme: com.eleybourn.bookcatalogue
     * host: goodreadsauth
     *
     * <pre>
     *     {@code
     *      <activity
     *          android:name=".goodreads.GoodreadsAuthorizationActivity"
     *          android:launchMode="singleInstance">
     *          <intent-filter>
     *              <action android:name="android.intent.action.VIEW" />
     *
     *              <category android:name="android.intent.category.DEFAULT" />
     *              <category android:name="android.intent.category.BROWSABLE" />
     *              <data
     *                  android:host="goodreadsauth"
     *                  android:scheme="com.eleybourn.bookcatalogue" />
     *          </intent-filter>
     *      </activity>
     *      }
     * </pre>
     */
    public static final String AUTHORIZATION_CALLBACK =
            "com.eleybourn.bookcatalogue://goodreadsauth";

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the payload and make sure it is what we expect
        Uri uri = getIntent().getData();

        //if (uri != null) && uri.toString().startsWith("BookCatalogue")) {
        if (uri != null) {
            // Goodreads does not set the verifier...but we may as well check for it.
            // The verifier was added in API version 1.0A, and Goodreads seems to
            // implement 1.0.
            //String verifier = uri.getQueryParameter("oauth_verifier");

            // Handle the auth response by passing it off to a background task to check.
            GoodreadsAuthorizationResultCheckTask task =
                    new GoodreadsAuthorizationResultCheckTask(this);
            QueueManager.getQueueManager().enqueueTask(task, QueueManager.Q_SMALL_JOBS);
        }

        // Bring the main app task back to the top
        Intent intent = new Intent(this, StartupActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        startActivity(intent);
        finish();
    }
}
