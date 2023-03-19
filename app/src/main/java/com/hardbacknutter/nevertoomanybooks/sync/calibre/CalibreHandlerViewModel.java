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

import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

public class CalibreHandlerViewModel
        extends ViewModel {

    private SingleFileDownloadTask singleFileDownloadTask;

    private CalibreContentServer server;

    @Nullable
    private Book tmpBook;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    public void init(@NonNull final Context context)
            throws CertificateException {
        if (server == null) {
            server = new CalibreContentServer(context);
            singleFileDownloadTask = new SingleFileDownloadTask(server);
        }
    }

    @NonNull
    Book getAndResetTempBook() {
        final Book book = Objects.requireNonNull(tmpBook, "tmpBook");
        tmpBook = null;
        return book;
    }

    void setTempBook(@NonNull final Book book) {
        tmpBook = book;
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
    boolean existsLocally(@NonNull final Context context,
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
    Uri getDocumentUri(@NonNull final Context context,
                       @NonNull final Book book)
            throws FileNotFoundException {

        final Optional<Uri> optFolderUri = CalibreContentServer.getFolderUri(context);
        if (optFolderUri.isPresent()) {
            try {
                return server.getDocumentFile(context, book, optFolderUri.get(), false)
                             .getUri();
            } catch (@NonNull final IOException e) {
                // Keep it simple.
                throw new FileNotFoundException(optFolderUri.get().toString());
            }
        }
        throw new FileNotFoundException("Folder not configured");
    }

    /**
     * Start the task to download the given book, storing it in the given folder.
     *
     * @param book   to download
     * @param folder to save to
     */
    void startDownload(@NonNull final Book book,
                       @NonNull final Uri folder) {
        singleFileDownloadTask.download(book, folder);
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == singleFileDownloadTask.getTaskId()) {
            singleFileDownloadTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return singleFileDownloadTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Uri>>> onCancelled() {
        return singleFileDownloadTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception; {@link TaskResult#getResult()} will always
     *         return a valid {@link Throwable} and never {@code null}
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onFailure() {
        return singleFileDownloadTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Uri>>> onFinished() {
        return singleFileDownloadTask.onFinished();
    }
}
