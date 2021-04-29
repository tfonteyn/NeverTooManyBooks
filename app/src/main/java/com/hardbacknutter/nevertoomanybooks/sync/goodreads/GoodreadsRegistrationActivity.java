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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityGoodreadsRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Allow the user to explain Goodreads and authorize this application to access their
 * Goodreads account.
 */
public class GoodreadsRegistrationActivity
        extends BaseActivity {

    /** View Binding. */
    private ActivityGoodreadsRegisterBinding mVb;

    /** Goodreads authorization task. */
    private GoodreadsAuthenticationViewModel mVm;

    @Override
    protected void onSetContentView() {
        mVb = ActivityGoodreadsRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(GoodreadsAuthenticationViewModel.class);
        mVm.onProgress().observe(this, this::onProgress);
        mVm.onCancelled().observe(this, this::onCancelled);
        mVm.onFailure().observe(this, this::onGrFailure);
        mVm.onFinished().observe(this, this::onGrFinished);

        // Goodreads Reg Link
        mVb.goodreadsUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(GoodreadsManager.BASE_URL))));

        mVb.btnAuthorize.setOnClickListener(v -> {
            Snackbar.make(mVb.btnAuthorize, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mVm.authenticate();
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
            final Exception e = message.getResult();
            final String msg = ExMsg
                    .map(this, e)
                    .orElse(getString(R.string.error_network_site_access_failed,
                                      getString(R.string.site_goodreads)));
            Snackbar.make(mVb.getRoot(), msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        if (message.isNewEvent()) {
            final GrStatus result = message.requireResult();
            if (result.getStatus() == GrStatus.CREDENTIALS_MISSING) {
                mVm.promptForAuthentication(this);
            } else {
                Snackbar.make(mVb.getRoot(), result.getMessage(this),
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }
}
