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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

public class CalibreHandlerViewModel
        extends ViewModel {

    private SingleFileDownloadTask mSingleFileDownloadTask;

    public void init(@NonNull final CalibreContentServer server) {
        if (mSingleFileDownloadTask == null) {
            mSingleFileDownloadTask = new SingleFileDownloadTask(server);
        }
    }

    @NonNull
    public LiveData<ProgressMessage> onProgress() {
        return mSingleFileDownloadTask.onProgressUpdate();
    }

    @NonNull
    public LiveData<FinishedMessage<Uri>> onCancelled() {
        return mSingleFileDownloadTask.onCancelled();
    }

    @NonNull
    public LiveData<FinishedMessage<Exception>> onFailure() {
        return mSingleFileDownloadTask.onFailure();
    }

    @NonNull
    public LiveData<FinishedMessage<Uri>> onFinished() {
        return mSingleFileDownloadTask.onFinished();
    }

    public boolean download(@NonNull final Book book,
                            @NonNull final Uri folder) {
        return mSingleFileDownloadTask.download(book, folder);
    }

    void connectProgressDialog(@NonNull final ProgressDialogFragment dialog) {
        dialog.setCanceller(mSingleFileDownloadTask);
    }
}
