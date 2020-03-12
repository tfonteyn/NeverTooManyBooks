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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Interface definition for an importer.
 */
public interface Importer
        extends Closeable {

    /**
     * Import books from an InputStream.
     *
     * @param context      Current context
     * @param importStream Stream for reading data
     * @param listener     Progress and cancellation provider
     *
     * @return {@link Results}
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    Results doBooks(@NonNull Context context,
                    @NonNull InputStream importStream,
                    @NonNull ProgressListener listener)
            throws IOException, ImportException;

    /**
     * Value class to report back what was imported.
     * <p>
     * Note: failed = processed - created - updated
     */
    class Results {

        public final List<Pair<Integer, String>> failedCsvLines = new ArrayList<>();
        /** The total #books that were present in the import data. */
        public int booksProcessed;
        /** #books we created. */
        public int booksCreated;
        /** #books we updated. */
        public int booksUpdated;
        /** The total #covers that were present in the import data. */
        public int coversProcessed;
        /** #covers we created. */
        public int coversCreated;
        /** #covers we updated. */
        public int coversUpdated;
        /** #styles we imported. */
        public int styles;

        @Override
        @NonNull
        public String toString() {
            return "Results{"
                   + "booksProcessed=" + booksProcessed
                   + ", booksCreated=" + booksCreated
                   + ", booksUpdated=" + booksUpdated

                   + ", coversSkipped=" + coversProcessed
                   + ", coversCreated=" + coversCreated
                   + ", coversUpdated=" + coversUpdated

                   + ", styles=" + styles
                   + ", failedCsvLines=" + failedCsvLines
                   + '}';
        }
    }
}
