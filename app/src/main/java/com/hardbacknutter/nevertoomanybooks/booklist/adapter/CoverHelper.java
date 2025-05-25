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

package com.hardbacknutter.nevertoomanybooks.booklist.adapter;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;

/**
 * Handles displaying and zooming for cover-images on the Booklist (both list and grid modes).
 * <p>
 * There is one instance of this class for each book-view-holder instance.
 * <p>
 * For book-detail/edit screens,
 * see {@code com.hardbacknutter.nevertoomanybooks.covers.ImageHandler}
 */
class CoverHelper {

    @Dimension
    private final int cachedImageWidth;
    private final boolean imageCachingEnabled;

    @NonNull
    private final CoverStorage coverStorage;

    /** each holder has its own loader - the more cores the cpu has, the faster we load. */
    @NonNull
    private final ImageViewLoader imageLoader;

    /**
     * Constructor.
     * <p>
     * Dev. note: the width comes from the style scaling factor.
     *
     * @param scaleType   to use for images
     *                    (ignored for placeholders)
     * @param maxSizeType how to adjust the size, see {@link ImageViewLoader.MaxSize}
     *                    (ignored for placeholders)
     * @param maxWidth    Maximum width for a cover in pixels
     * @param maxHeight   Maximum height for a cover in pixels
     */
    CoverHelper(@NonNull final ImageView.ScaleType scaleType,
                @NonNull final ImageViewLoader.MaxSize maxSizeType,
                @Dimension final int maxWidth,
                @Dimension final int maxHeight) {

        // In THIS class only used for the image-caching "filename"
        cachedImageWidth = maxWidth;

        coverStorage = ServiceLocator.getInstance().getCoverStorage();

        imageCachingEnabled = coverStorage.isImageCachingEnabled();

        imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                          scaleType, maxSizeType,
                                          maxWidth, maxHeight);
    }

    /**
     * Zoom the given cover.
     *
     * @param coverView containing the image to zoom.
     *                  Passed in to allow for future expansion.
     */
    void onZoomCover(@NonNull final View coverView) {
        final String uuid = (String) coverView.getTag(R.id.TAG_THUMBNAIL_UUID);
        coverStorage.getPersistedFile(uuid, 0).ifPresent(file -> {
            final FragmentActivity activity = (FragmentActivity) coverView.getContext();
            ZoomedImageDialogFragment.launch(activity.getSupportFragmentManager(), file);
        });
    }

    /**
     * Load the image owned by the UUID/cIdx into the destination ImageView.
     * Handles checking & storing in the cache.
     * <p>
     * Images will always be scaled to a fixed size.
     * <p>
     * A bitmap found in the cache will be displayed immediately.
     * If the image needs to be loaded from a file,
     * it will be done asynchronously using an {@link ImageViewLoader}.
     *
     * @param coverView to load the image into.
     *                  Passed in to allow for future expansion.
     * @param uuid      UUID of the book
     *
     * @return {@code true} if an image was shown;
     *         {@code false} if there was no image
     */
    boolean setImageView(@NonNull final ImageView coverView,
                         @NonNull final String uuid) {
        // store the uuid for use in onZoomCover
        coverView.setTag(R.id.TAG_THUMBNAIL_UUID, uuid);

        // 1. If caching is used, check it.
        if (imageCachingEnabled) {
            // BAD: database access on UI thread
            // Problem: we need to report back whether we have an image or not.
            final Bitmap bitmap = coverStorage.getCachedBitmap(uuid, 0, cachedImageWidth);
            if (bitmap != null) {
                // GOOD: displaying must be done on the UI thread
                imageLoader.fromBitmap(coverView, bitmap);
                return true;
            }
        }

        // 2. Cache did not have it, or it was busy.
        //    (the cache does not allow read-access while it is doing a write)
        // Check on the file system for the original image file.
        // BAD: file-system access on UI thread
        // Problem: we need to report back whether we have an image or not.
        final Optional<File> oFile = coverStorage.getPersistedFile(uuid, 0);
        if (oFile.isEmpty()) {
            // let the caller deal with a non-existing image-file
            return false;
        }

        // 3. We have a file.
        // GOOD: uses a background thread (UI thread for final step of displaying)
        if (imageCachingEnabled) {
            // 1. Gets the image from the file system and display it.
            // 2. Start a subsequent task to send it to the cache.
            imageLoader.fromFile(coverView, oFile.get(), bitmap ->
                    coverStorage.saveToCache(uuid, 0, bitmap, cachedImageWidth), null);
        } else {
            // Get the image from the file system and display it.
            imageLoader.fromFile(coverView, oFile.get(), null, null);
            // 2025-05-25: we did extensive tests using Glide 5.0rc1 library.
            // Neither during normal scrolling, fling-scrolling nor fast-scrolling
            // was there any difference. The most extreme test with 7000 books/covers
            // all visible/expanded, and Glide made no difference whatsoever.
        }
        return true;
    }
}
