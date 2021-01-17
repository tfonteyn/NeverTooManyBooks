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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

public class CalibreSingleFileDownload
        extends VMTask<Uri> {

    private Book mBook;
    private Uri mFolder;

    private CalibreContentServer mServer;

    public void init(@NonNull final CalibreContentServer server) {
        mServer = server;
    }

    public boolean start(@NonNull final Book book,
                         @NonNull final Uri folder) {
        mBook = book;
        mFolder = folder;

        // sanity check
        if (mBook.getString(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT).isEmpty()) {
            return false;
        }

        execute(R.id.TASK_ID_DOWNLOAD_SINGLE_FILE);
        return true;
    }

    @Nullable
    @Override
    protected Uri doWork(@NonNull final Context context)
            throws IOException {

        setIndeterminate(true);
        publishProgressStep(0, context.getString(R.string.progress_msg_please_wait));

        return mServer.getFile(context, mFolder, mBook, this);
    }
}
