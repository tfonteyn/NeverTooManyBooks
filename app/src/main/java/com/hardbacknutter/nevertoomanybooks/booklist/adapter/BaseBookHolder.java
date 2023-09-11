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
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.io.File;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;

public abstract class BaseBookHolder
        extends RowViewHolder
        implements BindableViewHolder<DataHolder> {

    /**
     * 0.6 is based on a standard paperback 17.5cm x 10.6cm
     * -> width = 0.6 * maxHeight.
     *
     * @see #coverLongestSide
     */
    private static final float HW_RATIO = 0.6f;

    private final int coverLongestSide;
    private final boolean imageCachingEnabled;

    @NonNull
    private final Style style;

    /**
     * Constructor.
     * <p>
     * <strong>Note:</strong> the itemView can be re-used.
     * Hence make sure to explicitly set visibility.
     *
     * @param itemView         the view specific for this holder
     * @param style            to use
     * @param coverLongestSide Longest side for a cover in pixels
     */
    BaseBookHolder(@NonNull final View itemView,
                   @NonNull final Style style,
                   @Dimension final int coverLongestSide) {
        super(itemView);
        this.style = style;
        this.coverLongestSide = coverLongestSide;

        imageCachingEnabled = ServiceLocator.getInstance().getCoverStorage()
                                            .isImageCachingEnabled();
    }

    /**
     * Get the style.
     *
     * @return style for this holder
     */
    @NonNull
    public Style getStyle() {
        return style;
    }

    /**
     * Zoom the given cover.
     *
     * @param coverView passed in to allow for future expansion
     */
    void onZoomCover(@NonNull final View coverView) {
        final String uuid = (String) coverView.getTag(R.id.TAG_THUMBNAIL_UUID);
        ServiceLocator.getInstance().getCoverStorage().getPersistedFile(uuid, 0).ifPresent(
                file -> {
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
     * @param imageView   to load into
     * @param imageLoader to use
     * @param uuid        UUID of the book
     *
     * @return {@code true} if an image was shown; {@code false} if there was no image
     */
    boolean setImageView(@NonNull final ImageView imageView,
                         @NonNull final ImageViewLoader imageLoader,
                         @NonNull final String uuid) {
        // store the uuid for use in the OnClickListener
        imageView.setTag(R.id.TAG_THUMBNAIL_UUID, uuid);

        // 1. If caching is used, check it.
        if (imageCachingEnabled) {
            final Bitmap bitmap = ServiceLocator.getInstance().getCoverStorage()
                                                .getCachedBitmap(uuid, 0,
                                                                 coverLongestSide,
                                                                 coverLongestSide);
            if (bitmap != null) {
                imageLoader.fromBitmap(imageView, bitmap);
                return true;
            }
        }

        // 2. Cache did not have it, or we were not allowed to check.
        // Check on the file system for the original image file.
        final Optional<File> file = ServiceLocator.getInstance().getCoverStorage()
                                                  .getPersistedFile(uuid, 0);
        // If the file image does not exist...
        if (file.isEmpty()) {
            // leave the space blank, but preserve the width BASED on the coverLongestSide!
            final ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            lp.width = (int) (coverLongestSide * HW_RATIO);
            lp.height = 0;
            imageView.setLayoutParams(lp);
            imageView.setImageDrawable(null);
            return false;
        }

        // Once we get here, we know the file is valid
        if (imageCachingEnabled) {
            // 1. Gets the image from the file system and display it.
            // 2. Start a subsequent task to send it to the cache.
            imageLoader.fromFile(imageView, file.get(), bitmap -> {
                if (bitmap != null) {
                    ServiceLocator.getInstance().getCoverStorage().saveToCache(
                            uuid, 0, bitmap, coverLongestSide, coverLongestSide);
                }
            });
        } else {
            // Cache not used: Get the image from the file system and display it.
            imageLoader.fromFile(imageView, file.get(), null);
        }
        return true;
    }
}
