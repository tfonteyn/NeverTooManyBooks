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
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStream;

/**
 * TOMF: fix inconsistencies introduced in the XmlExporter: OutputStream/BufferedWriter
 * <p>
 * Interface definition for an exporter.
 *
 * @author pjw
 */
public interface Exporter {

    /**
     * Export Books to an OutputStream.
     *
     * @param outputStream Stream for writing data
     * @param listener     Progress and cancellation interface
     *
     * @return number of books exported
     *
     * @throws IOException on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    @WorkerThread
    int doBooks(@NonNull OutputStream outputStream,
                @NonNull ProgressListener listener)
            throws IOException;
}
