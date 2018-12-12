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
package com.eleybourn.bookcatalogue.backup;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.backup.csv.Importer;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;

/**
 * Class to find covers for an importer when the import is reading from a local directory.
 *
 * @author pjw
 */
public class LocalCoverFinder implements Importer.CoverFinder {
    /** The root path to search for files */
    @NonNull
    private final String mSrc;
    private final boolean mIsForeign;
    @NonNull
    private final CatalogueDBAdapter mDb;

    public LocalCoverFinder(final @NonNull Context context, final @NonNull String srcPath, final @NonNull String dstPath) {
        mSrc = srcPath;
        mIsForeign = !mSrc.equals(dstPath);
        mDb = new CatalogueDBAdapter(context);
    }

    @Override
    public void close() {
        mDb.close();
    }

    public void copyOrRenameCoverFile(final @Nullable String srcUuid, final long srcId, final long dstId) throws IOException {
        if (srcUuid != null && !srcUuid.isEmpty()) {
            // Only copy UUID files if they are foreign...since they already exists, otherwise.
            if (mIsForeign) {
                copyCoverImageIfMissing(srcUuid);
            }
        } else {
            if (srcId != 0) {
                // This will be a rename or a copy
                if (mIsForeign) {
                    copyCoverImageIfMissing(srcId, dstId);
                } else {
                    renameCoverImageIfMissing(srcId, dstId);
                }
            }
        }

    }

    @Nullable
    private File findExternalCover(final long externalId) {
        return findExternalCover(Long.toString(externalId));
    }

    @Nullable
    private File findExternalCover(final @NonNull String name) {
        // Find the original, if present.
        File orig = new File(mSrc + File.separator + name + ".jpg");
        if (!orig.exists()) {
            orig = new File(mSrc + File.separator + name + ".png");
        }

        // Nothing to copy?
        return orig.exists() ? orig : null;

    }

    /**
     * Find the current cover file (or new file) based on the passed UUID.
     *
     * @param uuid of file
     *
     * @return Existing file (if length > 0), or new file object
     */
    @NonNull
    private File getNewCoverFile(final @NonNull String uuid) {
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
     */
    private void copyFileToCoverImageIfMissing(final @Nullable File orig, final @NonNull String newUuid) throws IOException {
        // Nothing to copy?
        if (orig == null || !orig.exists() || orig.length() == 0) {
            return;
        }

        // Check for ANY current image
        final File newFile = getNewCoverFile(newUuid);
        if (newFile.exists()) {
            return;
        }

        StorageUtils.copyFile(orig, newFile);
    }

    /**
     * Rename/move a specified source file into the default cover location for a new file.
     * DO NOT Overwrite EXISTING FILES.
     */
    private void renameFileToCoverImageIfMissing(final @Nullable File source, final @NonNull String newUuid) {
        // Nothing to copy?
        if (source == null || !source.exists() || source.length() == 0) {
            return;
        }

        // Check for ANY current image
        final File destination = getNewCoverFile(newUuid);
        if (destination.exists()) {
            return;
        }

        StorageUtils.renameFile(source, destination);
    }

    /**
     * Copy the ID-based cover from its current location to the correct location in Shared Storage, if it exists.
     *
     * @param externalId The file ID in external media
     * @param newId      The new file ID
     */
    private void renameCoverImageIfMissing(final long externalId, final long newId) {
        File orig = findExternalCover(externalId);
        // Nothing to copy?
        if (orig == null || !orig.exists() || orig.length() == 0) {
            return;
        }

        renameFileToCoverImageIfMissing(orig, mDb.getBookUuid(newId));
    }

    /**
     * Copy the ID-based cover from its current location to the correct location in Shared Storage, if it exists.
     *
     * @param externalId The file ID in external media
     * @param newId      The new file ID
     */
    private void copyCoverImageIfMissing(final long externalId, final long newId) throws IOException {
        File orig = findExternalCover(externalId);
        // Nothing to copy?
        if (orig == null || !orig.exists() || orig.length() == 0) {
            return;
        }

        copyFileToCoverImageIfMissing(orig, mDb.getBookUuid(newId));
    }

    /**
     * Copy the UUID-based cover from its current location to the correct location in Shared Storage, if it exists.
     */
    private void copyCoverImageIfMissing(final @NonNull String uuid) throws IOException {
        File orig = findExternalCover(uuid);
        // Nothing to copy?
        if (orig == null || !orig.exists()) {
            return;
        }

        copyFileToCoverImageIfMissing(orig, uuid);
    }
}
