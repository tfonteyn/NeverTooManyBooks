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
package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

@SuppressWarnings("WeakerAccess")
public class CoverHandlerViewModel
        extends ViewModel {

    private final TransformationTask transformationTask = new TransformationTask();
    /** Used to display a tip dialog when the user rotates a camera image. */
    private boolean showTipAboutRotating = true;

    @NonNull
    LiveData<LiveDataEvent<TransformationTask.TransformedData>> onFinished() {
        return transformationTask.onFinished();
    }

    /**
     * Execute the given transformation.
     *
     * @param transformation to run
     * @param destFile       file to write to
     */
    void execute(@NonNull final Transformation transformation,
                 @NonNull final File destFile) {
        transformationTask.transform(transformation, destFile, CoverHandler.NextAction.Done);
    }

    /**
     * Execute the given transformation.
     *
     * @param transformation to run
     * @param destFile       file to write to
     * @param action         What action should we take after we're done?
     */
    void execute(@NonNull final Transformation transformation,
                 @NonNull final File destFile,
                 @NonNull final CoverHandler.NextAction action) {
        transformationTask.transform(transformation, destFile, action);
    }

    boolean isShowTipAboutRotating() {
        return showTipAboutRotating;
    }

    void setShowTipAboutRotating(final boolean show) {
        showTipAboutRotating = show;
    }
}
