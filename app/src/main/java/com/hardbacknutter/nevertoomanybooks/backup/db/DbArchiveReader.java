/*
 * @Copyright 2020 HardBackNutter
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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class DbArchiveReader
        implements ArchiveReader {

    private static final String SQL_LIST_TABLES =
            "SELECT tbl_name FROM sqlite_master WHERE type='table'";

    @NonNull
    private final ImportManager mHelper;

    @Nullable
    private final SQLiteDatabase mSQLiteDatabase;

    // If some day we support other db files, this should become an enum.
    private boolean mIsCalibre;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     *
     * @throws IOException on failure to copy the database file
     */
    public DbArchiveReader(@NonNull final Context context,
                           @NonNull final ImportManager helper)
            throws IOException {

        mHelper = helper;

        // Copy the file from the uri to a place where we can access it as a database.
        try (InputStream is = context.getContentResolver().openInputStream(mHelper.getUri())) {
            if (is == null) {
                // openInputStream can return null, just pretend we couldn't find the file.
                // Should never happen - flw
                throw new FileNotFoundException(mHelper.getUri().toString());
            }

            File tmpDb = AppDir.Cache.getFile(context, System.currentTimeMillis() + ".db");
            tmpDb = FileUtils.copyInputStream(context, is, tmpDb);
            if (tmpDb != null) {
                mSQLiteDatabase = SQLiteDatabase.openDatabase(tmpDb.getAbsolutePath(), null,
                                                              SQLiteDatabase.OPEN_READONLY);
            } else {
                mSQLiteDatabase = null;
            }
        }
    }

    @Override
    public void validate(@NonNull final Context context)
            throws InvalidArchiveException, FileNotFoundException {

        // sanity check
        if (mSQLiteDatabase == null) {
            throw new FileNotFoundException("no db file");
        }

        // Determine if the database file is a supported format (for now, only check for Calibre).

        // checking 6 tables should be sufficient (and likely excessive)
        int calibreTables = 6;
        try (Cursor cursor = mSQLiteDatabase.rawQuery(SQL_LIST_TABLES, null)) {
            while (cursor.moveToNext()) {
                final String tableName = cursor.getString(0);
                if ("library_id".equals(tableName)
                    || "books".equals(tableName)
                    || "authors".equals(tableName)
                    || "books_authors_link".equals(tableName)
                    || "series".equals(tableName)
                    || "books_series_link".equals(tableName)) {
                    calibreTables--;
                    if (calibreTables == 0) {
                        mIsCalibre = true;
                        return;
                    }
                }
            }
        }

        throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException, InvalidArchiveException {

        // sanity check, we should not even get here if the database is not supported
        if (mIsCalibre && mSQLiteDatabase != null) {
            try (ArchiveReader reader =
                         new CalibreArchiveReader(context, mHelper, mSQLiteDatabase)) {
                reader.validate(context);
                return reader.read(context, progressListener);
            }
        }

        throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
    }

    @Override
    public void close() {
        if (mSQLiteDatabase != null) {
            mSQLiteDatabase.close();
            // all done, no need to keep this file.
            FileUtils.delete(new File(mSQLiteDatabase.getPath()));
        }
    }
}
