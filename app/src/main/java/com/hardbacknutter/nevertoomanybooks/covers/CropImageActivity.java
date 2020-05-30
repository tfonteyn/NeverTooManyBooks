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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityCropimageBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

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
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "CropImageActivity";

    public static final String BKEY_SOURCE = TAG + ":src";
    public static final String BKEY_DESTINATION = TAG + ":dst";

    /** used to calculate free space on Shared Storage, 100kb per picture is an overestimation. */
    private static final long ESTIMATED_PICTURE_SIZE = 100_000L;

    /** The source bitmap. */
    @Nullable
    private Bitmap mBitmap;
    /** The destination URI where to write the result to. */
    private Uri mDestinationUri;

    /** Whether the "save" button is already clicked. */
    private boolean mIsSaving;

    /** View binding. */
    private ActivityCropimageBinding mVb;

    @Override
    protected void onSetContentView() {
        mVb = ActivityCropimageBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if shared storage is mounted and accessible.
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Snackbar.make(mVb.coverImage0, R.string.error_storage_not_accessible,
                          Snackbar.LENGTH_LONG).show();
        } else {
            final long freeSpace = AppDir.Root.getFreeSpace(this);
            // make an educated guess how many pics we can store.
            if (freeSpace >= 0 && freeSpace / ESTIMATED_PICTURE_SIZE < 1) {
                Snackbar.make(mVb.coverImage0, R.string.error_storage_no_space_left,
                              Snackbar.LENGTH_LONG).show();
            }
        }

        final Bundle args = getIntent().getExtras();
        Objects.requireNonNull(args);

        final String srcPath = Objects.requireNonNull(args.getString(BKEY_SOURCE));
        final Uri uri = Uri.fromFile(new File(srcPath));
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            mBitmap = BitmapFactory.decodeStream(is);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "", e);
            }
        }

        if (mBitmap != null) {
            final String dstPath = Objects.requireNonNull(args.getString(BKEY_DESTINATION));
            mDestinationUri = Uri.fromFile(new File(dstPath));

            // the FAB button saves the image, use 'back' to cancel.
            mVb.fab.setOnClickListener(v -> onSave());

            // Flag indicating if by default the crop rectangle should be the whole image.
            final boolean wholeImage = PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getBoolean(Prefs.pk_image_cropper_frame_whole, false);

            mVb.coverImage0.initCropOverlay(this, mBitmap, wholeImage);

        } else {
            finish();
        }
    }

    private void onSave() {
        if (mIsSaving) {
            return;
        }
        final Rect cropRect = mVb.coverImage0.getCropRect();
        if (cropRect == null) {
            return;
        }
        // prevent multiple saves.
        mIsSaving = true;
        // Tell the view to stop responding to user touches.
        mVb.coverImage0.setNoTouching(true);

        final int width = cropRect.width();
        final int height = cropRect.height();

        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Rect dstRect = new Rect(0, 0, width, height);
        //noinspection ConstantConditions
        canvas.drawBitmap(mBitmap, cropRect, dstRect, null);

        try (OutputStream os = getContentResolver().openOutputStream(mDestinationUri)) {
            if (os != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                final Intent data = new Intent().setData(mDestinationUri);
                setResult(Activity.RESULT_OK, data);
            }
        } catch (@NonNull final IOException e) {
            Logger.error(this, TAG, e);
        }
        finish();
    }
}
