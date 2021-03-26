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

import com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;

public class GoodreadsHandlerViewModel
        extends GoodreadsAuthenticationViewModel {

    private final SendOneBookTask mSendOneBookTask = new SendOneBookTask(mTaskListener);

    void sendBook(final long bookId) {
        mSendOneBookTask.send(bookId);
    }

    void linkTaskWithDialog(@IdRes final int taskId,
                            @NonNull final ProgressDialogFragment dialog) {
        if (taskId == mSendOneBookTask.getTaskId()) {
            dialog.setCanceller(mSendOneBookTask);

        } else {
            super.linkTaskWithDialog(taskId, dialog);
        }
    }
}
