/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.AltEditionIsfdb;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbGetBookTask;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbGetEditionsTask;

@SuppressWarnings("WeakerAccess")
public class IsfdbTocSearchViewModel
        extends ViewModel {

    private final IsfdbGetEditionsTask isfdbGetEditionsTask = new IsfdbGetEditionsTask();
    private final IsfdbGetBookTask isfdbGetBookTask = new IsfdbGetBookTask();

    void searchByIsbn(@NonNull final ISBN isbn) {
        isfdbGetEditionsTask.search(isbn);
    }

    @NonNull
    LiveData<LiveDataEvent<List<AltEditionIsfdb>>> onIsfdbEditions() {
        return isfdbGetEditionsTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<List<AltEditionIsfdb>>> onIsfdbEditionsCancelled() {
        return isfdbGetEditionsTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onIsfdbEditionsFailure() {
        return isfdbGetEditionsTask.onFailure();
    }


    void searchBook(final long isfdbId) {
        isfdbGetBookTask.search(isfdbId);
    }

    @NonNull
    LiveData<LiveDataEvent<Book>> onIsfdbBook() {
        return isfdbGetBookTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<Book>> onIsfdbBookCancelled() {
        return isfdbGetBookTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onIsfdbBookFailure() {
        return isfdbGetBookTask.onFailure();
    }

    void searchEdition(@NonNull final AltEditionIsfdb edition) {
        isfdbGetBookTask.search(edition);
    }
}
