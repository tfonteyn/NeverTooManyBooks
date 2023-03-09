/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

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
    private final EnumSet<RecordType> recordTypes = EnumSet.noneOf(RecordType.class);

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    protected DataWriter<RESULTS> dataWriter;

    /**
     * Do an incremental export. Definition of incremental depends on the writer.
     * <ul>
     *     <li>{@code false}: all books</li>
     *     <li>{@code true}: books added/updated</li>
     * </ul>
     */
    private boolean incremental;

    /**
     * Add the given set of {@link RecordType}s.
     *
     * @param recordTypes to add
     */
    public void addRecordType(@NonNull final Set<RecordType> recordTypes) {
        this.recordTypes.addAll(recordTypes);
    }

    /**
     * Remove the given set of {@link RecordType}s.
     *
     * @param recordTypes to add
     */
    public void removeRecordType(@NonNull final Set<RecordType> recordTypes) {
        this.recordTypes.removeAll(recordTypes);
    }

    /**
     * Add or remove the given {@link RecordType}.
     *
     * @param add        {@code true} to add, {@code false} to remove
     * @param recordType to add/remove
     */
    public void setRecordType(final boolean add,
                              @NonNull final RecordType recordType) {
        if (add) {
            recordTypes.add(recordType);
        } else {
            recordTypes.remove(recordType);
        }
    }

    /**
     * Get the Set of RecordType.
     *
     * @return an immutable Set
     */
    @NonNull
    public Set<RecordType> getRecordTypes() {
        // sanity check
        recordTypes.remove(RecordType.MetaData);
        return EnumSet.copyOf(recordTypes);
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(final boolean incremental) {
        this.incremental = incremental;
    }

    /**
     * Perform a full write.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws IOException          on generic/other IO failures
     * @throws CertificateException on failures related to a user installed CA
     * @throws DataWriterException  on a decoding/parsing of data issue
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
            if (dataWriter != null) {
                dataWriter.cancel();
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "DataWriterHelperBase{"
               + "recordTypes=" + recordTypes
               + ", incremental=" + incremental
               + '}';
    }

}
