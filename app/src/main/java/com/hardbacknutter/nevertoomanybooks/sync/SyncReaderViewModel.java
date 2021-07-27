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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;

public class SyncReaderViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    private final SyncReadMetaDataTask mReadMetaDataTask = new SyncReadMetaDataTask();
    private final SyncReaderTask mReaderTask = new SyncReaderTask();
    private SyncReaderConfig mConfig;

    @Nullable
    private SyncServer mSyncServer;
    @Nullable
    private SyncReaderMetaData mMetaData;

    /**
     * Pseudo constructor.
     */
    public void init(@NonNull final Bundle args) {
        if (mSyncServer == null) {
            mSyncServer = Objects.requireNonNull(args.getParcelable(SyncServer.BKEY_SITE));
            if (mSyncServer.hasLastUpdateDateField()) {
                mConfig = new SyncReaderConfig(SyncReaderConfig.Updates.OnlyNewer);
            } else {
                mConfig = new SyncReaderConfig(SyncReaderConfig.Updates.Skip);
            }
        }
    }

    @Override
    protected void onCleared() {
        mReadMetaDataTask.cancel();
        mReaderTask.cancel();
        super.onCleared();
    }

    @NonNull
    public SyncServer getSyncServer() {
        return Objects.requireNonNull(mSyncServer, "mSyncServer");
    }

    @NonNull
    SyncReaderConfig getConfig() {
        return mConfig;
    }


    public void readMetaData() {
        Objects.requireNonNull(mSyncServer, "mSyncServer");
        mReadMetaDataTask.start(mSyncServer, mConfig);
    }

    @NonNull
    public LiveData<FinishedMessage<SyncReaderMetaData>> onMetaDataRead() {
        return mReadMetaDataTask.onFinished();
    }

    @NonNull
    public LiveData<FinishedMessage<Exception>> onMetaDataFailure() {
        return mReadMetaDataTask.onFailure();
    }

    @Nullable
    SyncReaderMetaData getMetaData() {
        return mMetaData;
    }

    @CallSuper
    public void setMetaData(@Nullable final SyncReaderMetaData metaData) {
        mMetaData = metaData;
    }

    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @NonNull
    Intent onImportFinished(@SuppressWarnings("TypeMayBeWeakened")
                            @NonNull final ReaderResults result) {
        mResultIntent.putExtra(SyncReader.BKEY_RESULTS, result);
        return mResultIntent;
    }

    /** Wrapper to handle {@link SyncReaderConfig.Updates}. */
    public boolean isNewBooksOnly() {
        return mConfig.getUpdateOption() == SyncReaderConfig.Updates.Skip;
    }

    /** Wrapper to handle {@link SyncReaderConfig.Updates}. */
    public void setNewBooksOnly() {
        mConfig.setUpdateOption(SyncReaderConfig.Updates.Skip);
    }

    /** Wrapper to handle {@link SyncReaderConfig.Updates}. */
    public boolean isAllBooks() {
        return mConfig.getUpdateOption() == SyncReaderConfig.Updates.Overwrite;
    }

    /** Wrapper to handle {@link SyncReaderConfig.Updates}. */
    public void setAllBooks() {
        mConfig.setUpdateOption(SyncReaderConfig.Updates.Overwrite);
    }

    /** Wrapper to handle {@link SyncReaderConfig.Updates}. */
    public boolean isNewAndUpdatedBooks() {
        return mConfig.getUpdateOption() == SyncReaderConfig.Updates.OnlyNewer;
    }

    /**
     * Wrapper to handle {@link SyncReaderConfig.Updates}.
     *
     * @see SyncReaderConfig for docs
     */
    public void setNewAndUpdatedBooks() {
        mConfig.setUpdateOption(SyncReaderConfig.Updates.OnlyNewer);
    }

    @Nullable
    public LocalDateTime getSyncDate() {
        return mConfig.getSyncDate();
    }

    /**
     * If we want new-books-only {@link SyncReaderConfig.Updates#Skip)
     * or new-books-and-updates {@link SyncReaderConfig.Updates#OnlyNewer},
     * we limit the fetch to the sync-date.
     */
    public void setSyncDate(@Nullable final LocalDateTime syncDate) {
        mConfig.setSyncDate(syncDate);
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    boolean isReadyToGo() {
        Objects.requireNonNull(mSyncServer, "mSyncServer");

        switch (mSyncServer) {
            case CalibreCS: {
                @Nullable
                final CalibreLibrary selectedLibrary =
                        mConfig.getExtraArgs().getParcelable(CalibreContentServer.BKEY_LIBRARY);
                return selectedLibrary != null && selectedLibrary.getTotalBooks() > 0;
            }
            case StripInfo:
                return true;

            default:
                throw new IllegalArgumentException();
        }
    }

    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mReaderTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<ReaderResults>> onImportCancelled() {
        return mReaderTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onImportFailure() {
        return mReaderTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<ReaderResults>> onImportFinished() {
        return mReaderTask.onFinished();
    }

    void startImport() {
        Objects.requireNonNull(mSyncServer, "mSyncServer");
        mReaderTask.start(mSyncServer, mConfig);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mReaderTask.getTaskId()) {
            mReaderTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
