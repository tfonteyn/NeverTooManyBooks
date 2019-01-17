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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment.FragmentTask;

import java.io.IOException;

/**
 * Activity to allow the user to authorize the application to access their Goodreads account and
 * to explain Goodreads.
 *
 * @author Philip Warner
 */
public class GoodreadsRegisterActivity
        extends BaseActivity {

    /**
     * Called by button click to start a non-UI-thread task to do the work.
     */
    public static void requestAuthorizationInBackground(@NonNull final FragmentActivity activity) {
        FragmentTask task = new TaskWithProgressDialogFragment.FragmentTaskAbstract() {
            @StringRes
            private int mMessage;

            /**
             * Call the static method to start the web page; this can take a few seconds.
             */
            @Override
            public void run(@NonNull final TaskWithProgressDialogFragment fragment,
                            @NonNull final SimpleTaskContext taskContext) {
                mMessage = requestAuthorizationImmediate(activity);
            }

            /**
             * Display any error message.
             */
            @Override
            public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                                 @Nullable final Exception e) {
                if (mMessage != 0) {
                    fragment.showUserMessage(fragment.getString(mMessage));
                }
            }

        };

        // Get the fragment to display task progress
        TaskWithProgressDialogFragment
                .newInstance(activity, R.string.progress_msg_connecting_to_web_site,
                             task, true, 0);
    }

    /**
     * Static method to request authorization from Goodreads.
     *
     * @return string resource id with failure message, 0 for success.
     */
    @StringRes
    private static int requestAuthorizationImmediate(@NonNull final Context context) {
        GoodreadsManager grMgr = new GoodreadsManager();
        // This next step can take several seconds....
        if (!grMgr.hasValidCredentials()) {
            try {
                grMgr.requestAuthorization(context);
            } catch (IOException e) {
                Logger.error(e);
                return R.string.gr_access_error;
            } catch (GoodreadsExceptions.NotAuthorizedException e) {
                return R.string.gr_auth_failed;
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
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.goodreads);

        // GR Reg Link
        TextView register = findViewById(R.id.goodreads_url);
        register.setText(GoodreadsManager.WEBSITE);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                String url = GoodreadsManager.WEBSITE;
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });

        // Auth button
        findViewById(R.id.authorize).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                requestAuthorizationInBackground(GoodreadsRegisterActivity.this);
            }
        });

        // Forget credentials
        View blurb = findViewById(R.id.forget_blurb);
        View blurbButton = findViewById(R.id.btn_forget_credentials);
        if (GoodreadsManager.hasCredentials()) {
            blurb.setVisibility(View.VISIBLE);
            blurbButton.setVisibility(View.VISIBLE);
            blurbButton.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(@NonNull final View v) {
                    GoodreadsManager.forgetCredentials();
                }
            });
        } else {
            blurb.setVisibility(View.GONE);
            blurbButton.setVisibility(View.GONE);
        }
        Tracker.exitOnCreate(this);
    }
}
