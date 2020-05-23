/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.widget.ImageView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;

// collapse all lines, restart app
// scroll to Pratchett (175 books) on 1st line, expand, scroll to end.
//////////////////////////////////////////////////////////////////////////// test 1
// no cache
// cacheWrites=175|cacheWrites=2065
// cacheWrites=175|cacheWriteTicks=2094
//
// from cache
// cacheReads=175|cacheReadTicks=2305
//
// ==> without rescaling code (leaving it to Android) ==> no point in using a cache.
////////////////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////////////// test 2
// writing to cache with REAL scaling
// cacheWrites=175|cacheWriteTicks=2678

// using cache
// cacheReads=175|cacheReadTicks=1585
//
// ==> so during the writing, its slower.... but afterwards, 50% faster compared to test 1
////////////////////////////////////////////////////////////////////////////


/**
 * Load a Bitmap from a file, and populate the view.
 * This behaves exactly the same as {@link ImageLoader} but when done,
 * it starts a new task to send the image to the image database cache.
 */
class ImageLoaderWithCacheWrite
        extends ImageLoader {

    private final String mUuid;
    private final int mCIdx;

    /**
     * Constructor.
     *
     * @param imageView to populate
     * @param file      to load, must be valid
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param onSuccess (optional) Runnable to execute after successfully displaying the image
     * @param uuid      UUID of the book
     * @param cIdx      0..n image index
     */
    ImageLoaderWithCacheWrite(@NonNull final ImageView imageView,
                              @NonNull final File file,
                              final int maxWidth,
                              final int maxHeight,
                              @Nullable final Runnable onSuccess,

                              final String uuid,
                              @IntRange(from = 0) final int cIdx) {
        super(imageView, file, maxWidth, maxHeight, onSuccess);

        mUuid = uuid;
        mCIdx = cIdx;
    }

    @Override
    protected void onPostExecute(@Nullable final Bitmap bitmap) {
        super.onPostExecute(bitmap);
        if (bitmap != null) {
            // Start another task to send it to the cache
            new CoversDAO.ImageCacheWriterTask(mUuid, mCIdx, mMaxWidth, mMaxHeight, bitmap)
                    .execute();
        }
    }
}
