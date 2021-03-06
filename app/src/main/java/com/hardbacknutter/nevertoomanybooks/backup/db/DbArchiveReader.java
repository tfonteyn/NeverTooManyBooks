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
package com.hardbacknutter.nevertoomanybooks.backup.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * A generic wrapper to read sqlite db files.
 * <p>
 * The {@link #validate(Context)} should detect which database we're dealing with and
 * create the delegate {@link ArchiveReader} and run {@link #validate(Context)} on it.
 * <p>
 * The incoming db is copied to the internal cache dir first.
 */
public class DbArchiveReader
        implements ArchiveReader {

    /** Import configuration. */
    @SuppressWarnings("FieldCanBeLocal")
    @NonNull
    private final ImportHelper mHelper;

    @Nullable
    private final SQLiteDatabase mSQLiteDatabase;

    @SuppressWarnings("unused")
    @Nullable
    private ArchiveReader mDelegateReader;


    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws IOException           on failure to copy the database file
     */
    public DbArchiveReader(@NonNull final Context context,
                           @NonNull final ImportHelper helper)
            throws CoverStorageException, IOException {

        mHelper = helper;

        try (InputStream is = context.getContentResolver().openInputStream(mHelper.getUri())) {
            if (is == null) {
                throw new FileNotFoundException(mHelper.getUri().toString());
            }

            // Copy the file from the uri to a place where we can access it as a database.
            File tmpDb = new File(context.getCacheDir(), System.nanoTime() + ".db");
            tmpDb = FileUtils.copy(is, tmpDb);
            mSQLiteDatabase = SQLiteDatabase.openDatabase(tmpDb.getAbsolutePath(), null,
                                                          SQLiteDatabase.OPEN_READONLY);
        }
    }

    @Override
    public void validate(@NonNull final Context context)
            throws InvalidArchiveException, FileNotFoundException {

        // sanity check
        if (mSQLiteDatabase == null) {
            throw new FileNotFoundException("no db file");
        }

        // Determine if the database file is a supported format
//        mDelegateReader = SomeDatabaseArchiveReader.getReader(context, mSQLiteDatabase, mHelper);
//        if (mDelegateReader != null) {
//            mDelegateReader.validate(context);
//            return;
//        }

        throw new InvalidArchiveException(ArchiveReader.ERROR_NO_READER_AVAILABLE);
    }

    @Nullable
    @Override
    @WorkerThread
    public ArchiveMetaData readMetaData(@NonNull final Context context)
            throws InvalidArchiveException, IOException, ImportException,
                   StorageException {
        if (mDelegateReader != null) {
            return mDelegateReader.readMetaData(context);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws InvalidArchiveException, ImportException, IOException,
                   StorageException, CredentialsException {

        // sanity check, we should not even get here if the database is not supported
        if (mDelegateReader == null) {
            throw new InvalidArchiveException(ArchiveReader.ERROR_INVALID_INPUT);
        }

        return mDelegateReader.read(context, progressListener);
    }

    @Override
    public void close()
            throws IOException {
        try {
            if (mDelegateReader != null) {
                mDelegateReader.close();
            }
        } finally {
            if (mSQLiteDatabase != null) {
                mSQLiteDatabase.close();
                // all done, no need to keep this file.
                FileUtils.delete(new File(mSQLiteDatabase.getPath()));
            }
        }
    }
}
