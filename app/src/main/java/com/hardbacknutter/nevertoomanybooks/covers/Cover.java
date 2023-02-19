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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Represents a persisted cover file with a name based on the book uuid and the cover index number.
 * <p>
 * Storing is done as jpg; loading tries jpg first, png second.
 */
public class Cover {

    private static final String TAG = "Cover";

    @NonNull
    private final String uuid;

    @IntRange(from = 0, to = 1)
    private final int cIdx;

    public Cover(@NonNull final String uuid,
                 final int cIdx) {
        this.uuid = uuid;
        this.cIdx = cIdx;
    }

    /**
     * Get the file for this cover. We'll attempt to find a jpg or a png.
     * <p>
     * Any {@link StorageException} is <strong>IGNORED</strong>
     *
     * @return file
     */
    @NonNull
    public Optional<File> getPersistedFile() {
        if (uuid.isEmpty()) {
            return Optional.empty();
        }

        final File coverDir;
        try {
            coverDir = CoverDir.getDir(ServiceLocator.getAppContext());
        } catch (@NonNull final StorageException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getPersistedCoverFile", e);
            }
            return Optional.empty();
        }

        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx;
        } else {
            name = uuid;
        }

        File coverFile;
        // Try finding a jpg
        coverFile = new File(coverDir, name + ".jpg");
        if (!coverFile.exists()) {
            // not found, try finding a png
            coverFile = new File(coverDir, name + ".png");
            if (!coverFile.exists()) {
                coverFile = null;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            ServiceLocator.getInstance().getLogger()
                          .e(TAG, new Throwable("getPersistedFile"),
                             this + "|file="
                             + (coverFile == null ? "null" : coverFile.getAbsolutePath()));
        }

        if (coverFile != null && coverFile.exists()) {
            return Optional.of(coverFile);
        }
        return Optional.empty();
    }

    /**
     * Persist the given temporary file to a permanent location.
     *
     * @param file temp file to persist
     *
     * @return permanent file
     */
    @NonNull
    public File persist(@NonNull final File file)
            throws IOException, StorageException {
        final String name;
        if (cIdx > 0) {
            name = uuid + "_" + cIdx + ".jpg";
        } else {
            name = uuid + ".jpg";
        }

        final File destination = new File(CoverDir.getDir(ServiceLocator.getAppContext()), name);
        FileUtils.rename(file, destination);
        return destination;
    }

    /**
     * Delete the persisted file (if it exists).
     */
    public void delete() {
        getPersistedFile().ifPresent(FileUtils::delete);
        // Delete from the cache. And yes, we also delete the ones
        // where != index, but we don't care; it's a cache.
        if (ImageUtils.isImageCachingEnabled()) {
            ServiceLocator.getInstance().getCoverCacheDao().delete(uuid);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "Cover{"
               + "uuid='" + uuid + '\''
               + ", cIdx=" + cIdx
               + '}';
    }
}
