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

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface definition for an importer.
 *
 * @author pjw
 */
public interface Importer {

    /**
     * Import from an InputStream. It's up to the implementation to decide what to import.
     *
     * @param importStream Stream for reading data
     * @param coverFinder  (Optional) object to find a file on the local device
     * @param listener     Progress and cancellation provider
     *
     * @return number of items handled (!= imported)
     *
     * @throws IOException on any error
     */
    @SuppressWarnings({"UnusedReturnValue", "SameReturnValue"})
    int doImport(@NonNull final InputStream importStream,
                 @Nullable final CoverFinder coverFinder,
                 @NonNull final ImportListener listener)
            throws IOException;

    /**
     * Interface for finding a cover file on the local device if missing from
     * bookCatalogue directory.
     * <p>
     * Legacy of the "import from a directory" model. Used by the CSV importer.
     */
    interface CoverFinder {

        void copyOrRenameCoverFile(@NonNull final String uuidFromFile)
                throws IOException;

        void copyOrRenameCoverFile(final long srcId,
                                   @NonNull final String uuidFromBook)
                throws IOException;
    }

    /**
     * Listener interface to get progress messages.
     */
    interface ImportListener {

        /**
         * @param max value (can be estimated) for the progress counter
         */
        void setMax(final int max);

        /**
         * Report progress in absolute position.
         *
         * @param message  to display
         * @param position absolute position for the progress counter
         */
        void onProgress(@NonNull final String message,
                        final int position);

        /**
         * @return <tt>true</tt> if we are cancelled.
         */
        boolean isCancelled();
    }
}
