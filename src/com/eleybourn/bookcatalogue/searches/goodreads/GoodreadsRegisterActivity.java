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
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;

/**
 * Activity to allow the user to authorize the application to access their goodreads account and
 * to explain goodreads.
 *
 * @author Philip Warner
 */
public class GoodreadsRegisterActivity extends BaseActivity {

    /**
     * Called by button click to start a non-UI-thread task to do the work.
     */
    public static void requestAuthorizationInBackground(final @NonNull FragmentActivity activity) {
        FragmentTask task = new FragmentTask() {
            private int mMessage = 0;

            /**
             * Call the static method to start the web page; this can take a few seconds
             */
            @Override
            public void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @NonNull SimpleTaskContext taskContext) {
                mMessage = requestAuthorizationImmediate(activity);
            }

            /**
             * Display any error message
             */
            @Override
            public void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @Nullable Exception exception) {
                if (mMessage != 0)
                    fragment.showUserMessage(fragment.getString(mMessage));
            }

        };

        // Get the fragment to display task progress
        SimpleTaskQueueProgressDialogFragment.runTaskWithProgress(activity, R.string.progress_msg_connecting_to_web_site, task, true, 0);
    }

    /**
     * Static method to request authorization from goodreads.
     */
    private static int requestAuthorizationImmediate(final @NonNull Context context) {
        GoodreadsManager grMgr = new GoodreadsManager();
        // This next step can take several seconds....
        if (!grMgr.hasValidCredentials()) {
            try {
                grMgr.requestAuthorization(context);
            } catch (NetworkException e) {
                Logger.error(e);
                return R.string.gr_access_error;
            }
        } else {
            return R.string.gr_auth_access_already_auth;
        }
        return 0;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_register;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.goodreads);

        final Resources res = this.getResources();
        /* GR Reg Link */
        TextView register = findViewById(R.id.goodreads_url);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = res.getString(R.string.url_goodreads);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });

        /* Auth button */
        findViewById(R.id.authorize).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                requestAuthorizationInBackground(GoodreadsRegisterActivity.this);
            }
        });

        /* Forget credentials */
        boolean hasCred = GoodreadsManager.hasCredentials();
        View blurb = findViewById(R.id.forget_blurb);
        View blurb_button = findViewById(R.id.forget);
        if (hasCred) {
            blurb.setVisibility(View.VISIBLE);
            blurb_button.setVisibility(View.VISIBLE);
            blurb_button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    GoodreadsManager.forgetCredentials();
                }
            });
        } else {
            blurb.setVisibility(View.GONE);
            blurb_button.setVisibility(View.GONE);
        }
        Tracker.exitOnCreate(this);
    }
}
