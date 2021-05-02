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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbGetBookTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbGetEditionsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class EditBookTocViewModel
        extends ViewModel {

    private final IsfdbGetEditionsTask mIsfdbGetEditionsTask = new IsfdbGetEditionsTask();
    private final IsfdbGetBookTask mIsfdbGetBookTask = new IsfdbGetBookTask();

    void searchByIsbn(@NonNull final ISBN isbn) {
        mIsfdbGetEditionsTask.search(isbn);
    }

    @NonNull
    LiveData<FinishedMessage<List<Edition>>> onIsfdbEditions() {
        return mIsfdbGetEditionsTask.onFinished();
    }

    @NonNull
    LiveData<FinishedMessage<List<Edition>>> onIsfdbEditionsCancelled() {
        return mIsfdbGetEditionsTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onIsfdbEditionsFailure() {
        return mIsfdbGetEditionsTask.onFailure();
    }


    void searchBook(final long isfdbId) {
        mIsfdbGetBookTask.search(isfdbId);
    }

    @NonNull
    LiveData<FinishedMessage<Bundle>> onIsfdbBook() {
        return mIsfdbGetBookTask.onFinished();
    }

    @NonNull
    LiveData<FinishedMessage<Bundle>> onIsfdbBookCancelled() {
        return mIsfdbGetBookTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onIsfdbBookFailure() {
        return mIsfdbGetBookTask.onFailure();
    }

    void searchEdition(@NonNull final Edition edition) {
        mIsfdbGetBookTask.search(edition);
    }
}
