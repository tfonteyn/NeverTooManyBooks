/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityCropimageBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

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
 * <p>
 * Note this code consistently uses a {@link ContentResolver} for input/output.
 */
public class CropImageActivity
        extends AppCompatActivity {

    /** Log tag. */
    private static final String TAG = "CropImageActivity";

    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_SOURCE = TAG + ":src";
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_DESTINATION = TAG + ":dst";

    /** used to calculate free space on Shared Storage, 100kb per picture is an overestimation. */
    private static final long ESTIMATED_PICTURE_SIZE = 100_000L;

    /** The destination URI where to write the result to. */
    private Uri destinationUri;

    /** View Binding. */
    private ActivityCropimageBinding vb;

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

        // make an educated guess how many pics we can store.
        try {
            final File coverDir = ServiceLocator.getInstance().getCoverStorage().getDir();
            if (FileUtils.getFreeSpace(coverDir) / ESTIMATED_PICTURE_SIZE < 1) {
                Snackbar.make(vb.coverImage0, R.string.error_storage_no_space_left,
                              Snackbar.LENGTH_LONG).show();
            }

        } catch (@NonNull final StorageException e) {
            LoggerFactory.getLogger().e(TAG, e);
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setMessage(ExMsg.map(this, e)
                                     .orElseGet(() -> getString(R.string.error_unknown)))
                    .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                    .create()
                    .show();

        } catch (@NonNull final IOException e) {
            LoggerFactory.getLogger().e(TAG, e);
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setMessage(R.string.error_storage_not_accessible)
                    .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                    .create()
                    .show();
            return;
        }

        final Bundle args = Objects.requireNonNull(getIntent().getExtras(),
                                                   "getIntent().getExtras()");

        final String srcPath = Objects.requireNonNull(args.getString(BKEY_SOURCE),
                                                      "srcPath");
        final Uri uri = Uri.fromFile(new File(srcPath));

        Bitmap bitmap = null;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(is);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "", e);
            }
        }

        if (bitmap != null) {
            final String dstPath = Objects.requireNonNull(args.getString(BKEY_DESTINATION),
                                                          "dstPath");
            destinationUri = Uri.fromFile(new File(dstPath));

            vb.coverImage0.initCropView(bitmap);

            // the FAB button saves the image, use 'back' to cancel.
            vb.fab.setOnClickListener(v -> onSave());

        } else {
            finish();
        }
    }

    private void onSave() {
        // prevent multiple saves (cropping the bitmap might take some time)
        vb.fab.setEnabled(false);

        Bitmap bitmap = vb.coverImage0.getCroppedBitmap();
        if (bitmap != null) {
            try (OutputStream os = getContentResolver().openOutputStream(destinationUri)) {
                if (os != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    setResult(Activity.RESULT_OK, new Intent().setData(destinationUri));
                }
            } catch (@NonNull final IOException e) {
                LoggerFactory.getLogger().e(TAG, e);
                bitmap = null;
            }
        }

        if (bitmap == null) {
            StandardDialogs.showError(this, R.string.progress_msg_saving_image);
            return;
        }
        finish();
    }

    public static class ResultContract
            extends ActivityResultContract<ResultContract.Input, Optional<Uri>> {

        private File dstFile;

        @CallSuper
        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final ResultContract.Input input) {

            dstFile = input.dstFile;
            FileUtils.delete(dstFile);

            return new Intent(context, CropImageActivity.class)
                    .putExtra(BKEY_SOURCE, input.srcFile.getAbsolutePath())
                    .putExtra(BKEY_DESTINATION, dstFile.getAbsolutePath());
        }

        @NonNull
        @Override
        public final Optional<Uri> parseResult(final int resultCode,
                                               @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                LoggerFactory.getLogger()
                              .d(TAG, "parseResult",
                                 "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                FileUtils.delete(dstFile);
                return Optional.empty();
            }
            final Uri uri = intent.getData();
            if (uri != null) {
                return Optional.of(uri);
            } else {
                return Optional.empty();
            }
        }

        static class Input {

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
