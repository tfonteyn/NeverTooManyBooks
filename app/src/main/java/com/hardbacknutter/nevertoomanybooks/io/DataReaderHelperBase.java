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
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
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
 *     <li>{@link DataReaderException}:  The embedded Exception has the details,
 *                                       should be reported to the user,
 *                                       but action is optional.</li>
 *    <li>{@link IOException}:           Generic IO issues.</li>
 * </ul>
 *
 * @param <METADATA> the result object from a {@link #readMetaData(Context)}
 * @param <RESULTS>  the result object from a {@link #read(Context, ProgressListener)}
 */
public abstract class DataReaderHelperBase<METADATA, RESULTS> {

    /** <strong>What</strong> is going to be imported. */
    @NonNull
    private final EnumSet<RecordType> recordTypes = EnumSet.noneOf(RecordType.class);
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private DataReader<METADATA, RESULTS> dataReader;
    @Nullable
    private METADATA metadata;

    /**
     * <strong>How</strong> to handle existing books/covers.
     * New Books/Covers are always imported according to {@link #recordTypes}.
     */
    @NonNull
    private DataReader.Updates updateOption = DataReader.Updates.Skip;

    protected DataReaderHelperBase() {
    }

    /**
     * Add the given set of {@link RecordType}s.
     *
     * @param recordTypes to add
     */
    protected void addRecordType(@NonNull final Set<RecordType> recordTypes) {
        this.recordTypes.addAll(recordTypes);
    }

    /**
     * Add the given {@link RecordType}.
     *
     * @param recordType to add
     */
    public void addRecordType(@NonNull final RecordType recordType) {
        recordTypes.add(recordType);
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
     * Get the Set of {@link RecordType}.
     *
     * @return an immutable Set
     */
    @NonNull
    public Set<RecordType> getRecordTypes() {
        // sanity check
        recordTypes.remove(RecordType.MetaData);
        return EnumSet.copyOf(recordTypes);
    }

    /**
     * Get the {@link DataReader.Updates} setting.
     *
     * @return setting
     */
    @NonNull
    public DataReader.Updates getUpdateOption() {
        return updateOption;
    }

    public void setUpdateOption(@NonNull final DataReader.Updates updateOption) {
        this.updateOption = updateOption;
    }

    @NonNull
    public Optional<METADATA> getMetaData() {
        if (metadata != null) {
            return Optional.of(metadata);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Create a new {@link DataReader} specific for the source from where we're importing.
     * <p>
     * It is the callers responsibility to close this reader.
     *
     * @param context Current context
     *
     * @return reader
     *
     * @throws DataReaderException  on failure to produce a supported reader
     * @throws IOException          on generic/other IO failures
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     */
    @NonNull
    protected abstract DataReader<METADATA, RESULTS> createReader(@NonNull Context context)
            throws DataReaderException,
                   CredentialsException,
                   CertificateException,
                   StorageException,
                   IOException;

    /**
     * Read the {@link METADATA} object from the backup.
     *
     * @param context Current context
     *
     * @return Optional with {@link METADATA}
     *
     * @throws DataReaderException  on failure to read the metadata
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws IOException          on generic/other IO failures
     * @throws CertificateException on failures related to a user installed CA.
     * @see DataReader
     */
    @NonNull
    public Optional<METADATA> readMetaData(@NonNull final Context context)
            throws CredentialsException,
                   CertificateException,
                   DataReaderException,
                   StorageException,
                   IOException {

        try {
            dataReader = createReader(context);
            final Optional<METADATA> metaData = dataReader.readMetaData(context);
            metadata = metaData.orElse(null);
            return metaData;
        } finally {
            synchronized (this) {
                if (dataReader != null) {
                    dataReader.close();
                    dataReader = null;
                }
            }
        }
    }

    /**
     * Perform a full read.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws DataReaderException  on failure to read the data
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws IOException          on generic/other IO failures
     * @throws CertificateException on failures related to a user installed CA
     * @see DataReader
     */
    @NonNull
    @WorkerThread
    public RESULTS read(@NonNull final Context context,
                        @NonNull final ProgressListener progressListener)
            throws CertificateException,
                   CredentialsException,
                   DataReaderException,
                   StorageException,
                   IOException {

        SanityCheck.requireValue(recordTypes, "recordTypes");

        try {
            dataReader = createReader(context);
            return dataReader.read(context, progressListener);
        } finally {
            synchronized (this) {
                if (dataReader != null) {
                    dataReader.close();
                    dataReader = null;
                }
            }
        }
    }

    public void cancel() {
        synchronized (this) {
            if (dataReader != null) {
                dataReader.cancel();
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "DataReaderHelperBase{"
               + "recordTypes=" + recordTypes
               + ", updateOption=" + updateOption
               + ", metadata=" + metadata
               + '}';
    }
}
