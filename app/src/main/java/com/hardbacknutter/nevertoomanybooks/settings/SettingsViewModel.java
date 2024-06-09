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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.StorageMoverTask;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Shared on the Activity level as it's needed by more than 1 Fragment.
 * <p>
 * Note that {@link #forceRebuildBooklist} and {@link #requiresActivityRecreation}
 * are somewhat overlapping in functionality.
 */
@SuppressWarnings("WeakerAccess")
public class SettingsViewModel
        extends ViewModel {

    private static final String TAG = "SettingsViewModel";

    /** Passed in by the startup routines, indicating the storage device was not found. */
    public static final String BKEY_MISSING_STORAGE_VOLUME = TAG + ":msv";


    private final StorageMoverTask storageMoverTask = new StorageMoverTask();
    private boolean requiresActivityRecreation;

    private boolean forceRebuildBooklist;

    private String[] uiLangNames;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (uiLangNames == null) {
            final AppLocale appLocale = ServiceLocator.getInstance().getAppLocale();

            final String[] uiLangCodes = context.getResources()
                                                .getStringArray(R.array.pv_ui_language);
            uiLangNames = new String[uiLangCodes.length];
            uiLangNames[0] = context.getString(R.string.pt_ui_system_locale);
            for (int i = 1; i < uiLangCodes.length; i++) {
                final Locale locale = appLocale.getLocale(context, uiLangCodes[i]).orElseThrow();
                uiLangNames[i] = locale.getDisplayName(locale);
            }
        }
    }

    @NonNull
    public String[] getUiLangNames() {
        return uiLangNames;
    }

    boolean isRequiresActivityRecreation() {
        return requiresActivityRecreation;
    }

    public void setOnBackRequiresActivityRecreation() {
        requiresActivityRecreation = true;
    }

    public boolean isForceRebuildBooklist() {
        return forceRebuildBooklist;
    }

    public void setForceRebuildBooklist() {
        this.forceRebuildBooklist = true;
    }

    boolean moveData(@NonNull final Context context,
                     final int sourceIndex,
                     final int destIndex) {

        storageMoverTask.setDirs(context, sourceIndex, destIndex);
        try {
            if (storageMoverTask.checkSpace()) {
                storageMoverTask.start();
                return true;
            }
        } catch (@NonNull final IOException e) {
            // log but ignore, just report we can't move
            LoggerFactory.getLogger().e(TAG, e);
        }
        return false;
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return storageMoverTask.onProgress();
    }

    @NonNull
    LiveData<LiveDataEvent<Integer>> onMoveCancelled() {
        return storageMoverTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    LiveData<LiveDataEvent<Throwable>> onMoveFailure() {
        return storageMoverTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<Integer>> onMoveFinished() {
        return storageMoverTask.onFinished();
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == storageMoverTask.getTaskId()) {
            storageMoverTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
