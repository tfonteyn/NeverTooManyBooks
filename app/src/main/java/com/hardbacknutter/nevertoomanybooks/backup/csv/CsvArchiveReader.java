/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public class CsvArchiveReader
        implements ArchiveReader {

    /** import configuration. */
    @NonNull
    private final ImportManager mHelper;

    public CsvArchiveReader(@NonNull final ImportManager helper) {
        mHelper = helper;
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        @Nullable
        InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            // openInputStream can return null, just pretend we couldn't find the file.
            // Should never happen - flw
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        try (Importer importer = new CsvImporter(context, mHelper.getOptions())) {
            ReaderEntity entity = new CsvReaderEntity(is);
            return importer.read(context, entity, progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void validate(@NonNull final Context context) {
        // hope for the best
    }

    private static class CsvReaderEntity
            implements ReaderEntity {

        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param is InputStream to use
         */
        CsvReaderEntity(@NonNull final InputStream is) {
            mIs = is;
        }

        @NonNull
        @Override
        public String getName() {
            return ArchiveContainerEntry.BooksCsv.getName();
        }

        @NonNull
        @Override
        public Date getDateModified() {
            return new Date();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return mIs;
        }

        @NonNull
        @Override
        public ArchiveContainerEntry getType() {
            return ArchiveContainerEntry.BooksCsv;
        }
    }
}
