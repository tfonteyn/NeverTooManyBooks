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
package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class LibraryThingRegistrationViewModel
        extends ViewModel {

    private final ValidateKeyTask mValidateKeyTask = new ValidateKeyTask();

    @NonNull
    public LiveData<FinishedMessage<Exception>> onFailure() {
        return mValidateKeyTask.onFailure();
    }

    @NonNull
    public LiveData<FinishedMessage<Integer>> onCancelled() {
        return mValidateKeyTask.onCancelled();
    }

    @NonNull
    public LiveData<FinishedMessage<Integer>> onFinished() {
        return mValidateKeyTask.onFinished();
    }

    void validateKey() {
        mValidateKeyTask.connect();
    }

    /**
     * Request a known valid ISBN from LT to see if the user key is valid.
     */
    private static class ValidateKeyTask
            extends MTask<Integer> {

        /** Log tag. */
        private static final String TAG = "ValidateKeyTask";

        ValidateKeyTask() {
            super(R.id.TASK_ID_VALIDATE_CONNECTION, TAG);
        }

        void connect() {
            execute();
        }

        @NonNull
        @Override
        @WorkerThread
        protected Integer doWork(@NonNull final Context context)
                throws StorageException, SearchException, CredentialsException {

            final SearchEngine.CoverByIsbn ltm = (SearchEngine.CoverByIsbn) SearchEngineRegistry
                    .getInstance().createSearchEngine(SearchSites.LIBRARY_THING);
            final String fileSpec = ltm
                    .searchCoverByIsbn("0451451783", 0, ImageFileInfo.Size.Small);

            if (fileSpec != null) {
                final int result;
                final File file = new File(fileSpec);
                if (ImageUtils.isAcceptableSize(file)) {
                    result = R.string.lt_key_is_correct;
                } else {
                    result = R.string.lt_key_is_incorrect;
                }
                FileUtils.delete(file);
                return result;

            } else {
                // The sample ISBN code we use has become invalid...
                return R.string.warning_image_not_found;
            }
        }
    }
}
