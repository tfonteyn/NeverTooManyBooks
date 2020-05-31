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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityLibrarythingRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Contains details about LibraryThing links and how to register for a developer key.
 * At a later data we could also include the user key for maintaining user-specific data.
 */
public class LibraryThingRegistrationActivity
        extends BaseActivity {

    /** View Binding. */
    private ActivityLibrarythingRegisterBinding mVb;

    private final TaskListener<Integer> mListener = new TaskListener<Integer>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Integer> message) {
            final int stringId =
                    message.result != null ? message.result : R.string.progress_end_cancelled;
            Snackbar.make(mVb.devKey, stringId, Snackbar.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onSetContentView() {
        mVb = ActivityLibrarythingRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.site_library_thing);

        mVb.registerUrl.setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                                              Uri.parse(LibraryThingSearchEngine.BASE_URL + '/'))));

        mVb.devKeyUrl.setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                                              Uri.parse(LibraryThingSearchEngine.BASE_URL
                                                        + "/services/keys.php"))));

        final String key = PreferenceManager.getDefaultSharedPreferences(this)
                                            .getString(LibraryThingSearchEngine.PREFS_DEV_KEY, "");
        mVb.devKey.setText(key);

        // Saves first, then TESTS the key.
        mVb.fab.setOnClickListener(v -> {
            //noinspection ConstantConditions
            String devKey = mVb.devKey.getText().toString().trim();
            PreferenceManager.getDefaultSharedPreferences(this)
                             .edit()
                             .putString(LibraryThingSearchEngine.PREFS_DEV_KEY, devKey)
                             .apply();

            if (!devKey.isEmpty()) {
                Snackbar.make(mVb.devKey, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                new ValidateKey(mListener).execute();
            }
        });
    }

    /**
     * Request a known valid ISBN from LT to see if the user key is valid.
     */
    private static class ValidateKey
            extends TaskBase<Integer> {

        /** Log tag. */
        private static final String TAG = "LT.ValidateKey";

        /**
         * Constructor.
         *
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        private ValidateKey(@NonNull final TaskListener<Integer> taskListener) {
            super(R.id.TASK_ID_LT_VALIDATE_KEY, taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            try {
                final SearchEngine.CoverByIsbn ltm = new LibraryThingSearchEngine();
                final String fileSpec = ltm.searchCoverImageByIsbn(context, "0451451783", 0,
                                                                   ImageFileInfo.Size.Small);
                if (fileSpec != null) {
                    if (ImageUtils.isFileGood(new File(fileSpec))) {
                        return R.string.lt_key_is_correct;
                    } else {
                        return R.string.lt_key_is_incorrect;
                    }
                }

                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }
                return R.string.warning_cover_not_found;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return R.string.error_unexpected_error;
            }
        }
    }
}
