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
package com.hardbacknutter.nevertoomanybooks.backup.bin;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * <strong>Dev. note:</strong> this class will be reused for reading multiple images.
 * Do not add/use class globals.
 * <p>
 * We import images without checking if we actually have the book/author/...
 * This is on-purpose so we can import images without importing books,
 * as a way to add images to existing books.
 * HOWEVER: this will not work for Authors, as we rely on the file-uuid being stored
 * with the author.
 */
public class ImageRecordReader
        implements RecordReader {

    private static final String TAG = "ImageRecordReader";

    /** The amount of bits we'll shift the last-modified time. (== divide by 65536) */
    private static final int FILE_LM_PRECISION = 16;

    @NonNull
    private final DataReader.Updates updateOption;

    /**
     * Constructor.
     *
     * @param updateOption options
     */
    public ImageRecordReader(@NonNull final DataReader.Updates updateOption) {
        this.updateOption = updateOption;
    }

    @SuppressWarnings("OverlyBroadThrowsClause")
    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ProgressListener progressListener)
            throws StorageException, IOException {

        final ImportResults results = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            if (recordType == RecordType.Cover) {
                results.imagesProcessed = 1;

                try {
                    final CoverStorage storage = ServiceLocator.getInstance().getCoverStorage();
                    // see if we have this file already
                    File dstFile = new File(storage.getDir(), record.getName());
                    final boolean exists = dstFile.exists();

                    if (exists) {
                        // Are we allowed to overwrite at all ?
                        switch (updateOption) {
                            case Skip: {
                                results.imagesSkipped++;
                                return results;
                            }
                            case OnlyNewer: {
                                // Shift 16 bits to get to +- 1 minute precision.
                                // Using milliseconds creates far to many false positives
                                final long importFileDate =
                                        record.getLastModifiedEpochMilli() >> FILE_LM_PRECISION;
                                final long existingFileDate =
                                        dstFile.lastModified() >> FILE_LM_PRECISION;

                                if (existingFileDate > importFileDate) {
                                    results.imagesSkipped++;
                                    return results;
                                }
                                break;
                            }

                            // handled below
                            case Overwrite:
                            default:
                                break;
                        }
                    }

                    // Don't close this stream; Also; this comes from a zip/tar archive
                    // which will give us a buffered stream; do not buffer twice.
                    final InputStream is = record.getInputStream();
                    dstFile = storage.persist(is, dstFile);

                    if (storage.isAcceptableSize(dstFile)) {
                        //noinspection ResultOfMethodCallIgnored
                        dstFile.setLastModified(record.getLastModifiedEpochMilli());
                        if (exists) {
                            results.imagesUpdated++;
                        } else {
                            results.imagesCreated++;
                        }
                    } else {
                        // discard
                        FileUtils.delete(dstFile);
                    }
                } catch (@NonNull final IOException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger().d(TAG, "read", e);
                    }
                    // we swallow IOExceptions, **EXCEPT** when the disk is full.
                    if (FileUtils.isDiskFull(e)) {
                        throw e;
                    }
                    // we don't want to quit importing just because one file fails.
                    results.imagesFailed++;
                }
            }
        }
        return results;
    }
}
