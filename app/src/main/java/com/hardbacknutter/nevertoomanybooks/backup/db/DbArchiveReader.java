/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;

/**
 * A generic wrapper to read sqlite db files.
 * <p>
 * The {@link #validate(Context)} should detect which database we're dealing with and
 * create the delegate {@link DataReader} and run {@link #validate(Context)} on it.
 * <p>
 * The incoming db is copied to the internal cache dir first.
 */
public class DbArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    @Nullable
    private final SQLiteDatabase sqLiteDatabase;

    @SuppressWarnings("unused")
    @Nullable
    private DataReader<ArchiveMetaData, ImportResults> delegateDataReader;


    /**
     * Constructor.
     *
     * @param context Current context
     * @param uri     to read from
     *
     * @throws IOException           on failure to copy the database file
     * @throws FileNotFoundException if the uri cannot be resolved
     */
    public DbArchiveReader(@NonNull final Context context,
                           @NonNull final Uri uri)
    throws IOException {

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new FileNotFoundException(uri.toString());
            }

            // Copy the file from the uri to a place where we can access it as a database.
            final File tmpDb = new File(context.getCacheDir(), System.nanoTime() + ".db");
            try (OutputStream os = new FileOutputStream(tmpDb)) {
                FileUtils.copy(is, os);
            } finally {
                FileUtils.delete(tmpDb);
            }
            sqLiteDatabase = SQLiteDatabase.openDatabase(tmpDb.getAbsolutePath(), null,
                                                         SQLiteDatabase.OPEN_READONLY);
        }
    }

    @WorkerThread
    @Override
    public void validate(@NonNull final Context context)
            throws DataReaderException,
                   IOException, CredentialsException {

        // sanity check
        if (sqLiteDatabase == null) {
            throw new FileNotFoundException("no db file");
        }

        // Determine if the database file is a supported format
        //  delegateDataReader = SomeDatabaseArchiveReader.getReader(context, sqLiteDatabase,
        //                                                           importHelper);
        if (delegateDataReader != null) {
            delegateDataReader.validate(context);
            return;
        }

        throw new DataReaderException(context.getString(R.string.error_file_not_recognized));
    }

    @Override
    @WorkerThread
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException,
                   CredentialsException,
                   StorageException,
                   IOException {
        if (delegateDataReader != null) {
            return delegateDataReader.readMetaData(context);
        } else {
            return Optional.empty();
        }
    }


    @Override
    @WorkerThread
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   CredentialsException,
                   StorageException,
                   IOException {

        // sanity check, we should not even get here if the database is not supported
        if (delegateDataReader == null) {
            throw new DataReaderException(context.getString(
                    R.string.error_file_not_recognized));
        }

        return delegateDataReader.read(context, progressListener);
    }

    @Override
    public void close()
            throws IOException {
        try {
            if (delegateDataReader != null) {
                delegateDataReader.close();
            }
        } finally {
            if (sqLiteDatabase != null) {
                sqLiteDatabase.close();
                // all done, no need to keep this file.
                FileUtils.delete(new File(sqLiteDatabase.getPath()));
            }
        }
    }
}
