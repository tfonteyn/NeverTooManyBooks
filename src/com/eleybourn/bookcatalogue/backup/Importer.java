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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interface definition for an importer.
 *
 * @author pjw
 */
public interface Importer
        extends Closeable {

    /**
     * Import books from an InputStream.
     *
     * @param importStream Stream for reading data
     * @param coverFinder  (Optional) object to find a cover on the local device
     * @param listener     Progress and cancellation provider
     *
     * @return {@link Results}
     *
     * @throws IOException on failure
     */
    @NonNull
    Results doBooks(@NonNull InputStream importStream,
                    @Nullable CoverFinder coverFinder,
                    @NonNull ProgressListener listener)
            throws IOException, ImportException;

    /**
     * Interface for finding a cover file on the local device if missing from
     * bookCatalogue directory.
     * <p>
     * Legacy of the "import from a directory" model. Used by the CSV importer.
     */
    interface CoverFinder {

        void copyOrRenameCoverFile(@NonNull String uuidFromFile)
                throws IOException;

        void copyOrRenameCoverFile(long srcId,
                                   @NonNull String uuidFromBook)
                throws IOException;
    }

    class Results {

        public int booksProcessed;
        public int booksCreated;
        public int booksUpdated;
        public int booksFailed;
        public int coversImported;
    }
}
