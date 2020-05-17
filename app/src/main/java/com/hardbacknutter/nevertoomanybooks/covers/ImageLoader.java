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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Load a Bitmap from a file, and populate the view.
 */
public class ImageLoader
        extends AsyncTask<Void, Void, Bitmap> {

    private static final String TAG = "ImageLoader";
    final int mMaxWidth;
    final int mMaxHeight;
    @NonNull
    private final WeakReference<ImageView> mImageView;
    @NonNull
    private final File mFile;
    @Nullable
    private final Runnable mOnSuccess;

    /**
     * Constructor.
     *
     * @param imageView to populate
     * @param file      to load, must be valid
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param onSuccess (optional) Runnable to execute after successfully displaying the image
     */
    public ImageLoader(@NonNull final ImageView imageView,
                       @NonNull final File file,
                       final int maxWidth,
                       final int maxHeight,
                       @Nullable final Runnable onSuccess) {
        // see onPostExecute
        imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);
        mImageView = new WeakReference<>(imageView);
        mFile = file;
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
        mOnSuccess = onSuccess;
    }

    @Override
    @Nullable
    protected Bitmap doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);

        // scales the image as it's being read.
        return ImageUtils.decodeFile(mFile, mMaxWidth, mMaxHeight);
    }

    @Override
    protected void onPostExecute(@Nullable final Bitmap bitmap) {
        final ImageView imageView = mImageView.get();
        // are we still associated with this view ? (remember: views are recycled)
        if (imageView != null && this.equals(imageView.getTag(R.id.TAG_THUMBNAIL_TASK))) {
            imageView.setTag(R.id.TAG_THUMBNAIL_TASK, null);
            if (bitmap != null) {
                ImageUtils.setImageView(imageView, mMaxWidth, mMaxHeight, bitmap, 0);
                if (mOnSuccess != null) {
                    mOnSuccess.run();
                }
            } else {
                ImageUtils.setPlaceholder(imageView, R.drawable.ic_broken_image, 0, mMaxHeight);
            }
        }
    }
}
