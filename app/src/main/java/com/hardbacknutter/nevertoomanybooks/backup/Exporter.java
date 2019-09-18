/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * TODO: fix inconsistencies introduced in the XmlExporter: OutputStream/BufferedWriter
 * <p>
 * Interface definition for an exporter.
 */
public interface Exporter
        extends Closeable {

    /**
     * Export Books to an OutputStream.
     *
     * @param os       Stream for writing data
     * @param listener Progress and cancellation interface
     *
     * @return {@link Results}
     *
     * @throws IOException on failure
     */
    @WorkerThread
    Results doBooks(@NonNull OutputStream os,
                    @NonNull ProgressListener listener)
            throws IOException;

    /**
     * Value class to report back what was exported.
     */
    class Results {

        /** The total #books that were considered for export. */
        public int booksProcessed;
        /** #books we exported. */
        public int booksExported;
        /**
         * The total #covers that were considered for exporting.
         * This is the sum of exported + missing + skipped.
         */
        public int coversProcessed;
        /** #covers exported. */
        public int coversExported;
        /** #books that did not have a cover. */
        public int coversMissing;
        /** #styles we exported. */
        public int styles;

        @Override
        @NonNull
        public String toString() {
            return "Results{"
                   + "booksProcessed=" + booksProcessed
                   + ", booksExported=" + booksExported
                   + ", coversProcessed=" + coversProcessed
                   + ", coversExported=" + coversExported
                   + ", coversMissing=" + coversMissing
                   + ", styles=" + styles
                   + '}';
        }
    }
}
