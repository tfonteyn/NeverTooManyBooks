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

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Export the main database file.
 * <p>
 * Note on testing: this class is purposely hardcoded to use the actual database file.
 */
public class DbArchiveWriter
        implements DataWriter<ExportResults> {

    /** Export configuration. */
    @NonNull
    private final ExportHelper exportHelper;

    @NonNull
    private final File databasePath;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  export configuration
     */
    public DbArchiveWriter(@NonNull final Context context,
                           @NonNull final ExportHelper helper) {
        exportHelper = helper;
        databasePath = DBHelper.getDatabasePath(context);
    }

    @NonNull
    @Override
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        try (FileInputStream fis = new FileInputStream(databasePath);
             FileOutputStream fos = exportHelper.createOutputStream(context)) {
            FileUtils.copy(fis, fos);
        }

        final ExportResults results = new ExportResults();
        results.database = true;
        return results;
    }
}
