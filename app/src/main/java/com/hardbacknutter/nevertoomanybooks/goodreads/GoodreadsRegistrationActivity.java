/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityGoodreadsRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

/**
 * Allow the user to explain Goodreads and authorize this application to access their
 * Goodreads account.
 */
public class GoodreadsRegistrationActivity
        extends BaseActivity {

    /** View Binding. */
    private ActivityGoodreadsRegisterBinding mVb;

    /** Goodreads authorization task. */
    private GrAuthTask mGrAuthTask;

    @Override
    protected void onSetContentView() {
        mVb = ActivityGoodreadsRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGrAuthTask = new ViewModelProvider(this).get(GrAuthTask.class);
        mGrAuthTask.onProgressUpdate().observe(this, this::onProgress);
        mGrAuthTask.onCancelled().observe(this, this::onCancelled);
        mGrAuthTask.onFailure().observe(this, this::onGrFailure);
        mGrAuthTask.onFinished().observe(this, this::onGrFinished);

        // Goodreads Reg Link
        mVb.goodreadsUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GoodreadsManager.BASE_URL))));

        mVb.btnAuthorize.setOnClickListener(v -> {
            Snackbar.make(mVb.btnAuthorize, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mGrAuthTask.start();
        });

        final GoodreadsAuth grAuth = new GoodreadsAuth();
        if (grAuth.hasCredentials()) {
            mVb.lblDeleteCredentials.setVisibility(View.VISIBLE);
            mVb.btnDeleteCredentials.setVisibility(View.VISIBLE);
            mVb.btnDeleteCredentials.setOnClickListener(v -> GoodreadsAuth.clearAll());
        } else {
            mVb.lblDeleteCredentials.setVisibility(View.GONE);
            mVb.btnDeleteCredentials.setVisibility(View.GONE);
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.text != null) {
            Snackbar.make(mVb.getRoot(), message.text, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onCancelled(@NonNull final LiveDataEvent message) {
        if (message.isNewEvent()) {
            Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        if (message.isNewEvent()) {
            Snackbar.make(mVb.getRoot(), GrStatus.getMessage(this, message.result),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
                mGrAuthTask.prompt(this);
            } else {
                Snackbar.make(mVb.getRoot(), message.result.getMessage(this),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }
}
