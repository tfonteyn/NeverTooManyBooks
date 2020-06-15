/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityGoodreadsRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Allow the user to explain Goodreads and authorize this application to access their
 * Goodreads account.
 */
public class GoodreadsRegistrationActivity
        extends BaseActivity {

    private ActivityGoodreadsRegisterBinding mVb;

    private final TaskListener<Integer> mTaskListener = new TaskListener<Integer>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Integer> message) {
            final Context context = GoodreadsRegistrationActivity.this;
            if (message.result != null && message.result == GrStatus.FAILED_CREDENTIALS) {
                RequestAuthTask.prompt(context, mTaskListener);
            } else {
                Snackbar.make(mVb.btnAuthorize, GoodreadsHandler.digest(context, message),
                              Snackbar.LENGTH_LONG).show();
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            if (message.text != null) {
                Snackbar.make(mVb.btnAuthorize, message.text, Snackbar.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onSetContentView() {
        mVb = ActivityGoodreadsRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Goodreads Reg Link
        mVb.goodreadsUrl.setText(GoodreadsHandler.BASE_URL);
        mVb.goodreadsUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GoodreadsHandler.BASE_URL))));

        mVb.btnAuthorize.setOnClickListener(v -> {
            Snackbar.make(mVb.btnAuthorize, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            new RequestAuthTask(mTaskListener).execute();
        });

        final GoodreadsAuth grAuth = new GoodreadsAuth(this);
        if (grAuth.hasCredentials(this)) {
            mVb.lblDeleteCredentials.setVisibility(View.VISIBLE);
            mVb.btnDeleteCredentials.setVisibility(View.VISIBLE);
            mVb.btnDeleteCredentials.setOnClickListener(v -> GoodreadsAuth.clearAll(this));
        } else {
            mVb.lblDeleteCredentials.setVisibility(View.GONE);
            mVb.btnDeleteCredentials.setVisibility(View.GONE);
        }
    }
}
