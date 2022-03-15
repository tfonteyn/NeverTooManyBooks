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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Set;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * For better or worse... this class and it's children implementations
 * are passed around a lot, and hence thighly coupled.
 * The alternative was a lot of duplicate code and a LOT of individual parameter passing.
 *
 * <ul>
 *     <li>{@link CredentialsException}: We cannot authenticate to the site,
 *                                       the user MUST take action on it NOW.</li>
 *    <li>{@link CertificateException}:  There is an issue with the site certificate,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link StorageException}:     Specific local storage issues,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link DataWriterException}:  The embedded Exception has the details,
 *                                       should be reported to the user,
 *                                       but action is optional.</li>
 *    <li>{@link IOException}:           Generic IO issues.</li>
 * </ul>
 *
 * @param <RESULTS> the result object from a {@link #write(Context, ProgressListener)}
 */
public abstract class DataWriterHelperBase<RESULTS> {

    /** <strong>What</strong> is going to be exported. */
    @NonNull
    private final EnumSet<RecordType> mRecordTypes = EnumSet.noneOf(RecordType.class);

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    protected DataWriter<RESULTS> mDataWriter;

    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean mIncremental;

    public void addRecordType(@NonNull final Set<RecordType> recordTypes) {
        mRecordTypes.addAll(recordTypes);
    }

    public void removeRecordType(@NonNull final Set<RecordType> recordTypes) {
        mRecordTypes.removeAll(recordTypes);
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
        // sanity check
        mRecordTypes.remove(RecordType.MetaData);
        // Return a copy!
        return EnumSet.copyOf(mRecordTypes);
    }

    public boolean isIncremental() {
        return mIncremental;
    }

    public void setIncremental(final boolean incremental) {
        mIncremental = incremental;
    }

    /**
     * Perform a full write.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @see DataWriter
     */
    @WorkerThread
    @NonNull
    public abstract RESULTS write(@NonNull Context context,
                                  @NonNull ProgressListener progressListener)
            throws DataWriterException,
                   CertificateException,
                   CredentialsException,
                   SSLException,
                   StorageException,
                   IOException;

    public void cancel() {
        synchronized (this) {
            if (mDataWriter != null) {
                mDataWriter.cancel();
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "DataWriterHelperBase{"
               + "mRecordTypes=" + mRecordTypes
               + ", mIncremental=" + mIncremental
               + '}';
    }

}
