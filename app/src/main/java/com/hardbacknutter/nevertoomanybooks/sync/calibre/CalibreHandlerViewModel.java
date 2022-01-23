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

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;

public class CalibreHandlerViewModel
        extends ViewModel {

    private SingleFileDownloadTask mSingleFileDownloadTask;

    private CalibreContentServer mServer;

    @Nullable
    private Book mTempBook;

    public void init(@NonNull final Context context)
            throws CertificateException, SSLException {
        if (mServer == null) {
            mServer = new CalibreContentServer(context);
            mSingleFileDownloadTask = new SingleFileDownloadTask(mServer);
        }
    }

    @NonNull
    Book getAndResetTempBook() {
        final Book book = Objects.requireNonNull(mTempBook, "mTempBook");
        mTempBook = null;
        return book;
    }

    void setTempBook(@NonNull final Book book) {
        mTempBook = book;
    }

    /**
     * Check if we have the book in the local folder.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param context Current context
     * @param book    to check
     *
     * @return {@code true} if we have the file
     */
    public boolean existsLocally(@NonNull final Context context,
                                 @NonNull final Book book) {
        try {
            getDocumentUri(context, book);
            return true;

        } catch (@NonNull final FileNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Get the book file from the local folder.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param context Current context
     * @param book    to get
     *
     * @return book
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    public Uri getDocumentUri(@NonNull final Context context,
                              @NonNull final Book book)
            throws FileNotFoundException {

        final Optional<Uri> optFolderUri = CalibreContentServer.getFolderUri(context);
        if (optFolderUri.isPresent()) {
            try {
                return mServer.getDocumentFile(context, book, optFolderUri.get(), false)
                              .getUri();
            } catch (@NonNull final IOException e) {
                // Keep it simple.
                throw new FileNotFoundException(optFolderUri.get().toString());
            }
        }
        throw new FileNotFoundException("Folder not configured");
    }

    /**
     * Start the download task.
     *
     * @param book   to download
     * @param folder to store the result
     */
    public void startDownload(@NonNull final Book book,
                              @NonNull final Uri folder) {
        mSingleFileDownloadTask.download(book, folder);
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == mSingleFileDownloadTask.getTaskId()) {
            mSingleFileDownloadTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
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
}
