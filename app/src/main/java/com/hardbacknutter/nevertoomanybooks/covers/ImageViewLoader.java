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
import androidx.annotation.Px;
import androidx.annotation.UiThread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Optional;
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
    private final Handler handler;
    @NonNull
    private final Executor executor;

    @Px
    private final int width;
    @Px
    private final int height;

    @NonNull
    private final ImageView.ScaleType scaleType;

    @NonNull
    private final MaxSize maxSize;
    private final Transformation scalableImageDecoder;

    /**
     * Constructor.
     *
     * @param executor  to use
     * @param scaleType to use for images
     *                  (ignored for placeholders)
     * @param maxSize   how to adjust the size, see {@link MaxSize}
     *                  (ignored for placeholders)
     * @param width     Desired/Maximum width for a cover in pixels
     * @param height    Desired/Maximum height for a cover in pixels
     */
    @UiThread
    public ImageViewLoader(@NonNull final Executor executor,
                           @NonNull final ImageView.ScaleType scaleType,
                           @NonNull final MaxSize maxSize,
                           @Px final int width,
                           @Px final int height) {

        handler = new Handler(Looper.getMainLooper());

        this.executor = executor;

        this.scaleType = scaleType;
        this.maxSize = maxSize;
        this.width = width;
        this.height = height;

        scalableImageDecoder = new Transformation()
                .setScale(this.width, this.height);
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

        // Use the maximum ALLOWABLE size
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.width = width + imageView.getPaddingLeft() + imageView.getPaddingRight();
        lp.height = height + imageView.getPaddingTop() + imageView.getPaddingBottom();
        imageView.setLayoutParams(lp);

        // These are likely not needed...
        imageView.setMaxHeight(Integer.MAX_VALUE);
        imageView.setMaxWidth(Integer.MAX_VALUE);

        // essential, so lets not rely on it having been set in xml
        imageView.setAdjustViewBounds(true);

        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);
    }

    /**
     * Load the image bitmap into the given view.
     *
     * @param imageView View to populate
     * @param bitmap    The Bitmap of the image
     */
    @UiThread
    public void fromBitmap(@NonNull final ImageView imageView,
                           @NonNull final Bitmap bitmap) {
        switch (maxSize) {
            case Enforce: {
                // Calculate the maximum ALLOWABLE size
                adjustLayoutParameters(imageView, bitmap);
                break;
            }
            case Constrained:
            default:
        }

        // essential, so lets not rely on it having been set in xml
        imageView.setAdjustViewBounds(true);

        imageView.setScaleType(scaleType);
        imageView.setImageBitmap(bitmap);
    }

    private void adjustLayoutParameters(@NonNull final ImageView imageView,
                                        @NonNull final Bitmap bitmap) {

        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (bitmap.getWidth() < bitmap.getHeight()) {
            // image is portrait; limit the height
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = height + imageView.getPaddingTop() + imageView.getPaddingBottom();
        } else {
            // image is landscape; limit the width
            lp.width = width + imageView.getPaddingLeft() + imageView.getPaddingRight();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        imageView.setLayoutParams(lp);
    }

    /**
     * Load the file in a background thread and display it in the given view.
     *
     * @param imageView to populate
     * @param file      to load, must be valid
     * @param onSuccess (optional) Consumer to execute after successfully displaying the image
     * @param onFailed  (optional) Runnable to execute after failing to decode the file
     */
    @UiThread
    public void fromFile(@NonNull final ImageView imageView,
                         @NonNull final File file,
                         @Nullable final Consumer<Bitmap> onSuccess,
                         @Nullable final Runnable onFailed) {

        // TODO: not 100% convinced that using 'this' is a safe approach.
        // maybe replace with a UUID.randomUUID()
        imageView.setTag(R.id.TAG_THUMBNAIL_TASK, this);
        final WeakReference<ImageView> viewWeakReference = new WeakReference<>(imageView);

        executor.execute(() -> {
            Thread.currentThread().setName(TAG);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // do the actual background work.
            final Optional<Bitmap> oBitmap = scalableImageDecoder.setSource(file).transform();
            //TODO: use the handler from the view instead?
            // all done; back to the UI thread.
            handler.post(() -> {
                // are we still associated with this view ? (remember: views are recycled)
                final ImageView view = viewWeakReference.get();
                if (view != null && this.equals(view.getTag(R.id.TAG_THUMBNAIL_TASK))) {
                    // clear the association
                    view.setTag(R.id.TAG_THUMBNAIL_TASK, null);
                    if (oBitmap.isPresent()) {
                        fromBitmap(view, oBitmap.get());
                        if (onSuccess != null) {
                            onSuccess.accept(oBitmap.get());
                        }
                    } else {
                        // We only get here if we found the image-file, but we failed to
                        // load/decode it.
                        if (onFailed != null) {
                            onFailed.run();
                        } else {
                            // So use 'broken-image' icon and preserve the space.
                            placeholder(view, R.drawable.broken_image_24px);
                        }
                    }
                }
            });
        });
    }

    public enum MaxSize {
        /** Enforce the desired size as the maximum size. */
        Enforce,
        /**
         * Don't change, let the xml constraint settings rule.
         * Used for grid-mode where the grid layout will control the available space.
         */
        Constrained
    }
}
