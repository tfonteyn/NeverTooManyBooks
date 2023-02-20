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

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.org.json.JSONException;

class SingleFileDownloadTask
        extends MTask<Uri> {

    /** Log tag. */
    private static final String TAG = "SingleFileDownloadTask";

    @NonNull
    private final CalibreContentServer server;
    private Book book;
    private Uri folder;

    /**
     * Constructor.
     *
     * @param server to access
     */
    SingleFileDownloadTask(@NonNull final CalibreContentServer server) {
        super(R.id.TASK_ID_DOWNLOAD_SINGLE_FILE, TAG);
        this.server = server;
    }

    /**
     * Start the task to download the given book, storing it in the given folder.
     *
     * @param book   to download
     * @param folder to save to
     */
    public void download(@NonNull final Book book,
                         @NonNull final Uri folder) {
        this.book = book;
        this.folder = folder;

        if (BuildConfig.DEBUG /* always */) {
            if (!CalibreBook.isCalibreBook(book)) {
                throw new IllegalArgumentException("Missing Calibre keys");
            }
        }
        execute();
    }

    @Override
    public void cancel() {
        super.cancel();
        synchronized (server) {
            server.cancel();
        }
    }

    @NonNull
    @Override
    protected Uri doWork()
            throws IOException,
                   StorageException,
                   JSONException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_please_wait));

        if (!server.isMetaDataRead()) {
            server.readMetaData(context);
        }
        return server.fetchFile(context, book, folder, this);
    }
}
