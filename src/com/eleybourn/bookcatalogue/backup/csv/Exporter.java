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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface definition for a 'books' exporter.
 *
 * Currently (Feb 2013) there is only one, but there will probably be an XML export/import one day.
 * or JSON?
 *
 * @author pjw
 */
public interface Exporter {

    /**
     * Export to an OutputStream.
     *
     * @param outputStream Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @return <tt>true</tt>on success
     *
     * @throws IOException on any error
     */
    boolean doBooks(final @NonNull OutputStream outputStream,
                    final @NonNull ExportListener listener) throws IOException;

    /**
     * Listener interface to get progress messages.
     */
    interface ExportListener {
        void setMax(final int max);

        void onProgress(final @NonNull String message, final int position);

        boolean isCancelled();
    }

}
