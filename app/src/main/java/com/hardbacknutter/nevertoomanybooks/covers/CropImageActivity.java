/*
 * @Copyright 2018-2024 HardBackNutter
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

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.nevertoomanybooks.covers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityCropimageBinding;

/**
 * The activity can crop specific region of interest from an image.
 * <p>
 * Must be configured in the manifest to use a fullscreen theme.
 * <pre>
 *     {@code
 *         <activity
 *             android:name=".cropper.CropImageActivity"
 *             android:theme="@style/Theme.App.FullScreen" />
 *      }
 * </pre>
 * <p>
 * Depends on / works in conjunction with {@link CropImageView}.
 */
public class CropImageActivity
        extends AppCompatActivity {

    /** Log tag. */
    private static final String TAG = "CropImageActivity";

    private static final String BKEY_SOURCE = TAG + ":src";
    private static final String BKEY_DESTINATION = TAG + ":dst";

    /** used to calculate free space on Shared Storage, 100kb per picture is an overestimation. */
    private static final long ESTIMATED_PICTURE_SIZE = 100_000L;

    /** View Binding. */
    private ActivityCropimageBinding vb;
    private String destinationPath;

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // uses full-screen theme, see manifest
        super.onCreate(savedInstanceState);

        vb = ActivityCropimageBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        try {
            final File coverDir = ServiceLocator.getInstance().getCoverStorage().getDir();
            // make an educated guess how many pics we can store.
            if (FileUtils.getFreeSpace(coverDir) / ESTIMATED_PICTURE_SIZE < 1) {
                // Shouldn't we 'finish()' the activity? i.e. handle like an exception?
                Snackbar.make(vb.coverImage0, R.string.error_insufficient_storage,
                              Snackbar.LENGTH_LONG).show();
            }

        } catch (@NonNull final CoverStorageException | IOException e) {
            // just log, do not display exception data
            LoggerFactory.getLogger().e(TAG, e);
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_storage_not_accessible)
                    .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                    .create()
                    .show();
            return;
        }

        final Bundle args = Objects.requireNonNull(getIntent().getExtras(),
                                                   "getIntent().getExtras()");

        final String srcPath = Objects.requireNonNull(args.getString(BKEY_SOURCE), BKEY_SOURCE);
        final Bitmap bitmap = getBitmap(srcPath);
        if (bitmap != null) {
            destinationPath = Objects.requireNonNull(args.getString(BKEY_DESTINATION),
                                                     BKEY_DESTINATION);

            vb.coverImage0.setInitialBitmap(bitmap);

            // the FAB button saves the image
            vb.fab.setOnClickListener(v -> onSave());
            // Back is cancel
            vb.bottomAppBar.setNavigationOnClickListener(v -> finish());
            // Reset/undo but stay here editing
            vb.bottomAppBar.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.MENU_UNDO) {
                    vb.coverImage0.resetBitmap();
                    return true;
                }
                return false;
            });
        } else {
            finish();
        }
    }

    @Nullable
    private Bitmap getBitmap(@NonNull final String srcPath) {
        Bitmap bitmap = null;
        try (InputStream is = new FileInputStream(srcPath)) {
            bitmap = BitmapFactory.decodeStream(is);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "getBitmap", e);
            }
        }
        return bitmap;
    }

    private void onSave() {
        // prevent multiple saves (cropping the bitmap might take some time)
        vb.fab.setEnabled(false);

        @Nullable
        Bitmap bitmap = vb.coverImage0.getCroppedBitmap();
        if (bitmap != null) {
            try {
                final File destination = new File(destinationPath);
                ServiceLocator.getInstance().getCoverStorage().persist(bitmap, destination);

                setResult(Activity.RESULT_OK,
                          new Intent().putExtra(BKEY_DESTINATION, destinationPath));
                finish();

            } catch (@NonNull final IOException | CoverStorageException e) {
                LoggerFactory.getLogger().e(TAG, e);
                bitmap = null;
            }
        }

        if (bitmap == null) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.action_save)
                    .setMessage(R.string.error_storage_not_writable)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }

    public static class ResultContract
            extends ActivityResultContract<ResultContract.Input, Optional<File>> {

        @CallSuper
        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final ResultContract.Input input) {

            // do NOT delete the destination file in case source and destination was the same file

            return new Intent(context, CropImageActivity.class)
                    .putExtra(BKEY_SOURCE, input.srcFile.getAbsolutePath())
                    .putExtra(BKEY_DESTINATION, input.dstFile.getAbsolutePath());
        }

        @NonNull
        @Override
        public final Optional<File> parseResult(final int resultCode,
                                                @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                LoggerFactory.getLogger().d(TAG, "parseResult",
                                            "resultCode=" + resultCode, "intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return Optional.empty();
            }
            final String filename = intent.getStringExtra(BKEY_DESTINATION);
            if (filename != null && !filename.isEmpty()) {
                return Optional.of(new File(filename));
            } else {
                return Optional.empty();
            }
        }

        public static class Input {

            @NonNull
            final File srcFile;
            @NonNull
            final File dstFile;

            Input(@NonNull final File srcFile,
                  @NonNull final File dstFile) {
                this.srcFile = srcFile;
                this.dstFile = dstFile;
            }
        }
    }
}
