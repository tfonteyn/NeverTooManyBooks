/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;

/**
 * Build the dedicated SharedPreferences file with the language mappings.
 * Only build once per Locale.
 */
public class BuildLanguageMappingsTask
        extends LTask<Boolean>
        implements StartupViewModel.StartupTask {

    /** Log tag. */
    private static final String TAG = "BuildLanguageMappings";

    public BuildLanguageMappingsTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_BUILD_LANG_MAP, TAG, taskListener);
    }

    @Override
    @UiThread
    public void start() {
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        ServiceLocator.getInstance().getLanguages().createLanguageMappingCache(context);
        return true;
    }
}
