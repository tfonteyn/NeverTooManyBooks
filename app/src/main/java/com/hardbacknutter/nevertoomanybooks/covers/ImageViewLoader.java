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
import android.os.Process;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Load a Bitmap from a file, and populate the view.
 */
public class ImageViewLoader {

    /** Log tag. */
    private static final String TAG = "ImageViewLoader";

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final Executor mExecutor;

    private final int mWidth;
    private final int mHeight;

    @NonNull
    private final ImageView.ScaleType mScaleType;

    private final boolean mEnforceMaxSize;

    @UiThread
    public ImageViewLoader(@NonNull final Executor executor,
                           final int width,
                           final int height) {
        this(executor, ImageView.ScaleType.FIT_START, width, height, true);
    }

    /**
     * Constructor.
     *
     * @param executor       to use
     * @param scaleType      to use for images (ignored for placeholders)
     * @param width          Desired width of the image
     * @param height         Desired height of the image
     * @param enforceMaxSize if {@code true}, then use the desired size as the maximum size
     */
    @UiThread
    public ImageViewLoader(@NonNull final Executor executor,
                           @NonNull final ImageView.ScaleType scaleType,
                           final int width,
                           final int height,
                           final boolean enforceMaxSize) {

        mHandler = new Handler(Looper.getMainLooper());

        mExecutor = executor;

        mScaleType = scaleType;
        mWidth = width;
        mHeight = height;
        mEnforceMaxSize = enforceMaxSize;
    }

    /**
     * Load a placeholder drawable in the given view.
     *
     * @param imageView View to populate
     * @param drawable  drawable to use
     */
    @UiThread
    public void placeholder(@NonNull final ImageView imageView,
                            @DrawableRes final int drawable) {
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.width = mWidth;
        lp.height = mHeight;
        imageView.setLayoutParams(lp);

        imageView.setPadding(0, 0, 0, 0);
        imageView.setAdjustViewBounds(true);
        imageView.setMaxHeight(Integer.MAX_VALUE);
        imageView.setMaxWidth(Integer.MAX_VALUE);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);
    }

    /**
     * Load the image bitmap into the given view.
     * The image is scaled to fit the box exactly preserving the aspect ratio.
     *
     * @param imageView View to populate
     * @param bitmap    The Bitmap of the image
     */
    @UiThread
    public void fromBitmap(@NonNull final ImageView imageView,
                           @NonNull final Bitmap bitmap) {

        // Modifying the layout parameters is mainly for zooming,
        // but is sometimes (why?) needed elsewhere.
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (bitmap.getWidth() < bitmap.getHeight()) {
            // image is portrait; limit the height
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = mHeight;
        } else {
            // image is landscape; limit the width
            lp.width = mWidth;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        imageView.setLayoutParams(lp);

        // padding MUST be 0dp to allow scaling ratio to work properly
        imageView.setPadding(0, 0, 0, 0);
        // essential, so lets not reply on it having been set in xml
        imageView.setAdjustViewBounds(true);
        if (mEnforceMaxSize) {
            imageView.setMaxHeight(mHeight);
            imageView.setMaxWidth(mWidth);
        } else {
            imageView.setMaxHeight(Integer.MAX_VALUE);
            imageView.setMaxWidth(Integer.MAX_VALUE);
        }
        imageView.setScaleType(mScaleType);
        imageView.setImageBitmap(bitmap);
    }

    /**
     * Load the file in a background thread and display it in the given view.
     *
     * @param imageView to populate
     * @param file      to load, must be valid
     * @param onSuccess (optional) Consumer to execute after successfully displaying the image
     */
    @UiThread
    public void fromFile(@NonNull final ImageView imageView,
                         @NonNull final File file,
                         @Nullable final Consumer<Bitmap> onSuccess) {

        // TODO: not 100% convinced that using 'this' is a safe approach.
        // maybe replace with a UUID.randomUUID()
        imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);
        final WeakReference<ImageView> viewWeakReference = new WeakReference<>(imageView);

        mExecutor.execute(() -> {
            Thread.currentThread().setName(TAG);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // do the actual background work.
            final Bitmap bitmap = ImageUtils.decodeFile(file, mWidth, mHeight);

            // all done; back to the UI thread.
            mHandler.post(() -> {
                // are we still associated with this view ? (remember: views are recycled)
                final ImageView view = viewWeakReference.get();
                if (view != null && this.equals(view.getTag(R.id.TAG_THUMBNAIL_TASK))) {
                    // clear the association
                    view.setTag(R.id.TAG_THUMBNAIL_TASK, null);
                    if (bitmap != null) {
                        fromBitmap(view, bitmap);
                        if (onSuccess != null) {
                            onSuccess.accept(bitmap);
                        }
                    } else {
                        // We only get here if we THOUGHT we had an image, but we failed to
                        // load/decode it. So use 'broken-image' icon and preserve the space
                        placeholder(view, R.drawable.ic_baseline_broken_image_24);
                    }
                }
            });
        });
    }
}
