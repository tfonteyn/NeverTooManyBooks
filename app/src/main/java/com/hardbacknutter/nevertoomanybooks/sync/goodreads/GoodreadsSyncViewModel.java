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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;

public class GoodreadsSyncViewModel
        extends GoodreadsAuthenticationViewModel {

    private final ImportTask mImportTask = new ImportTask(mTaskListener);
    private final SendBooksTask mSendBooksTask = new SendBooksTask(mTaskListener);

    void startImport(final boolean sync) {
        mImportTask.startImport(sync);
    }

    void startSend(@SuppressWarnings("SameParameterValue") final boolean fromLastBookId,
                   final boolean updatesOnly) {
        mSendBooksTask.send(fromLastBookId, updatesOnly);
    }

    void connectProgressDialog(@IdRes final int taskId,
                               @NonNull final ProgressDialogFragment dialog) {
        if (taskId == R.id.TASK_ID_GR_SEND_BOOKS) {
            dialog.setCanceller(mSendBooksTask);
        } else if (taskId == R.id.TASK_ID_GR_IMPORT) {
            dialog.setCanceller(mImportTask);
        } else {
            super.connectProgressDialog(taskId, dialog);
        }
    }
}
