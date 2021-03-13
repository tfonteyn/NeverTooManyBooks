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
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;

/**
 * Load a Bitmap from a file, and populate the view.
 */
public class ImageViewLoader {

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final Executor mExecutor;

    private final int mMaxWidth;
    private final int mMaxHeight;

    /**
     * Constructor.
     *
     * @param executor  to use; should usually be {@link ASyncExecutor#MAIN}
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     */
    @UiThread
    public ImageViewLoader(@NonNull final Executor executor,
                           final int maxWidth,
                           final int maxHeight) {

        mHandler = new Handler(Looper.getMainLooper());

        mExecutor = executor;

        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
    }

    /**
     * @param imageView to populate
     * @param file      to load, must be valid
     * @param onSuccess (optional) Consumer to execute after successfully displaying the image
     */
    @UiThread
    public void loadAndDisplay(@NonNull final ImageView imageView,
                               @NonNull final File file,
                               @Nullable final Consumer<Bitmap> onSuccess) {

        imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);
        final WeakReference<ImageView> viewWeakReference = new WeakReference<>(imageView);

        mExecutor.execute(() -> {
            // do the actual background work.
            final Bitmap bitmap = ImageUtils.decodeFile(file, mMaxWidth, mMaxHeight);

            // all done; back to the UI thread.
            mHandler.post(() -> {
                // are we still associated with this view ? (remember: views are recycled)
                final ImageView view = viewWeakReference.get();
                if (view != null && this.equals(view.getTag(R.id.TAG_THUMBNAIL_TASK))) {
                    // clear the association
                    view.setTag(R.id.TAG_THUMBNAIL_TASK, null);
                    if (bitmap != null) {
                        ImageUtils.setImageView(view, mMaxWidth, mMaxHeight, bitmap, 0);
                        if (onSuccess != null) {
                            onSuccess.accept(bitmap);
                        }
                    } else {
                        // We only get here if we THOUGHT we had an image, but we failed to
                        // load/decode it. So use 'broken-image' icon and preserve the space
                        ImageUtils.setPlaceholder(view, mMaxWidth, mMaxHeight,
                                                  R.drawable.ic_baseline_broken_image_24, 0);
                    }
                }
            });
        });
    }
}
