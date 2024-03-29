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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.graphics.Bitmap;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface CoverCacheDao {

    /**
     * Count the total amount of covers in the cache.
     * <p>
     * <strong>All Exceptions are ignored, and {@code 0} is returned instead.</strong>
     *
     * @return number of covers
     */
    int count();

    /**
     * Delete the cached covers associated with the passed {@link Book} uuid.
     * <p>
     * <strong>All Exceptions are ignored, and {@code false} is returned instead.</strong>
     *
     * @param uuid of the book to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull String uuid);

    /**
     * delete all rows.
     * <p>
     * <strong>All Exceptions are ignored.</strong>
     **/
    void deleteAll();

    /**
     * Get a cached image.
     * <p>
     * <strong>All Exceptions are ignored, and {@code null} is returned instead.</strong>
     *
     * @param uuid   UUID of the book
     * @param cIdx   0..n image index
     * @param width  desired/maximum width
     *
     * @return Bitmap (if cached) or {@code null} (if not cached)
     */
    @Nullable
    @AnyThread
    Bitmap getCover(@NonNull String uuid,
                    @IntRange(from = 0, to = 1) int cIdx,
                    int width);

    /**
     * Save the passed bitmap to the cache.
     * <p>
     * <strong>All Exceptions are ignored.</strong>
     *
     * @param uuid   UUID of the book
     * @param cIdx   0..n image index
     * @param bitmap to save
     * @param width  desired/maximum width
     */
    @UiThread
    void saveCover(@NonNull String uuid,
                   @IntRange(from = 0, to = 1) int cIdx,
                   @NonNull Bitmap bitmap,
                   int width);
}
