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

package com.eleybourn.bookcatalogue.searches.librarything;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

/**
 * This is the Administration page. It contains details about LibraryThing links
 * and how to register for a developer key. At a later data we could also include
 * the user key for maintaining user-specific LibraryThing data.
 *
 * @author Philip Warner
 */
public class LibraryThingAdminActivity extends BaseActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_librarything;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.library_thing);

        /* LT Reg Link */
        findViewById(R.id.register_url).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(LibraryThingManager.getBaseURL() + "/"));
                startActivity(intent);
            }
        });

        /* DevKey Link */
        findViewById(R.id.dev_key_url).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(LibraryThingManager.getBaseURL() + "/services/keys.php"));
                startActivity(intent);
            }
        });

        EditText devKeyView = findViewById(R.id.dev_key);
        String key = BookCatalogueApp.getStringPreference(LibraryThingManager.PREFS_LT_DEV_KEY, "");
        devKeyView.setText(key);

        /* Save Button */
        findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText devKeyView = findViewById(R.id.dev_key);
                String devKey = devKeyView.getText().toString().trim();
                BookCatalogueApp.getSharedPreferences().edit()
                        .putString(LibraryThingManager.PREFS_LT_DEV_KEY, devKey)
                        .apply();

                if (!devKey.isEmpty()) {
                    FragmentTask task = new FragmentTask() {
                        /**
                         * Validate the key by getting a known cover
                         */
                        @Override
                        public void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @NonNull SimpleTaskContext taskContext) {
                            LibraryThingManager ltm = new LibraryThingManager();
                            File tmpFile = ltm.getCoverImage("0451451783", LibraryThingManager.ImageSizes.SMALL);
                            if (tmpFile != null) {
                                tmpFile.deleteOnExit();
                                long length = tmpFile.length();
                                if (length < 100) {
                                    // Queue a message
                                    fragment.showUserMessage(getString(R.string.lt_incorrect_key));
                                } else {
                                    // Queue a message
                                    fragment.showUserMessage(getString(R.string.lt_correct_key));
                                }
                                StorageUtils.deleteFile(tmpFile);
                            }
                        }

                        @Override
                        public void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @Nullable Exception exception) {
                        }
                    };

                    // Get the fragment to display task progress
                    SimpleTaskQueueProgressDialogFragment.runTaskWithProgress(LibraryThingAdminActivity.this, R.string.progress_msg_connecting_to_web_site, task, true, 0);

                }
            }
        });

        /* Reset Button */
        findViewById(R.id.reset_messages).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = BookCatalogueApp.getSharedPreferences();
                SharedPreferences.Editor ed = prefs.edit();
                for (String key : prefs.getAll().keySet()) {
                    if (key.toLowerCase().startsWith(LibraryThingManager.PREFS_LT_HIDE_ALERT.toLowerCase()))
                        ed.remove(key);
                }
                ed.apply();
            }
        });

        // hide soft keyboard at first
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }
}
