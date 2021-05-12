/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;

public class SyncWriterConfig {

    /** <strong>What</strong> is going to be exported. */
    @NonNull
    private final Set<RecordType> mExportEntries = EnumSet.noneOf(RecordType.class);
    ;

    /** Extra arguments for specific writers. The writer must define them. */
    private final Bundle mExtraArgs = new Bundle();

    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean mIncremental;

    /** If a book no longer exists on the server, should we delete the local book. */
    private boolean mDeleteLocalBooks;

    /**
     * Constructor.
     */
    public SyncWriterConfig() {
        mExportEntries.add(RecordType.Books);
        mExportEntries.add(RecordType.Cover);
    }

    void setExportEntry(@SuppressWarnings("SameParameterValue") @NonNull final RecordType entry,
                        final boolean isSet) {
        if (isSet) {
            mExportEntries.add(entry);
        } else {
            mExportEntries.remove(entry);
        }
    }

    @NonNull
    public Set<RecordType> getExporterEntries() {
        return mExportEntries;
    }

    @NonNull
    public Bundle getExtraArgs() {
        return mExtraArgs;
    }

    public boolean isDeleteLocalBooks() {
        return mDeleteLocalBooks;
    }

    public void setDeleteLocalBooks(final boolean deleteLocalBooks) {
        mDeleteLocalBooks = deleteLocalBooks;
    }

    public boolean isIncremental() {
        return mIncremental;
    }

    void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterConfig{"
               + "mExportEntries=" + mExportEntries
               + ", mIncremental=" + mIncremental
               + ", mDeleteLocalBooks=" + mDeleteLocalBooks
               + ", mExtraArgs=" + mExtraArgs
               + '}';
    }
}
