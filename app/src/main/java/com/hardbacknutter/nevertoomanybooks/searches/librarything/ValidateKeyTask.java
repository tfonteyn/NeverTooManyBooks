/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * Request a known valid ISBN from LT to see if the user key is valid.
 */
public class ValidateKeyTask
        extends VMTask<Integer> {

    private static final String TAG = "ValidateKeyTask";

    public void startTask() {
        execute(R.id.TASK_ID_LT_VALIDATE_KEY);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doWork() {
        Thread.currentThread().setName(TAG);
        final Context context = App.getTaskContext();

        final SearchEngine.CoverByIsbn ltm = new LibraryThingSearchEngine();
        final String fileSpec = ltm.searchCoverImageByIsbn(context, "0451451783", 0,
                                                           ImageFileInfo.Size.Small);
        if (fileSpec != null) {
            if (ImageUtils.isFileGood(new File(fileSpec))) {
                return R.string.lt_key_is_correct;
            } else {
                return R.string.lt_key_is_incorrect;
            }
        }

        return R.string.warning_image_not_found;
    }
}
