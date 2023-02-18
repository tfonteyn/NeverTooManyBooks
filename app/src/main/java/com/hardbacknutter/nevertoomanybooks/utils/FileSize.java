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

package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;

public final class FileSize {
    /** Bytes to Mb: decimal as per <a href="https://en.wikipedia.org/wiki/File_size">IEC</a>. */
    private static final int TO_MEGABYTES = 1_000_000;
    /** Bytes to Kb: decimal as per <a href="https://en.wikipedia.org/wiki/File_size">IEC</a>. */
    private static final int TO_KILOBYTES = 1_000;

    private FileSize() {
    }

    /**
     * Format a number of bytes in a human readable form.
     *
     * @param context Current context
     * @param bytes   to format
     *
     * @return formatted # bytes
     */
    @NonNull
    public static String format(@NonNull final Context context,
                                final float bytes) {
        if (bytes < 3_000) {
            // Show 'bytes' if < 3k
            return context.getString(R.string.size_bytes, bytes);
        } else if (bytes < 250_000) {
            // Show Kb if less than 250kB
            return context.getString(R.string.size_kilobytes, bytes / TO_KILOBYTES);
        } else {
            // Show MB otherwise...
            return context.getString(R.string.size_megabytes, bytes / TO_MEGABYTES);
        }
    }
}
