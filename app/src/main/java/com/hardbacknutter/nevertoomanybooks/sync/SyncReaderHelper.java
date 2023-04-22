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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;

public final class SyncReaderHelper
        extends DataReaderHelperBase<SyncReaderMetaData, ReaderResults> {

    /** <strong>Where</strong> we read from. */
    @NonNull
    private final SyncServer syncServer;
    /** Extra arguments for specific readers. The reader must define them. */
    private final Bundle extraArgs = ServiceLocator.getInstance().newBundle();
    @SuppressWarnings("FieldNotUsedInToString")
    private final Locale systemLocale;
    /** <strong>How</strong> to handle individual fields. Can be {@code null}. aka unused. */
    @Nullable
    private SyncReaderProcessor syncProcessor;
    @Nullable
    private LocalDateTime syncDate;

    /**
     * Constructor.
     *
     * @param syncServer   to use
     * @param systemLocale to use for ISO date parsing
     */
    SyncReaderHelper(@NonNull final SyncServer syncServer,
                     @NonNull final Locale systemLocale) {
        this.syncServer = syncServer;
        this.systemLocale = systemLocale;

        // set the defaults
        addRecordType(EnumSet.of(RecordType.Books,
                                 RecordType.Cover));

        setUpdateOption(this.syncServer.hasLastUpdateDateField()
                        ? DataReader.Updates.OnlyNewer
                        : DataReader.Updates.Skip);
    }

    /**
     * Get the location to read from.
     *
     * @return the syncserver to use
     */
    @NonNull
    SyncServer getSyncServer() {
        return syncServer;
    }

    @Nullable
    public SyncReaderProcessor getSyncProcessor() {
        return syncProcessor;
    }

    public void setSyncProcessor(@Nullable final SyncReaderProcessor syncProcessor) {
        this.syncProcessor = syncProcessor;
    }

    @NonNull
    public Bundle getExtraArgs() {
        return extraArgs;
    }

    /**
     * Get the optional sync-date (cut-off) for use with {@link DataReader.Updates#OnlyNewer}.
     *
     * @return date or {@code null}
     */
    @Nullable
    public LocalDateTime getSyncDate() {
        return syncDate;
    }

    /**
     * If we want new-books-only {@link DataReader.Updates#Skip}
     * or new-books-and-updates {@link DataReader.Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     */
    void setSyncDate(@Nullable final LocalDateTime syncDate) {
        this.syncDate = syncDate;
    }

    @NonNull
    protected DataReader<SyncReaderMetaData, ReaderResults> createReader(
            @NonNull final Context context)
            throws DataReaderException,
                   CredentialsException,
                   CertificateException,
                   IOException {
        return syncServer.createReader(context, systemLocale, this);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderHelper{"
               + super.toString()
               + ", syncDate=" + syncDate
               + ", extraArgs=" + extraArgs
               + ", syncProcessor=" + syncProcessor
               + ", syncServer=" + syncServer
               + '}';
    }
}
