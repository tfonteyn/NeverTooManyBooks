/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Activity to allow the user to authorize the application to access their
 * Goodreads account and to explain Goodreads.
 */
public class GoodreadsRegistrationActivity
        extends BaseActivity {

    private View mAuthButton;

    private final TaskListener<Integer> mTaskListener = new TaskListener<Integer>() {
        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Integer> message) {
            String msg = GoodreadsTasks.handleResult(GoodreadsRegistrationActivity.this,
                                                     message);
            if (msg != null) {
                showUserMessage(msg);
            } else {
                RequestAuthTask.needsRegistration(GoodreadsRegistrationActivity.this,
                                                  mTaskListener);
            }
        }

        @Override
        public void onTaskProgress(@NonNull final TaskProgressMessage message) {
            if (message.values != null && message.values.length > 0) {
                showUserMessage(message.values[0]);
            }
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_goodreads_register;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.goodreads);

        // Goodreads Reg Link
        TextView register = findViewById(R.id.goodreads_url);
        register.setText(GoodreadsManager.WEBSITE);
        register.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GoodreadsManager.WEBSITE))));

        // Auth button
        mAuthButton = findViewById(R.id.authorize);
        mAuthButton.setOnClickListener(v -> {
            UserMessage.show(mAuthButton, R.string.progress_msg_connecting);
            new RequestAuthTask(mTaskListener).execute();
        });

        // Forget credentials
        View blurb = findViewById(R.id.forget_blurb);
        View blurbButton = findViewById(R.id.btn_delete_credentials);
        if (GoodreadsManager.hasCredentials()) {
            blurb.setVisibility(View.VISIBLE);
            blurbButton.setVisibility(View.VISIBLE);
            blurbButton.setOnClickListener(v -> GoodreadsManager.resetCredentials());
        } else {
            blurb.setVisibility(View.GONE);
            blurbButton.setVisibility(View.GONE);
        }
    }

    /**
     * Allows the ViewModel to send us a message to display to the user.
     * <p>
     * If the type is {@code Integer} we assume it's a {@code StringRes}
     * else we do a toString() it.
     *
     * @param message to display, either a {@code Integer (StringRes)} or a {@code String}
     */
    private void showUserMessage(@Nullable final Object message) {
        if (message instanceof Integer) {
            UserMessage.show(mAuthButton, (int) message);
        } else if (message != null) {
            UserMessage.show(mAuthButton, message.toString());
        }
    }
}
