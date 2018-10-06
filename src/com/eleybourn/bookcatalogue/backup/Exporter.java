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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

/**
 * Interface definition for a 'books' exporter.
 *
 * Currently (Feb 2013) there is only one, but there will probably be an XML export/import one day.
 * or JSON?
 *
 * @author pjw
 */
public interface Exporter {
    /** Flag value to indicate new books and books with more recent update_date fields should be exported */
    int EXPORT_NOTHING = 0;
    int EXPORT_SINCE = (1 << 1);
    int EXPORT_PREFERENCES = (1 << 2);
    int EXPORT_STYLES = (1 << 3);
    int EXPORT_COVERS = (1 << 4);
    int EXPORT_DETAILS = (1 << 5);

    /** Flag value to indicate ALL books should be exported */
    int EXPORT_ALL = EXPORT_PREFERENCES | EXPORT_STYLES | EXPORT_COVERS | EXPORT_DETAILS;
    int EXPORT_ALL_SINCE = EXPORT_PREFERENCES | EXPORT_STYLES | EXPORT_COVERS | EXPORT_DETAILS | EXPORT_SINCE;
    int EXPORT_MASK = EXPORT_ALL | EXPORT_SINCE;

    /**
     * Export function
     *
     * @param outputStream Stream to send data
     * @param listener     Progress & cancellation interface
     *
     * @return true on success
     */
    boolean export(@NonNull final OutputStream outputStream,
                   @NonNull final ExportListener listener,
                   final int backupFlags,
                   final Date since) throws IOException;

    /**
     * Listener interface to get progress messages.
     *
     * @author pjw
     */
    interface ExportListener {
        void setMax(final int max);

        void onProgress(@NonNull final String message, final int position);

        boolean isCancelled();
    }

}
