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

import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.backup.common.DataWriter;
import com.hardbacknutter.nevertoomanybooks.backup.common.ExporterBase;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class SyncWriterHelper
        extends ExporterBase<SyncWriterResults> {

    /** Extra arguments for specific writers. The writer must define them. */
    private final Bundle mExtraArgs = new Bundle();

    /** <strong>Where</strong> we write to. */
    @Nullable
    private SyncServer mSyncServer;

    /** If a book no longer exists on the server, should we delete the local book. */
    private boolean mDeleteLocalBooks;

    /**
     * Constructor.
     */
    SyncWriterHelper(@NonNull final SyncServer syncServer) {
        super(EnumSet.of(RecordType.Books,
                         RecordType.Cover));
        mSyncServer = syncServer;
    }

    @NonNull
    public SyncServer getSyncServer() {
        return Objects.requireNonNull(mSyncServer, "mSyncServer");
    }

    public void setSyncServer(@NonNull final SyncServer syncServer) {
        mSyncServer = syncServer;
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

    @NonNull
    @WorkerThread
    public SyncWriterResults write(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws ExportException,
                   IOException,
                   StorageException,
                   CertificateException {

        Objects.requireNonNull(mSyncServer, "mSyncServer");

        try (DataWriter<SyncWriterResults> writer = mSyncServer.createWriter(context, this)) {
            return writer.write(context, progressListener);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterHelper{"
               + super.toString()
               + ", mSyncServer=" + mSyncServer
               + ", mDeleteLocalBooks=" + mDeleteLocalBooks
               + ", mExtraArgs=" + mExtraArgs
               + '}';
    }
}
