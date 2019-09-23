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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Contains details about LibraryThing links and how to register for a developer key.
 * At a later data we could also include the user key for maintaining user-specific data.
 */
public class LibraryThingRegistrationActivity
        extends BaseActivity {

    private EditText mDevKeyView;

    private final TaskListener<Integer> mListener = new TaskListener<Integer>() {
        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Integer> message) {
            UserMessage.show(mDevKeyView, message.result);
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
                                              Uri.parse(LibraryThingManager.getBaseURL() + '/'))));

        // DevKey Link.
        findViewById(R.id.dev_key_url).setOnClickListener(
                v -> startActivity(new Intent(Intent.ACTION_VIEW,
                                              Uri.parse(LibraryThingManager.getBaseURL()
                                                        + "/services/keys.php"))));

        mDevKeyView = findViewById(R.id.dev_key);
        String key = SearchEngine.getPref().getString(LibraryThingManager.PREFS_DEV_KEY, "");
        mDevKeyView.setText(key);

        FloatingActionButton fabButton = findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_save);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> {
            String devKey = mDevKeyView.getText().toString().trim();
            SearchEngine.getPref()
                        .edit()
                        .putString(LibraryThingManager.PREFS_DEV_KEY, devKey)
                        .apply();

            if (!devKey.isEmpty()) {
                UserMessage.show(mDevKeyView, R.string.progress_msg_connecting);
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

            try {
                LibraryThingManager ltm = new LibraryThingManager();
                File tmpFile = ltm.getCoverImage("0451451783", SearchEngine.ImageSize.Small);
                if (tmpFile != null) {
                    tmpFile.deleteOnExit();
                    long length = tmpFile.length();
                    StorageUtils.deleteFile(tmpFile);

                    if (length > ImageUtils.MIN_IMAGE_FILE_SIZE) {
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
                Logger.error(this, e);
                mException = e;
                return R.string.error_unexpected_error;
            }
        }
    }
}
