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
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Load & scale a Bitmap from a file; and populate the view.
 */
public class ImageViewLoader {

    /** Log tag. */
    private static final String TAG = "ImageViewLoader";

    @NonNull
    private final Handler handler;
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

        handler = new Handler(Looper.getMainLooper());

        this.executor = executor;
        this.scaleType = scaleType;
        this.applySizing = applySizing;
        this.width = width;
        this.height = height;
    }

    private static boolean isAssociated(@Nullable final ImageView view,
                                        @NonNull final UUID taskUuid) {
        return view != null && taskUuid.equals(view.getTag(R.id.TAG_THUMBNAIL_TASK));
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

        final UUID taskUuid = UUID.randomUUID();
        imageView.setTag(R.id.TAG_THUMBNAIL_TASK, taskUuid);
        final WeakReference<ImageView> viewWeakReference = new WeakReference<>(imageView);

        executor.execute(() -> {
            if (!isAssociated(viewWeakReference.get(), taskUuid)) {
                return;
            }

            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // do the loading/scaling as background work.
            final Optional<Bitmap> oBitmap = new Transformation()
                    .setScale(this.width, this.height)
                    .setSource(file)
                    .transform();
            // do NOT quit if oBitmap.isEmpty() as we'll load a placeholder further down if so.

            if (!isAssociated(viewWeakReference.get(), taskUuid)) {
                // We're not sending it to onSuccess.
                // In practice this means sending it to the file-based caching.
                // But if we're scrolling too fast to stay associated, then
                // we can assume the cache-writer is already busy anyhow, so the returned
                // bitmap would get dropped anyhow, and NOT recycled. i.e. a worse situation
                // we must avoid.
                oBitmap.ifPresent(Bitmap::recycle);
                return;
            }

            // back to the UI thread to display the bitmap or placeholder
            handler.post(() -> {
                final ImageView view = viewWeakReference.get();
                if (!isAssociated(view, taskUuid)) {
                    oBitmap.ifPresent(Bitmap::recycle);
                    return;
                }

                // clear the association
                view.setTag(R.id.TAG_THUMBNAIL_TASK, null);

                if (oBitmap.isPresent()) {
                    // Finally, load it into the View
                    fromBitmap(view, oBitmap.get());
                    if (onSuccess != null) {
                        onSuccess.accept(oBitmap.get());
                    }
                } else {
                    // Found the image-file, but failed to load/decode it.
                    if (onFailed != null) {
                        onFailed.run();
                    } else {
                        // Use 'broken-image' icon and preserve the space.
                        placeholder(view, R.drawable.broken_image_24px);
                    }
                }
            });
        });
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
