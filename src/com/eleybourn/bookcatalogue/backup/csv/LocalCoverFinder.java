/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.csv;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Class to find covers for an importer when the import is reading from a local directory.
 * <p>
 * Used be the CSV file import class. It check for a cover with the UUID or the id
 *
 * @author pjw
 */
public class LocalCoverFinder
        implements Importer.CoverFinder {

    /** The root path to search for files. */
    @NonNull
    private final String mSrc;
    /** indicates the cover files are not in the 'covers' directory, so will need to be copied. */
    private final boolean mIsForeign;

    /**
     * Constructor.
     *
     * @param srcPath the path where to look for cover files.
     */
    public LocalCoverFinder(@NonNull final String srcPath) {
        mSrc = srcPath;
        mIsForeign = !mSrc.equals(StorageUtils.getSharedStorage().getAbsolutePath());
    }

    /**
     * Entry point for the CSV importer.
     *
     * @param srcId        used to find a cover file
     * @param uuidFromBook of the book
     *
     * @throws IOException on failure
     */
    @Override
    public void copyOrRenameCoverFile(final long srcId,
                                      @NonNull final String uuidFromBook)
            throws IOException {

        if (srcId != 0) {
            if (mIsForeign) {
                // copy it
                File source = findExternalCover(String.valueOf(srcId));
                if (source == null || !source.exists() || source.length() == 0) {
                    return;
                }
                copyFileToCoverImageIfMissing(source, uuidFromBook);

            } else {
                // it's a rename
                File source = findExternalCover(String.valueOf(srcId));
                if (source == null || !source.exists() || source.length() == 0) {
                    return;
                }

                // Check for ANY current image as we do NOT want to overwrite one.
                File destination = getCoverFile(uuidFromBook);
                if (destination.exists()) {
                    return;
                }
                StorageUtils.renameFile(source, destination);
            }
        }
    }

    /**
     * Entry point for the CSV importer.
     *
     * @param uuidFromFile used to find a cover file; uuid must be valid. No checks are made.
     *
     * @throws IOException on failure
     */
    @Override
    public void copyOrRenameCoverFile(@NonNull final String uuidFromFile)
            throws IOException {
        // Only copy UUID files if they are foreign...since they already exists, otherwise.
        if (mIsForeign) {
            File source = findExternalCover(uuidFromFile);
            if (source == null || !source.exists() || source.length() == 0) {
                return;
            }
            copyFileToCoverImageIfMissing(source, uuidFromFile);
        }
    }

    @Nullable
    private File findExternalCover(@NonNull final String name) {
        // Find the original, if present.
        File source = new File(mSrc + File.separator + name + ".jpg");
        if (!source.exists()) {
            source = new File(mSrc + File.separator + name + ".png");
        }

        // Anything to copy?
        return source.exists() ? source : null;
    }

    /**
     * Find the current cover file (or new file) based on the passed UUID.
     *
     * @param uuid of file
     *
     * @return Existing file (if length > 0), or new file object
     */
    @NonNull
    private File getCoverFile(@NonNull final String uuid) {
        // Check for ANY current image; delete empty ones and retry
        File newFile = StorageUtils.getCoverFile(uuid);
        while (newFile.exists()) {
            if (newFile.length() > 0) {
                return newFile;
            } else {
                StorageUtils.deleteFile(newFile);
            }
            newFile = StorageUtils.getCoverFile(uuid);
        }

        return StorageUtils.getCoverFile(uuid);
    }

    /**
     * Copy a specified source file into the default cover location for a new file.
     * DO NO Overwrite EXISTING FILES.
     *
     * @param source  file to copy
     * @param newUuid the uuid to use for the destination file name
     *
     * @throws IOException on failure
     */
    private void copyFileToCoverImageIfMissing(@Nullable final File source,
                                               @NonNull final String newUuid)
            throws IOException {
        // Nothing to copy?
        if (source == null || !source.exists() || source.length() == 0) {
            return;
        }

        // Check for ANY current image
        final File dest = getCoverFile(newUuid);
        if (dest.exists()) {
            return;
        }

        StorageUtils.copyFile(source, dest);
    }
}
