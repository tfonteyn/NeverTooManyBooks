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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Contains details about LibraryThing links and how to register for a developer key.
 * At a later data we could also include the user key for maintaining user-specific data.
 */
public class LibraryThingRegistrationActivity
        extends BaseActivity {

    private static final String TAG = "LibraryThingReg";

    private EditText mDevKeyView;

    private final TaskListener<Integer> mListener = new TaskListener<Integer>() {
        @Override
        public void onFinished(@NonNull final FinishMessage<Integer> message) {
            Snackbar.make(mDevKeyView, message.result, Snackbar.LENGTH_LONG).show();
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_librarything_register;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.library_thing);

        // LT Registration Link.
        findViewById(R.id.register_url).setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                                              Uri.parse(LibraryThingManager.BASE_URL + '/'))));

        // DevKey Link.
        findViewById(R.id.dev_key_url).setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                                              Uri.parse(LibraryThingManager.BASE_URL
                                                        + "/services/keys.php"))));

        mDevKeyView = findViewById(R.id.dev_key);
        String key = PreferenceManager.getDefaultSharedPreferences(this)
                                      .getString(LibraryThingManager.PREFS_DEV_KEY, "");
        mDevKeyView.setText(key);

        FloatingActionButton fabButton = findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_save);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> {
            String devKey = mDevKeyView.getText().toString().trim();
            PreferenceManager.getDefaultSharedPreferences(this)
                             .edit()
                             .putString(LibraryThingManager.PREFS_DEV_KEY, devKey)
                             .apply();

            if (!devKey.isEmpty()) {
                Snackbar.make(mDevKeyView, R.string.progress_msg_connecting,
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
        protected Integer doInBackground(final Void... params) {
            Thread.currentThread().setName("LT.ValidateKey");
            Context context = App.getAppContext();

            try {
                SearchEngine.CoverByIsbn ltm = new LibraryThingManager();
                File file = ltm.getCoverImage(context, "0451451783",
                                              SearchEngine.CoverByIsbn.ImageSize.Small);
                if (file != null) {
                    long fileLen = file.length();
                    StorageUtils.deleteFile(file);

                    if (fileLen > ImageUtils.MIN_IMAGE_FILE_SIZE) {
                        return R.string.lt_key_is_correct;
                    } else {
                        return R.string.lt_key_is_incorrect;
                    }
                }
                if (isCancelled()) {
                    // return value not used as onPostExecute is not called
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
