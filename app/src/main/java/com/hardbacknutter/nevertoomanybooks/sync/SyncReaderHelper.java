/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

public final class SyncReaderHelper
        extends DataReaderHelperBase<SyncReaderMetaData, ReaderResults> {

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final SyncServer syncServer;
    /** Extra arguments for specific readers. The reader must define them. */
    private final Bundle extraArgs = new Bundle();
    /** <strong>How</strong> to handle individual fields. Can be {@code null}. aka unused. */
    @NonNull
    private final SyncReaderProcessor.Builder syncProcessorBuilder;
    @Nullable
    private LocalDateTime syncDate;

    /**
     * Constructor.
     *
     * @param context    Current Context
     * @param syncServer to use
     */
    SyncReaderHelper(@NonNull final Context context,
                     @NonNull final SyncServer syncServer) {
        this.syncServer = syncServer;
        this.syncProcessorBuilder = syncServer.createSyncProcessorBuilder(context);

        // set the defaults
        addRecordType(RecordType.Books, RecordType.Cover);

        setUpdateOption(this.syncServer.hasLastUpdateDateField()
                        ? DataReader.Updates.OnlyNewer
                        : DataReader.Updates.Skip);
    }

    /**
     * Get the location to read from.
     *
     * @return the SyncServer to use
     */
    @NonNull
    SyncServer getSyncServer() {
        return syncServer;
    }

    @NonNull
    Collection<SyncField> getSyncFields() {
        return syncProcessorBuilder.getSyncFields();
    }

    /**
     * Reset current usage back to defaults, and write to preferences.
     */
    void resetSyncProcessor() {
        syncProcessorBuilder.resetPreferences();
    }

    /**
     * Update the {@link SyncAction} for all keys.
     *
     * @param action to set
     */
    @SuppressWarnings("SameParameterValue")
    void setSyncAction(@NonNull final SyncAction action) {
        syncProcessorBuilder.setSyncAction(action);
    }

    @NonNull
    Bundle getExtraArgs() {
        return extraArgs;
    }

    /**
     * Get the optional sync-date (cut-off) for use with {@link DataReader.Updates#OnlyNewer}.
     *
     * @return date or {@code null}
     */
    @Nullable
    LocalDateTime getSyncDate() {
        return syncDate;
    }

    /**
     * If we want new-books-only {@link DataReader.Updates#Skip}
     * or new-books-and-updates {@link DataReader.Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     *
     * @param syncDate date
     */
    void setSyncDate(@Nullable final LocalDateTime syncDate) {
        this.syncDate = syncDate;
    }

    boolean isReadyToGo() {
        switch (syncServer) {
            case CalibreCS: {
                @Nullable
                final CalibreLibrary selected = extraArgs
                        .getParcelable(CalibreContentServer.BKEY_LIBRARY);
                return selected != null && selected.getTotalBooks() > 0;
            }
            case StripInfo:
                return true;

            default:
                throw new IllegalArgumentException(syncServer.toString());
        }
    }

    @NonNull
    protected DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull final Context context)
            throws DataReaderException,
                   CredentialsException,
                   CertificateException,
                   IOException {

        if (getRecordTypes().isEmpty()) {
            throw new IllegalArgumentException("no recordTypes set");
        }
        return syncServer.createReader(context,
                                       getRecordTypes(), syncProcessorBuilder, syncDate,
                                       getUpdateOption(),
                                       extraArgs);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderHelper{"
               + super.toString()
               + ", syncDate=" + syncDate
               + ", extraArgs=" + extraArgs
               + ", syncProcessorBuilder=" + syncProcessorBuilder
               + ", syncServer=" + syncServer
               + '}';
    }
}
