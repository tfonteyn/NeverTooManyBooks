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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.database.DaoLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * A minimal implementation of {@link ArchiveReader} which reads a plain CSV file with books.
 */
public class CsvArchiveReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CsvArchiveReader";

    private static final String DB_BACKUP_NAME = "DbCsvBackup.db";
    private static final int DB_BACKUP_COPIES = 3;

    /** Import configuration. */
    @NonNull
    private final ImportHelper mHelper;
    /** Database Access. */
    @NonNull
    private final BookDao mBookDao;

    /**
     * Constructor.
     *
     * @param helper import configuration
     */
    public CsvArchiveReader(@NonNull final Context context,
                            @NonNull final ImportHelper helper) {
        mHelper = helper;
        mBookDao = new BookDao(context, TAG);
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws GeneralParsingException, ImportException,
                   IOException {

        // Importing CSV which we didn't create can be dangerous.
        // Backup the database, keeping up to CSV_BACKUP_COPIES copies.
        // ENHANCE: For now we don't inform the user of this nor offer a restore.
        FileUtils.copyWithBackup(mBookDao.getDatabaseFile(),
                                 AppDir.Upgrades.getFile(context, DB_BACKUP_NAME),
                                 DB_BACKUP_COPIES);

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        try (RecordReader recordReader = new CsvRecordReader(context, mBookDao)) {
            final ArchiveReaderRecord record = new CsvArchiveRecord(
                    mHelper.getUriInfo(context).getDisplayName(), is);

            return recordReader.read(context, record, mHelper.getOptions(), progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void close() {
        mBookDao.close();
        DaoLocator.getInstance().getMaintenanceDao().purge();
    }

    @VisibleForTesting
    public static class CsvArchiveRecord
            implements ArchiveReaderRecord {

        @NonNull
        private final String mName;

        /** The record source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param name of this record
         * @param is   InputStream to use
         */
        CsvArchiveRecord(@NonNull final String name,
                         @NonNull final InputStream is) {
            mName = name;
            mIs = is;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return Optional.of(RecordType.Books);
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return Optional.of(RecordEncoding.Csv);
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return mIs;
        }
    }
}
