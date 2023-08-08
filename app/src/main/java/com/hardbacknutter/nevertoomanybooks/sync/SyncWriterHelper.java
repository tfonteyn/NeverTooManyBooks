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
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public class SyncWriterHelper
        extends DataWriterHelperBase<SyncWriterResults> {

    private static final String ERROR_SYNC_SERVER_NULL = "syncServer";

    /** Extra arguments for specific writers. The writer must define them. */
    private final Bundle extraArgs = ServiceLocator.getInstance().newBundle();
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Locale systemLocale;
    /** <strong>Where</strong> we write to. */
    @Nullable
    private SyncServer syncServer;
    /** If a book no longer exists on the server, should we delete the local book. */
    private boolean deleteLocalBooks;

    /**
     * Constructor.
     *
     * @param syncServer   to write to
     * @param systemLocale to use for ISO date parsing
     */
    SyncWriterHelper(@NonNull final SyncServer syncServer,
                     @NonNull final Locale systemLocale) {
        this.syncServer = syncServer;
        this.systemLocale = systemLocale;

        // set the default
        getRecordTypes().addAll(EnumSet.of(RecordType.Books,
                                           RecordType.Cover));
    }

    @NonNull
    SyncServer getSyncServer() {
        return Objects.requireNonNull(syncServer, ERROR_SYNC_SERVER_NULL);
    }

    void setSyncServer(@NonNull final SyncServer syncServer) {
        this.syncServer = syncServer;
    }

    @NonNull
    public Bundle getExtraArgs() {
        return extraArgs;
    }

    public boolean isDeleteLocalBooks() {
        return deleteLocalBooks;
    }

    void setDeleteLocalBooks(final boolean deleteLocalBooks) {
        this.deleteLocalBooks = deleteLocalBooks;
    }

    @NonNull
    @WorkerThread
    public SyncWriterResults write(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws DataWriterException,
                   CertificateException,
                   CredentialsException,
                   StorageException,
                   IOException {

        Objects.requireNonNull(syncServer, ERROR_SYNC_SERVER_NULL);

        try {
            dataWriter = syncServer.createWriter(context, this, systemLocale);
            return dataWriter.write(context, progressListener);
        } finally {
            close();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterHelper{"
               + super.toString()
               + ", syncServer=" + syncServer
               + ", deleteLocalBooks=" + deleteLocalBooks
               + ", extraArgs=" + extraArgs
               + '}';
    }
}
