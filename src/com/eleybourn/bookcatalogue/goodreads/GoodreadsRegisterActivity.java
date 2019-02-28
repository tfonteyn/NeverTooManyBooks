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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Activity to allow the user to authorize the application to access their Goodreads account and
 * to explain Goodreads.
 *
 * @author Philip Warner
 */
public class GoodreadsRegisterActivity
        extends BaseActivity
        implements ProgressDialogFragment.OnTaskFinishedListener {

    /**
     * Called by button click to start a non-UI-thread task to do the work.
     */
    public static void requestAuthorization(@NonNull final FragmentActivity context) {
        //noinspection unchecked
        ProgressDialogFragment<Integer> frag = (ProgressDialogFragment)
                context.getSupportFragmentManager().findFragmentByTag(RequestAuthTask.TAG);
        if (frag == null) {
            frag = ProgressDialogFragment.newInstance(R.string.progress_msg_connecting_to_web_site,
                                                      true, 0);
            frag.show(context.getSupportFragmentManager(), RequestAuthTask.TAG);
        }
        new RequestAuthTask(frag).execute();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.goodreads_register;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
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
                requestAuthorization(GoodreadsRegisterActivity.this);
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
    }

    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               final Object result) {
        UserMessage.showUserMessage(this, (Integer) result);
    }

    private static class RequestAuthTask
            extends AsyncTask<Void, Object, Integer> {

        private static final String TAG = RequestAuthTask.class.getSimpleName();
        /** Generic identifier. */
        private static final int M_TASK_ID = R.id.TASK_ID_GR_REQUEST_AUTH;
        /**
         * {@link #doInBackground} should catch exceptions, and set this field.
         * {@link #onPostExecute} can then check it.
         */
        @Nullable
        protected Exception mException;

        protected ProgressDialogFragment<Integer> mFragment;

        /**
         * Constructor.
         *
         * @param frag fragment to use for progress updates.
         */
        @UiThread
        RequestAuthTask(@NonNull final ProgressDialogFragment<Integer> frag) {
            mFragment = frag;
            mFragment.setTask(M_TASK_ID, this);
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            GoodreadsManager grMgr = new GoodreadsManager();
            // This next step can take several seconds....
            if (!grMgr.hasValidCredentials()) {
                try {
                    grMgr.requestAuthorization(mFragment.requireContext());
                } catch (IOException e) {
                    Logger.error(e);
                    return R.string.gr_access_error;
                } catch (AuthorizationException e) {
                    return R.string.error_authorization_failed;
                }
            } else {
                return R.string.gr_auth_access_already_auth;
            }
            if (isCancelled()) {
                // return value not used as onPostExecute is not called
                return R.string.progress_end_cancelled;
            }
            return R.string.info_authorized;
        }

        /**
         * If the task was cancelled (by the user cancelling the progress dialog) then
         * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
         *
         * @param result of the task
         */
        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Integer result) {
            mFragment.taskFinished(M_TASK_ID, mException == null, result);
        }
    }
}
