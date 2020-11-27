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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public class JsonArchiveReader
        implements ArchiveReader {

    /** import configuration. */
    @NonNull
    private final ImportHelper mHelper;

    public JsonArchiveReader(@NonNull final ImportHelper helper) {
        mHelper = helper;
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            // openInputStream can return null, just pretend we couldn't find the file.
            // Should never happen - flw
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        try (Importer importer = new JsonImporter(context, mHelper.getOptions())) {
            final ReaderEntity entity = new JsonReaderEntity(is);
            return importer.read(context, entity, progressListener);
        } finally {
            is.close();
        }
    }

    @Override
    public void validate(@NonNull final Context context) {
        // hope for the best
    }

    @VisibleForTesting
    public static class JsonReaderEntity
            implements ReaderEntity {

        /** The entity source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param is InputStream to use
         */
        JsonReaderEntity(@NonNull final InputStream is) {
            mIs = is;
        }

        @NonNull
        @Override
        public String getName() {
            return ArchiveContainerEntry.BooksJson.getName();
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

        @NonNull
        @Override
        public ArchiveContainerEntry getType() {
            return ArchiveContainerEntry.BooksJson;
        }
    }
}
