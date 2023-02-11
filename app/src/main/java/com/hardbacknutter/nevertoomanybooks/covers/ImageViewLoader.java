/*
 * @Copyright 2018-2022 HardBackNutter
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

    private final boolean enforceMaxSize;

    @UiThread
    public ImageViewLoader(@NonNull final Executor executor,
                           @Px final int width,
                           @Px final int height) {
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
                           @Px final int width,
                           @Px final int height,
                           final boolean enforceMaxSize) {

        handler = new Handler(Looper.getMainLooper());

        this.executor = executor;

        this.scaleType = scaleType;
        this.width = width;
        this.height = height;
        this.enforceMaxSize = enforceMaxSize;
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
        lp.width = width + imageView.getPaddingLeft() + imageView.getPaddingRight();
        lp.height = height + imageView.getPaddingTop() + imageView.getPaddingBottom();
        imageView.setLayoutParams(lp);

        imageView.setAdjustViewBounds(true);
        imageView.setMaxHeight(Integer.MAX_VALUE);
        imageView.setMaxWidth(Integer.MAX_VALUE);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);
    }

    /**
     * Load the image bitmap into the given view.
     * The image is scaled to fit the box exactly preserving the aspect ratio.
     * i.e. one dimension will fit 100%, the other dimension will (usually) have
     * white-space left.
     *
     * @param imageView View to populate
     * @param bitmap    The Bitmap of the image
     */
    @UiThread
    public void fromBitmap(@NonNull final ImageView imageView,
                           @NonNull final Bitmap bitmap) {

        final int tmpWidth = width + imageView.getPaddingLeft() + imageView.getPaddingRight();
        final int tmpHeight = height + imageView.getPaddingTop() + imageView.getPaddingBottom();

        // Modifying the layout parameters is mainly for zooming,
        // but is sometimes (why?) needed elsewhere.
        final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (bitmap.getWidth() < bitmap.getHeight()) {
            // image is portrait; limit the height
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = tmpHeight;
        } else {
            // image is landscape; limit the width
            lp.width = tmpWidth;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        imageView.setLayoutParams(lp);

        // essential, so lets not rely on it having been set in xml
        imageView.setAdjustViewBounds(true);

        if (enforceMaxSize) {
            imageView.setMaxHeight(tmpHeight);
            imageView.setMaxWidth(tmpWidth);
        } else {
            imageView.setMaxHeight(Integer.MAX_VALUE);
            imageView.setMaxWidth(Integer.MAX_VALUE);
        }
        imageView.setScaleType(scaleType);
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

        executor.execute(() -> {
            Thread.currentThread().setName(TAG);
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // do the actual background work.
            final Bitmap bitmap = ImageUtils.decodeFile(file, width, height);

            // all done; back to the UI thread.
            handler.post(() -> {
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
