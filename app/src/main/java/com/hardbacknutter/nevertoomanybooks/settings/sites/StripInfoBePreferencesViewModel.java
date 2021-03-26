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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;

public class StripInfoBePreferencesViewModel
        extends ViewModel {

    private final ValidateConnectionTask mValidateConnectionTask = new ValidateConnectionTask();

    @Override
    protected void onCleared() {
        mValidateConnectionTask.cancel(true);
        super.onCleared();
    }

    @NonNull
    LiveData<FinishedMessage<Boolean>> onConnectionSuccessful() {
        return mValidateConnectionTask.onFinished();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onConnectionFailed() {
        return mValidateConnectionTask.onFailure();
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
         * @param context a localised application context
         *
         * @return {@code true} on success
         *
         * @throws IOException on failures
         */
        @Nullable
        @Override
        protected Boolean doWork(@NonNull final Context context)
                throws IOException {
            final String url = SearchEngineRegistry
                    .getInstance()
                    .getByEngineId(SearchSites.STRIP_INFO_BE)
                    .getSiteUrl();

            new StripInfoAuth(url).login();

            return true;
        }
    }
}
