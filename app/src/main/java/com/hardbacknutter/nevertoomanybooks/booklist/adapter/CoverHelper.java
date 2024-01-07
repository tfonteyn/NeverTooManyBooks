/*
 * @Copyright 2018-2023 HardBackNutter
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
 * For book-detail/edit screens,
 * see {@code com.hardbacknutter.nevertoomanybooks.covers.CoverHandler}
 */
class CoverHelper {

    /**
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     * -> width = 0.6 * maxHeight.
     *
     * @see #maxWidthInPixels
     */
    private static final float HW_RATIO = 0.6f;

    @Dimension
    private final int maxWidthInPixels;
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
     * @param maxWidthInPixels Maximum width for a cover in pixels
     * @param scaleType        to use for images
     *                         (ignored for placeholders)
     * @param maxSizeType      how to adjust the size, see {@link ImageViewLoader.MaxSize}
     *                         (ignored for placeholders)
     */
    CoverHelper(@Dimension final int maxWidthInPixels,
                @NonNull final ImageView.ScaleType scaleType,
                @NonNull final ImageViewLoader.MaxSize maxSizeType) {

        // In THIS class only used for the image-caching "filename"
        this.maxWidthInPixels = maxWidthInPixels;

        coverStorage = ServiceLocator.getInstance().getCoverStorage();

        imageCachingEnabled = coverStorage.isImageCachingEnabled();

        //URGENT: deal with  this.maxWidthInPixels, this.maxWidthInPixels
        imageLoader = new ImageViewLoader(ASyncExecutor.MAIN,
                                          scaleType, maxSizeType,
                                          this.maxWidthInPixels, this.maxWidthInPixels);
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
            final Bitmap bitmap = coverStorage.getCachedBitmap(uuid, 0, maxWidthInPixels);
            if (bitmap != null) {
                imageLoader.fromBitmap(coverView, bitmap);
                return true;
            }
        }

        // 2. Cache did not have it, or we were not allowed to check.
        // Check on the file system for the original image file.
        final Optional<File> file = coverStorage.getPersistedFile(uuid, 0);
        if (file.isEmpty()) {
            // let the caller deal with a non-existing image-file
            return false;
        }

        // 3. We have a file.
        if (imageCachingEnabled) {
            // 1. Gets the image from the file system and display it.
            // 2. Start a subsequent task to send it to the cache.
            imageLoader.fromFile(coverView, file.get(), bitmap -> {
                if (bitmap != null) {
                    coverStorage.saveToCache(uuid, 0, bitmap, maxWidthInPixels);
                }
            }, null);
        } else {
            // Get the image from the file system and display it.
            imageLoader.fromFile(coverView, file.get(), null, null);
        }
        return true;
    }
}
