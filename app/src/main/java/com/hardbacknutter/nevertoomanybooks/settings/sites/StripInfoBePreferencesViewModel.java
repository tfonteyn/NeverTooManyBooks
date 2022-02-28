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
package com.hardbacknutter.nevertoomanybooks.settings.sites;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

public class StripInfoBePreferencesViewModel
        extends ViewModel {

    private final ValidateConnectionTask mValidateConnectionTask = new ValidateConnectionTask();

    @Override
    protected void onCleared() {
        mValidateConnectionTask.cancel();
        super.onCleared();
    }

    /**
     * Get two arrays with matching name and id's for all Bookshelves.
     *
     * @return Pair of (entries,entryValues)
     */
    @NonNull
    Pair<CharSequence[], CharSequence[]> getBookshelves() {
        final ArrayList<Bookshelf> all;
        all = ServiceLocator.getInstance().getBookshelfDao().getAll();
        final CharSequence[] entries = new CharSequence[all.size()];
        final CharSequence[] entryValues = new CharSequence[all.size()];

        int i = 0;
        for (final Bookshelf bookshelf : all) {
            entries[i] = bookshelf.getName();
            entryValues[i] = String.valueOf(bookshelf.getId());
            i++;
        }

        return new Pair<>(entries, entryValues);
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Boolean>>> onConnectionSuccessful() {
        return mValidateConnectionTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onConnectionFailed() {
        return mValidateConnectionTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mValidateConnectionTask.onProgress();
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mValidateConnectionTask.getTaskId()) {
            mValidateConnectionTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    void validateConnection() {
        mValidateConnectionTask.connect();
    }

    /**
     * A simple connection validation.
     */
    private static class ValidateConnectionTask
            extends MTask<Boolean> {

        /** Log tag. */
        private static final String TAG = "StripInfoValidateConTask";

        ValidateConnectionTask() {
            super(R.id.TASK_ID_VALIDATE_CONNECTION, TAG);
        }

        void connect() {
            execute();
        }

        /**
         * Make a test connection.
         *
         * @param context The localised Application context
         *
         * @return {@code true} on success
         *
         * @throws IOException on failures
         */
        @Nullable
        @Override
        protected Boolean doWork(@NonNull final Context context)
                throws IOException, CredentialsException {
            publishProgress(0, context.getString(R.string.progress_msg_connecting));
            final String url = SearchEngineRegistry
                    .getInstance()
                    .getByEngineId(SearchSites.STRIP_INFO_BE)
                    .getHostUrl();

            new StripInfoAuth(url).login();

            return true;
        }
    }
}
