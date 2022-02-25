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
package com.hardbacknutter.nevertoomanybooks.backup.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class ExporterBase<RESULTS> {

    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final Set<RecordType> mRecordTypes;

    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean mIncremental;

    public ExporterBase(@NonNull final Set<RecordType> defaultRecordTypes) {
        mRecordTypes = defaultRecordTypes;
    }

    public void addRecordType(@NonNull final RecordType... recordTypes) {
        mRecordTypes.addAll(Arrays.asList(recordTypes));
    }

    public void removeRecordType(@NonNull final RecordType... recordTypes) {
        Arrays.stream(recordTypes).forEach(mRecordTypes::remove);
    }

    public void setRecordType(final boolean add,
                              @NonNull final RecordType recordType) {
        if (add) {
            mRecordTypes.add(recordType);
        } else {
            mRecordTypes.remove(recordType);
        }
    }

    @NonNull
    public Set<RecordType> getRecordTypes() {
        // Return a copy!
        return EnumSet.copyOf(mRecordTypes);
    }

    public boolean isIncremental() {
        return mIncremental;
    }

    public void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    @NonNull
    @WorkerThread
    public abstract RESULTS write(@NonNull Context context,
                                  @NonNull ProgressListener progressListener)
            throws ExportException,
                   IOException,
                   StorageException,
                   CertificateException;

    @Override
    @NonNull
    public String toString() {
        return "ExporterBase{"
               + "mRecordTypes=" + mRecordTypes
               + ", mIncremental=" + mIncremental
               + '}';
    }
}
