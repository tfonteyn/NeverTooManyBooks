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

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.org.json.JSONException;

class SingleFileDownloadTask
        extends MTask<Uri> {

    /** Log tag. */
    private static final String TAG = "SingleFileDownloadTask";

    private final CalibreContentServer mServer;
    private Book mBook;
    private Uri mFolder;

    /**
     * Constructor.
     *
     * @param server to access
     */
    SingleFileDownloadTask(@NonNull final CalibreContentServer server) {
        super(R.id.TASK_ID_DOWNLOAD_SINGLE_FILE, TAG);
        mServer = server;
    }

    public boolean download(@NonNull final Book book,
                            @NonNull final Uri folder) {
        mBook = book;
        mFolder = folder;

        // sanity check
        if (mBook.getString(DBKeys.KEY_CALIBRE_BOOK_MAIN_FORMAT).isEmpty()) {
            return false;
        }
        return execute();
    }

    @NonNull
    @Override
    protected Uri doWork(@NonNull final Context context)
            throws IOException, JSONException {

        setIndeterminate(true);
        publishProgress(0, context.getString(R.string.progress_msg_please_wait));

        if (!mServer.isMetaDataRead()) {
            mServer.readMetaData(context);
        }
        return mServer.getFile(context, mFolder, mBook, this);
    }
}
