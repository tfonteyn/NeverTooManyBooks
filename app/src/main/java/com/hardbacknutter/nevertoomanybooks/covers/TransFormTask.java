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

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

/**
 * Note that by default this task <strong>only</strong> decodes the file to a Bitmap.
 * <ol>
 * <li>To scale the input, call one of the {@link Transformation#setScale} methods.</li>
 * <li>For rotation based on the input, call {@link Transformation#setSurfaceRotation}.</li>
 * <li>For specific rotation, call {@link Transformation#setRotation}.
 *     Can be combined with input-rotation</li>
 * </ol>
 * before executing this task.
 * <p>
 * The transformation is done "in-place", i.e the srcFile is overwritten with the result.
 */
public class TransFormTask
        extends MTask<TransFormTask.TransformedData> {

    /** Log tag. */
    private static final String TAG = "TransFormTask";

    private Transformation transformation;
    private File destFile;
    private CoverHandler.NextAction nextAction;

    TransFormTask() {
        super(R.id.TASK_ID_IMAGE_TRANSFORMATION, TAG);
    }

    void transform(@NonNull final Transformation transformation,
                   @NonNull final File destFile,
                   @NonNull final CoverHandler.NextAction action) {
        this.transformation = transformation;
        this.destFile = destFile;
        nextAction = action;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected TransformedData doWork() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        if (transformation.transform().isPresent()) {
            try {
                final Bitmap bitmap =
                        ImageUtils.writeFile(transformation.transform().get(), destFile);
                if (bitmap != null) {
                    return new TransformedData(bitmap, destFile, nextAction);
                }
            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Bitmap save FAILED", e);
                }
            }
        }

        return new TransformedData(null, destFile, nextAction);
    }

    /**
     * Value class with the results.
     */
    static class TransformedData {

        @Nullable
        private final Bitmap bitmap;
        @NonNull
        private final File file;
        @NonNull
        private final CoverHandler.NextAction nextAction;

        /**
         * Constructor.
         *
         * @param bitmap resulting bitmap; or {@code null} on failure
         * @param file   If the bitmap is set, the transformed file.
         *               If the bitmap is {@code null}, the file value MUST BE IGNORED.
         * @param action what to do with the result.
         */
        TransformedData(@Nullable final Bitmap bitmap,
                        @NonNull final File file,
                        @NonNull final CoverHandler.NextAction action) {
            this.bitmap = bitmap;
            this.file = file;
            nextAction = action;
        }

        @Nullable
        Bitmap getBitmap() {
            return bitmap;
        }

        @NonNull
        File getFile() {
            return file;
        }

        @NonNull
        CoverHandler.NextAction getNextAction() {
            return nextAction;
        }

        @Override
        @NonNull
        public String toString() {
            return "TransformedData{"
                   + "bitmap=" + (bitmap != null)
                   + ", file=" + file.getAbsolutePath()
                   + ", nextAction=" + nextAction
                   + '}';
        }
    }

}
