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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
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
    private final String mSrc;
    private final boolean mIsForeign;
    private final CatalogueDBAdapter mDb;

    LocalCoverFinder(@NonNull final String srcPath, @NonNull final String dstPath) {
        mSrc = srcPath;
        mIsForeign = !mSrc.equals(dstPath);
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mDb.open();
    }

    public void copyOrRenameCoverFile(@Nullable final String srcUuid, final long srcId, final long dstId) throws IOException {
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
    private File findExternalCover(@NonNull final String name) {
        // Find the original, if present.
        File orig = new File(mSrc + File.separator + name + ".jpg");
        if (!orig.exists()) {
            orig = new File(mSrc + File.separator + name + ".png");
        }

        // Nothing to copy?
        return orig.exists() ? orig : null;

    }

    /**
     * Find the current cover file (or new file) based on the passed source and UUID.
     *
     * @param orig    Original file to be copied/renamed if no existing file.
     * @param newUuid UUID of file
     *
     * @return Existing file (if length > 0), or new file object
     */
    private File getNewCoverFile(@NonNull final File orig, @NonNull final String newUuid) {
        // Check for ANY current image; delete empty ones and retry
        File newFile = StorageUtils.getCoverFile(newUuid);
        while (newFile.exists()) {
            if (newFile.length() > 0) {
                return newFile;
            } else {
                StorageUtils.deleteFile(newFile);
            }
            newFile = StorageUtils.getCoverFile(newUuid);
        }

        return StorageUtils.getCoverFile(newUuid);
    }

    /**
     * Copy a specified source file into the default cover location for a new file.
     * DO NO OVERWRITE EXISTING FILES.
     */
    private void copyFileToCoverImageIfMissing(@Nullable final File orig, @NonNull final String newUuid) throws IOException {
        // Nothing to copy?
        if (orig == null || !orig.exists() || orig.length() == 0) {
            return;
        }

        // Check for ANY current image
        final File newFile = getNewCoverFile(orig, newUuid);
        if (newFile.exists()) {
            return;
        }

        StorageUtils.copyFile(orig, newFile);
    }

    /**
     * Rename/move a specified source file into the default cover location for a new file.
     * DO NO OVERWRITE EXISTING FILES.
     */
    private void renameFileToCoverImageIfMissing(@Nullable final File orig, @NonNull final String newUuid) {
        // Nothing to copy?
        if (orig == null || !orig.exists() || orig.length() == 0) {
            return;
        }

        // Check for ANY current image
        final File newFile = getNewCoverFile(orig, newUuid);
        if (newFile.exists()) {
            return;
        }

        StorageUtils.renameFile(orig, newFile);
    }

    /**
     * Copy the ID-based cover from its current location to the correct location in shared
     * storage, if it exists.
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
     * Copy the ID-based cover from its current location to the correct location in shared
     * storage, if it exists.
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
     * Copy the UUID-based cover from its current location to the correct location in shared
     * storage, if it exists.
     */
    private void copyCoverImageIfMissing(@NonNull final String uuid) throws IOException {
        File orig = findExternalCover(uuid);
        // Nothing to copy?
        if (orig == null || !orig.exists()) {
            return;
        }

        copyFileToCoverImageIfMissing(orig, uuid);
    }
}
