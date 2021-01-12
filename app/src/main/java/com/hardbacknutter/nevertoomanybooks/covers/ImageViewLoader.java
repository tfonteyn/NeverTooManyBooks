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
public class ImageViewLoader
        extends AsyncTask<Void, Void, Bitmap> {

    /** Log tag. */
    private static final String TAG = "ImageViewLoader";
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
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param file      to load, must be valid
     * @param onSuccess (optional) Runnable to execute after successfully displaying the image
     */
    public ImageViewLoader(@NonNull final ImageView imageView,
                           final int maxWidth,
                           final int maxHeight,
                           @NonNull final File file,
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
            // clear the association
            imageView.setTag(R.id.TAG_THUMBNAIL_TASK, null);
            if (bitmap != null) {
                ImageUtils.setImageView(imageView, mMaxWidth, mMaxHeight, bitmap, 0);
                if (mOnSuccess != null) {
                    mOnSuccess.run();
                }
            } else {
                // We only get here if we THOUGHT we had an image, but we failed to
                // load/decode it. So use 'broken-image' icon and preserve the space
                ImageUtils.setPlaceholder(imageView, mMaxWidth, mMaxHeight,
                                          R.drawable.ic_broken_image, 0);
            }
        }
    }
}
