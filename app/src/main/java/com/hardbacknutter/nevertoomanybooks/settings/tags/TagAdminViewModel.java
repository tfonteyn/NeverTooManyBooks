/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.tags;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

@SuppressWarnings("WeakerAccess")
public class TagAdminViewModel
        extends ViewModel {

    private static final String TAG = "TagAdminViewModel";

    private final MapperTask mapperTask = new MapperTask();
    private boolean modified;

    @Override
    protected void onCleared() {
        mapperTask.cancel();
        super.onCleared();
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mapperTask.onProgress();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<LiveDataEvent<Throwable>> onTagMapperFailure() {
        return mapperTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<Integer>> onTagMapperCancelled() {
        return mapperTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<Integer>> onTagMapperFinished() {
        return mapperTask.onFinished();
    }

    void startTagMapper() {
        mapperTask.start();
    }

    void cancelTagMapper() {
        mapperTask.cancel();
    }

    boolean isModified() {
        return modified;
    }

    void setModified() {
        this.modified = true;
    }

    private static class MapperTask
            extends MTask<Integer> {

        MapperTask() {
            super(R.id.TASK_ID_TAG_MAPPER, TAG);
        }

        void start() {
            execute();
        }

        @NonNull
        @Override
        protected Integer doWork()
                throws DaoWriteException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

            // TODO: allow progress messages
            return ServiceLocator.getInstance().getBookDao().applyTagMappings(context, locale);
        }
    }
}
