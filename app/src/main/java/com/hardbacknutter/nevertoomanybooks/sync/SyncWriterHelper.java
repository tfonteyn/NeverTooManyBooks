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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterException;
import com.hardbacknutter.nevertoomanybooks.io.DataWriterHelperBase;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class SyncWriterHelper
        extends DataWriterHelperBase<SyncWriterResults> {

    /** Extra arguments for specific writers. The writer must define them. */
    private final Bundle extraArgs = ServiceLocator.newBundle();

    /** <strong>Where</strong> we write to. */
    @Nullable
    private SyncServer syncServer;

    /** If a book no longer exists on the server, should we delete the local book. */
    private boolean deleteLocalBooks;

    /**
     * Constructor.
     */
    SyncWriterHelper(@NonNull final SyncServer syncServer) {
        this.syncServer = syncServer;

        // set the default
        addRecordType(EnumSet.of(RecordType.Books,
                                 RecordType.Cover));
    }

    @NonNull
    public SyncServer getSyncServer() {
        return Objects.requireNonNull(syncServer, "mSyncServer");
    }

    public void setSyncServer(@NonNull final SyncServer syncServer) {
        this.syncServer = syncServer;
    }

    @NonNull
    public Bundle getExtraArgs() {
        return extraArgs;
    }

    public boolean isDeleteLocalBooks() {
        return deleteLocalBooks;
    }

    public void setDeleteLocalBooks(final boolean deleteLocalBooks) {
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

        Objects.requireNonNull(syncServer, "mSyncServer");

        try {
            mDataWriter = syncServer.createWriter(context, this);
            return mDataWriter.write(context, progressListener);
        } finally {
            synchronized (this) {
                if (mDataWriter != null) {
                    mDataWriter.close();
                    mDataWriter = null;
                }
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterHelper{"
               + super.toString()
               + ", mSyncServer=" + syncServer
               + ", mDeleteLocalBooks=" + deleteLocalBooks
               + ", mExtraArgs=" + extraArgs
               + '}';
    }
}
