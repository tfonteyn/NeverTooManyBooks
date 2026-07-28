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
package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Info about a cover file.
 */
public class ImageFileInfo {

    private static final String TAG = "ImageFileInfo";
    @NonNull
    private final AltEdition edition;
    @Nullable
    private final ImageWebSize size;
    @Nullable
    private final String fileSpec;
    @Nullable
    private final EngineId engineId;

    /**
     * Constructor. No file.
     *
     * @param edition of the book for this cover
     */
    public ImageFileInfo(@NonNull final AltEdition edition) {
        this.edition = edition;
        fileSpec = null;
        size = null;
        engineId = null;
    }

    /**
     * Constructor.
     *
     * @param edition of the book for this cover
     * @param fileSpec (optional) of the cover file
     * @param size     (optional) size
     * @param engineId the search engine id
     */
    public ImageFileInfo(@NonNull final AltEdition edition,
                         @Nullable final String fileSpec,
                         @Nullable final ImageWebSize size,
                         @NonNull final EngineId engineId) {
        this.edition = edition;
        this.fileSpec = fileSpec;
        this.size = size;
        this.engineId = engineId;
    }

    /**
     * Get a temporary filename.
     *
     * @param source of the image (normally a SearchEngine specific code)
     * @param bookId (optional) either the native id, or the isbn
     * @param cIdx   0..n image index
     * @param size   (optional) size of the image
     *               Omitted if not set
     *
     * @return filename
     */
    @NonNull
    public static String getTempFilename(@NonNull final String source,
                                         @Nullable final String bookId,
                                         @IntRange(from = 0, to = 3) final int cIdx,
                                         @Nullable final ImageWebSize size) {
        // keep all "_" even for empty parts. Easier to parse the name if needed.
        return System.currentTimeMillis()
               + "_" + source
               + "_" + (bookId != null && !bookId.isEmpty() ? bookId : "")
               + "_" + cIdx
               + "_" + (size != null ? size : "")
               + ".jpg";
    }

    public static boolean isTempFilenameEquals(@Nullable final String path1,
                                               @Nullable final String path2) {
        if (path1 == null || path2 == null) {
            return Objects.equals(path1, path2);
        }
        final String[] s1 = path1.split("_", 2);
        final String[] s2 = path2.split("_", 2);
        // Sanity check; If there are no '_' character, just compare as-is.
        if (s1.length != 2 || s2.length != 2) {
            return path1.equals(path2);
        }

        // Compare without the path/timestamp part
        return s1[1].equals(s2[1]);
    }

    @NonNull
    public AltEdition getEdition() {
        return edition;
    }

    @Nullable
    public ImageWebSize getSize() {
        return size;
    }

    /**
     * The site where we found the image.
     * <p>
     * This method should only be called if {@link #getFile()} returns a valid result.
     *
     * @return engine-id
     */
    @NonNull
    public EngineId getEngineId() {
        return Objects.requireNonNull(engineId);
    }

    /**
     * Get the physical file if present.
     *
     * @return file
     */
    @NonNull
    public Optional<File> getFile() {
        if (fileSpec != null && !fileSpec.isEmpty()) {
            final File file = new File(fileSpec);
            if (file.exists() && file.length() > 0) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if this image is either bigger or equal to the given size,
     * or if we already established the image does not exist.
     *
     * @param size to compare to
     *
     * @return {@code true} if the image is usable
     */
    public boolean isUsable(@NonNull final ImageWebSize size) {
        // Does it have an actual file ?
        if (fileSpec != null) {
            // There is a file, and it is good (as determined at download time)
            // But is the size we have suitable ? Bigger files are always better (we hope)...
            if (this.size != null && this.size.compareTo(size) >= 0) {
                // YES, use the file we already have
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                    LoggerFactory.getLogger().d(TAG, "isUsable", "SUCCESS|imageFileInfo=" + this);
                }
                return true;
            }

            // else drop through and search for it.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger().d(TAG, "isUsable", "TO SMALL|imageFileInfo=" + this);
            }
            return false;

        } else {
            // a previous search failed, there simply is NO file
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGES) {
                LoggerFactory.getLogger().d(TAG, "isUsable", "NO FILE|imageFileInfo=" + this);
            }
            return true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ImageFileInfo{"
               + "edition=`" + edition + '`'
               + ", size=" + size
               + ", engineId=" + engineId
               + ", fileSpec=`"
               + (fileSpec == null ? "" : fileSpec.substring(fileSpec.lastIndexOf('/')))
               + '`'
               + '}';
    }
}
