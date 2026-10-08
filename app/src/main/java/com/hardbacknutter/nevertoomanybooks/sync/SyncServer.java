/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

/**
 * Note: {@link #hasLastUpdateDateField} / {@link #isSyncDateUserEditable}:
 * It's debatable that we could just use {@link #hasLastUpdateDateField} for both meanings.
 */
public interface SyncServer {

    /**
     * Get the {@link SyncServerId} for this instance.
     *
     * @return id
     */
    @NonNull
    SyncServerId getId();

    /**
     * A short label. Used in drop down menus and similar.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    String getLabel(@NonNull Context context);

    /**
     * Check whether each book has a specific last-update date to
     * (help) sync it with the server/website.
     *
     * @return {@code true} if a last-update date is available
     */
    boolean hasLastUpdateDateField();

    /**
     * Can we query the server using the last-sync-date.
     *
     * @return flag
     */
    boolean isSyncDateUserEditable();

    /**
     * Create an {@link DataWriter}.
     *
     * @param context         Current context
     * @param recordTypes     the record types to write
     * @param incremental     flag: if the last-sync-date setting should
     *                        be used to do an incremental write
     * @param deleteLocalBook flag: delete the local book if it no longer exists on the remote
     *
     * @return a new writer
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    @WorkerThread
    @NonNull
    DataWriter<SyncWriterResults> createWriter(@NonNull Context context,
                                               @NonNull Set<RecordType> recordTypes,
                                               boolean incremental,
                                               boolean deleteLocalBook)
            throws CertificateException;

    /**
     * Create an {@link DataReader}.
     *
     * @param context              Current context
     * @param recordTypes          the record types to accept and read
     * @param syncProcessorBuilder synchronization configuration
     * @param syncDate             optional cut-off date
     * @param updateOption         options
     * @param extraArgs            Bundle with reader specific arguments
     *
     * @return a new reader
     *
     * @throws CertificateException on failures related to a user installed CA.
     * @throws CredentialsException on authentication/login failures
     * @throws DataReaderException  if the input is not recognised
     * @throws IOException          on generic/other IO failures
     * @see DataReader
     */
    @NonNull
    @WorkerThread
    DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull Context context,
            @NonNull Set<RecordType> recordTypes,
            @Nullable SyncReaderProcessor.Builder syncProcessorBuilder,
            @Nullable LocalDateTime syncDate,
            @NonNull DataReader.Updates updateOption,
            @NonNull Bundle extraArgs)
            throws DataReaderException,
                   CertificateException,
                   CredentialsException,
                   IOException;

    /**
     * Create the default {@link SyncReaderProcessor.Builder}.
     * <p>
     * Simple fields are set to {@link SyncAction#CopyIfBlank}.
     * List fields are set to {@link SyncAction#Append}.
     *
     * @param context Current context
     *
     * @return a {@link SyncReaderProcessor.Builder}
     */
    @NonNull
    SyncReaderProcessor.Builder createSyncProcessorBuilder(@NonNull Context context);
}
