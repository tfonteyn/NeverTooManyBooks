/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.STask;

/**
 * Load & scale a Bitmap from a file; and populate the view.
 */
public class ImageViewLoader {

    @NonNull
    private final ExecutorService executor;

    @Px
    private final int width;
    @Px
    private final int height;

    @NonNull
    private final ImageView.ScaleType scaleType;

    @NonNull
    private final ApplySizing applySizing;

    private final Map<ImageView, Future<?>> runningTasks =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Constructor.
     *
     * @param executor    to use
     * @param scaleType   to use for images
     *                    (ignored for placeholders)
     * @param applySizing how to adjust the size, see {@link ApplySizing}
     *                    (ignored for placeholders)
     * @param width       Desired/Maximum width for a cover in pixels
     * @param height      Desired/Maximum height for a cover in pixels
     */
    @AnyThread
    public ImageViewLoader(@NonNull final ExecutorService executor,
                           @NonNull final ImageView.ScaleType scaleType,
                           @NonNull final ApplySizing applySizing,
                           @Px final int width,
                           @Px final int height) {

        this.executor = executor;
        this.scaleType = scaleType;
        this.applySizing = applySizing;
        this.width = width;
        this.height = height;
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

        // theoretically not needed as we've already scaled
        // the image to fit.
        // Except on Android 8.x! So until we drop 8x, this IS NEEDED.
        // See {@link Transformation#transform()}
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(drawable);
    }

    /**
     * Load the image bitmap into the given view.
     *
     * @param imageView View to populate
     * @param bitmap    The Bitmap of the image
     *
     * @throws IllegalArgumentException (debug)
     */
    @UiThread
    public void fromBitmap(@NonNull final ImageView imageView,
                           @NonNull final Bitmap bitmap) {
        switch (applySizing) {
            case Constrained: {
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
                break;
            }
            case Enforce: {
                final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
                lp.width = width;
                lp.height = height;
                imageView.setLayoutParams(lp);
                break;
            }
            case None: {
                break;
            }
            default:
                throw new IllegalArgumentException(applySizing.toString());
        }

        // essential, so lets not rely on it having been set in xml
        imageView.setAdjustViewBounds(true);

        imageView.setScaleType(scaleType);
        imageView.setImageBitmap(bitmap);
    }

    /**
     * Load the file in a background thread and display it in the given view.
     *
     * @param uuid        of the book
     * @param cIdx        0..n image index
     * @param file        to load, must be valid
     * @param imageView   to populate
     * @param onDisplayed (optional) Consumer to execute after successfully displaying the image
     */
    @UiThread
    public void fromFile(@Nullable final String uuid,
                         @IntRange(from = 0, to = 3) final int cIdx,
                         @NonNull final File file,
                         @NonNull final ImageView imageView,
                         @Nullable final Consumer<Bitmap> onDisplayed) {

        // CANCEL any previous task still trying to use this ImageView
        final Future<?> oldTask = runningTasks.remove(imageView);
        if (oldTask != null) {
            oldTask.cancel(true);
        }

        // CREATE the unique reference for THIS specific book+cover+view combo
        final ImageReference imageReference = new ImageReference(uuid, cIdx, imageView);

        final Future<?>[] task = new Future<?>[1];
        task[0] = STask.execute(
                executor,
                () -> {
                    // Initial check, as this thread might have been started after a small delay
                    // or even cancelled.
                    if (imageReference.getView() == null
                        || Thread.currentThread().isInterrupted()) {
                        return null;
                    }

                    final Optional<Bitmap> oBitmap = new Transformation()
                            .setScale(this.width, this.height)
                            .setSource(file)
                            .transform();

                    if (imageReference.getView() == null
                        || Thread.currentThread().isInterrupted()) {
                        // We're not sending the bitmap (if we have one) as the result,
                        // instead we just recycle it.
                        // If we send it back, we'd be sending it to the file-based caching.
                        // But if we're scrolling too fast to stay associated, then
                        // we can assume the cache-writer is already busy anyhow,
                        // so the returned bitmap would get dropped anyhow, and NOT recycled.
                        // i.e. a worse situation we must avoid.
                        oBitmap.ifPresent(Bitmap::recycle);
                        return null;
                    }

                    return oBitmap.orElse(null);
                },
                bitmap -> {
                    final ImageView view = imageReference.getView();
                    try {
                        // Check if the view is still there and still wants THIS bitmap
                        // Double check via runningTasks to ensure no newer task has started.
                        if (view == null || runningTasks.get(imageView) != task[0]) {
                            if (bitmap != null) {
                                bitmap.recycle();
                            }
                            return;
                        }

                        if (bitmap != null) {
                            // Finally, load it into the View
                            fromBitmap(view, bitmap);
                            if (onDisplayed != null) {
                                onDisplayed.accept(bitmap);
                            }
                        } else {
                            // Found the image-file, but failed to load/decode it.
                            // Use 'broken-image' icon and preserve the space.
                            placeholder(view, R.drawable.broken_image_24px);
                        }
                    } finally {
                        runningTasks.remove(imageView, task[0]);
                    }
                },
                e -> runningTasks.remove(imageView, task[0]));
        runningTasks.put(imageView, task[0]);
    }

    public enum ApplySizing {
        /** Use constraint settings. */
        Constrained,
        /** Use a fixed width and height. */
        Enforce,
        /** Don't apply any (avoids {@code null}-usage). */
        None
    }
}
