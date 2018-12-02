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
package com.eleybourn.bookcatalogue.backup.csv;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

public interface Importer {

    /**
     * Import from an InputStream.
     *
     * @param importStream Stream for reading data*
     * @param coverFinder  (Optional) object to find a file on the local device
     * @param listener     Progress and cancellation provider
     *
     * @return <tt>true</tt>on success
     *
     * @throws IOException on any error
     */
    @SuppressWarnings({"UnusedReturnValue", "SameReturnValue"})
    boolean importBooks(final @NonNull InputStream importStream,
                        final @Nullable CoverFinder coverFinder,
                        final @NonNull OnImporterListener listener) throws IOException;

    /**
     * Listener interface to get progress messages.
     *
     * @author pjw
     */
    interface OnImporterListener {
        void onProgress(final @NonNull String message, final int position);

        boolean isActive();

        void setMax(int max);
    }

    /**
     * Interface for finding a cover file on the local device if missing from bookCatalogue directory.
     * Legacy of the "import from a directory" model.
     *
     * @author pjw
     */
    interface CoverFinder extends AutoCloseable {
        void copyOrRenameCoverFile(final @NonNull String srcUuid, final long srcId, final long dstId) throws IOException;
    }
}
