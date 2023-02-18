/*
 * @Copyright 2018-2022 HardBackNutter
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
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class StorageMoverTask
        extends MTask<Integer> {

    private static final String TAG = "StorageMoverTask";

    /** Add 10% overhead when checking required space. */
    private static final float OVERHEAD = 1.1f;

    private int destIndex;
    private File sourceDir;
    private File destDir;

    /**
     * Constructor.
     */
    public StorageMoverTask() {
        super(R.id.TASK_ID_VOLUME_MOVER, TAG);
    }

    public void setDirs(@NonNull final Context context,
                        final int sourceIndex,
                        final int destIndex) {
        this.destIndex = destIndex;

        final File[] dirs = context.getExternalFilesDirs(null);
        sourceDir = dirs[sourceIndex];
        destDir = dirs[destIndex];
    }

    public boolean checkSpace()
            throws IOException {
        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(sourceDir);
            Objects.requireNonNull(destDir);
        }

        final long usedSpace = FileUtils.getUsedSpace(sourceDir, null);
        final long freeSpace = FileUtils.getFreeSpace(destDir);
        return freeSpace > (usedSpace * OVERHEAD);
    }

    public void start() {
        if (BuildConfig.DEBUG /* always */) {
            Objects.requireNonNull(sourceDir);
            Objects.requireNonNull(destDir);
        }

        execute();
    }

    @Nullable
    @Override
    protected Integer doWork(@NonNull final Context context)
            throws IOException {

        publishProgress(0, context.getString(R.string.progress_msg_please_wait));

        // two steps, so we don't delete anything if the copy fails or is cancelled
        TaskFileUtils.copyDirectory(sourceDir, destDir, this);
        if (isCancelled()) {
            return -1;
        }

        publishProgress(0, context.getString(R.string.progress_msg_cleaning_up));

        // Delete(File) swallows all exceptions as none are deemed critical.
        TaskFileUtils.deleteDirectory(sourceDir, null, this);
        if (isCancelled()) {
            return -1;
        }

        return destIndex;
    }
}
