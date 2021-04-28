/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * FIXME: currently we import covers without checking if we actually have the book.
 *
 * <strong>Warning:</strong> this class will be reused for reading multiple covers.
 * Do not add/use class globals.
 */
public class CoverRecordReader
        implements RecordReader {

    private static final String TAG = "CoverRecordReader";

    /** The amount of bits we'll shift the last-modified time. (== divide by 65536) */
    private static final int FILE_LM_PRECISION = 16;

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper helper,
                              @NonNull final ProgressListener progressListener)
            throws ExternalStorageException, DiskFullException {

        final ImportResults results = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            if (recordType == RecordType.Cover) {
                results.coversProcessed = 1;

                try {
                    // see if we have this file already
                    File dstFile = new File(CoverDir.getDir(context), record.getName());
                    final boolean exists = dstFile.exists();

                    if (exists) {
                        // Are we allowed to overwrite at all ?
                        if (helper.getUpdateOption() == ImportHelper.Updates.Skip) {
                            results.coversSkipped++;
                            return results;
                        }

                        // If we should only import newer data,
                        // check which is newer, the local file, or the imported file.
                        if (helper.getUpdateOption() == ImportHelper.Updates.OnlyNewer) {
                            // shift 16 bits to get to +- 1 minute precision.
                            // Using pure milliseconds will create far to many false positives
                            final long importFileDate =
                                    record.getLastModifiedEpochMilli() >> FILE_LM_PRECISION;
                            final long existingFileDate =
                                    dstFile.lastModified() >> FILE_LM_PRECISION;
                            if (existingFileDate > importFileDate) {
                                results.coversSkipped++;
                                return results;
                            }
                        }
                    }

                    // Don't close this stream; Also; this comes from a zip/tar archive
                    // which will give us a buffered stream; do not buffer twice.
                    final InputStream is = record.getInputStream();
                    dstFile = FileUtils.copy(is, dstFile);

                    if (ImageUtils.isAcceptableSize(dstFile)) {
                        //noinspection ResultOfMethodCallIgnored
                        dstFile.setLastModified(record.getLastModifiedEpochMilli());
                        if (exists) {
                            results.coversUpdated++;
                        } else {
                            results.coversCreated++;
                        }
                    }
                } catch (@NonNull final IOException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "", e);
                    }
                    // we swallow IOExceptions, **EXCEPT** when the disk is full.
                    if (DiskFullException.isDiskFull(e)) {
                        //noinspection ConstantConditions
                        throw new DiskFullException(e.getCause());
                    }
                    // we don't want to quit importing just because one cover fails.
                    results.coversSkipped++;
                }
            }
        }
        return results;
    }
}
