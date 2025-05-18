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

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

public interface ImageOwner {

    /**
     * Get the id of the owning entity.
     *
     * @return id
     */
    long getId();

    /**
     * Get the UUID to use as the image file name.
     *
     * @return uuid
     */
    @NonNull
    Optional<String> getImageUuid();

    /**
     * Get the <strong>current</strong> cover file for this book.
     * This method may return a temporary cover, or the persisted cover.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @param context Current context
     * @param cIdx    0..n image index
     *
     * @return file
     */
    @NonNull
    Optional<File> getImage(@NonNull Context context,
                            @IntRange(from = 0, to = 1) int cIdx);

    /**
     * Update the book cover with the given file.
     * This method may set a temporary cover, or persists the cover to storage.
     *
     * @param context Current context
     * @param cIdx    0..n image index
     * @param file    cover file or {@code null} to delete the cover
     *
     * @return the File after processing (either original, or a renamed/moved file)
     *
     * @throws StorageException The covers directory is not available
     * @throws IOException      on generic/other IO failures
     */
    @Nullable
    File setImage(@NonNull Context context,
                  @IntRange(from = 0, to = 1) int cIdx,
                  @Nullable File file)
            throws StorageException, IOException;

    /**
     * Convenience method for {@link #setImage(Context, int, File)} with a {@code null} file.
     *
     * @param context Current context
     * @param cIdx    0..n image index
     */
    default void removeImage(@NonNull final Context context,
                             @IntRange(from = 0, to = 1) final int cIdx) {
        try {
            setImage(context, cIdx, null);
        } catch (@NonNull final IOException | StorageException ignore) {
            // safe to ignore, can't happen with a 'null' input.
        }
    }
}
