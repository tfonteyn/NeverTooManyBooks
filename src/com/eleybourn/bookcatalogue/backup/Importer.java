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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface Importer {

    /** Options value to indicate ALL books should be imported */
    int IMPORT_ALL = 1;
    /** Options value to indicate new books and books with more recent update_date fields should be imported */
    int IMPORT_NEW_OR_UPDATED = 2;

    /**
     * Import function
     *
     * @param importStream Stream for reading data
     * @param coverFinder  (Optional) object to find a file on the local device
     * @param listener     Progress and cancellation provider
     *
     * @return <tt>true</tt>on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean importBooks(@NonNull final InputStream importStream,
                        @Nullable final Importer.CoverFinder coverFinder,
                        @NonNull final Importer.OnImporterListener listener,
                        final int importFlags) throws IOException;

    /**
     * Listener interface to get progress messages.
     *
     * @author pjw
     */
    interface OnImporterListener {
        void onProgress(@NonNull final String message, final int position);

        boolean isCancelled();

        void setMax(int max);
    }

    /**
     * Interface for finding a cover file on the local device if missing from bookCatalogue directory.
     * Legacy of the "import from a directory" model.
     *
     * @author pjw
     */
    interface CoverFinder {
        void copyOrRenameCoverFile(@NonNull final String srcUuid, final long srcId, final long dstId) throws IOException;
    }
}
