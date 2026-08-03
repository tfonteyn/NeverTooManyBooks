/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class ReadingProgressOutput
        implements LauncherOutput {
    @Nullable
    private final ReadingProgress readingProgress;
    @Nullable
    private final Boolean read;

    /**
     * Constructor.
     *
     * @param read Read/Unread status
     */
    ReadingProgressOutput(final boolean read) {
        this(null, read);
    }

    /**
     * Constructor.
     *
     * @param readingProgress data
     */
    ReadingProgressOutput(@Nullable final ReadingProgress readingProgress) {
        this(readingProgress, null);
    }

    private ReadingProgressOutput(@Nullable final ReadingProgress readingProgress,
                                  @Nullable final Boolean read) {
        this.readingProgress = readingProgress;
        this.read = read;
    }

    @NonNull
    static ReadingProgressOutput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final ReadingProgress progress = args.getParcelable(DBKey.READ_PROGRESS);
        final boolean read = args.getBoolean(DBKey.READ__BOOL);

        return new ReadingProgressOutput(progress, read);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        if (read != null) {
            args.putBoolean(DBKey.READ__BOOL, read);
        }
        if (readingProgress != null) {
            args.putParcelable(DBKey.READ_PROGRESS, readingProgress);
        }

        return args;
    }

    @Nullable
    ReadingProgress getReadingProgress() {
        return readingProgress;
    }

    @Nullable
    Boolean getRead() {
        return read;
    }

    @Override
    @NonNull
    public String toString() {
        return "ReadingProgressOutput{"
               + "readingProgress=" + readingProgress
               + ", read=" + read
               + '}';
    }
}
