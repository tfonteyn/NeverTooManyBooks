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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReadMetaDataTask;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.common.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;

public class ImportViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    private final ArchiveReadMetaDataTask mReadMetaDataTask = new ArchiveReadMetaDataTask();
    private final ArchiveReaderTask mReaderTask = new ArchiveReaderTask();

    /** The import configuration. */
    @Nullable
    private ImportHelper mImportHelper;
    @Nullable
    private ArchiveMetaData mMetaData;

    @NonNull
    ImportHelper createImportHelper(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws InvalidArchiveException, FileNotFoundException {

        mImportHelper = ImportHelper.newInstance(context, uri);
        return mImportHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return mImportHelper != null;
    }

    /**
     * The caller <strong>must</strong> ensure that the helper has been created previously.
     *
     * @return the helper
     *
     * @throws NullPointerException as a bug
     */
    @NonNull
    ImportHelper getImportHelper() {
        return Objects.requireNonNull(mImportHelper, "mImportHelper");
    }

    @Override
    protected void onCleared() {
        mReadMetaDataTask.cancel();
        mReaderTask.cancel();
        super.onCleared();
    }

    public void readMetaData() {
        Objects.requireNonNull(mImportHelper, "mImportHelper");
        mReadMetaDataTask.start(mImportHelper);
    }

    @NonNull
    public LiveData<FinishedMessage<ArchiveMetaData>> onMetaDataRead() {
        return mReadMetaDataTask.onFinished();
    }

    @NonNull
    public LiveData<FinishedMessage<Exception>> onMetaDataFailure() {
        return mReadMetaDataTask.onFailure();
    }

    @Nullable
    ArchiveMetaData getMetaData() {
        return mMetaData;
    }

    void setMetaData(@Nullable final ArchiveMetaData metaData) {
        mMetaData = metaData;
    }

    /**
     * {@link ArchiveReader#BKEY_RESULTS}: {@link ImportResults}
     */
    @Override
    @NonNull
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @NonNull
    Intent onImportFinished(@NonNull final ImportResults result) {
        mResultIntent.putExtra(ArchiveReader.BKEY_RESULTS, result);
        return mResultIntent;
    }

    public boolean isNewBooksOnly() {
        //noinspection ConstantConditions
        return mImportHelper.getUpdateOption() == ImportHelper.Updates.Skip;
    }

    public void setNewBooksOnly() {
        //noinspection ConstantConditions
        mImportHelper.setUpdateOption(ImportHelper.Updates.Skip);
    }

    public boolean isAllBooks() {
        //noinspection ConstantConditions
        return mImportHelper.getUpdateOption() == ImportHelper.Updates.Overwrite;
    }

    public void setAllBooks() {
        //noinspection ConstantConditions
        mImportHelper.setUpdateOption(ImportHelper.Updates.Overwrite);
    }

    public boolean isNewAndUpdatedBooks() {
        //noinspection ConstantConditions
        return mImportHelper.getUpdateOption() == ImportHelper.Updates.OnlyNewer;
    }

    public void setNewAndUpdatedBooks() {
        //noinspection ConstantConditions
        mImportHelper.setUpdateOption(ImportHelper.Updates.OnlyNewer);
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    boolean isReadyToGo() {
        return mMetaData != null;
    }

    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mReaderTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<ImportResults>> onImportCancelled() {
        return mReaderTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onImportFailure() {
        return mReaderTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<ImportResults>> onImportFinished() {
        return mReaderTask.onFinished();
    }

    void startImport() {
        Objects.requireNonNull(mImportHelper, "mImportHelper");
        mReaderTask.start(mImportHelper);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mReaderTask.getTaskId()) {
            mReaderTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
