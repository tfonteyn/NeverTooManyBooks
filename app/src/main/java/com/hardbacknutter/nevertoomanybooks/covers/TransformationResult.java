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

package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Value class with the results.
 */
class TransformationResult {

    @Nullable
    private final File file;
    @NonNull
    private final NextAction nextAction;

    /**
     * Constructor.
     *
     * @param file   the transformed file, or {@code null} on failure
     * @param action what to do with the result.
     */
    TransformationResult(@Nullable final File file,
                         @NonNull final NextAction action) {
        this.file = file;
        nextAction = action;
    }

    @Nullable
    File getFile() {
        return file;
    }

    @NonNull
    NextAction getNextAction() {
        return nextAction;
    }

    @Override
    @NonNull
    public String toString() {
        return "TransformationResult{"
               + "file=" + (file == null ? null : file.getAbsolutePath())
               + ", nextAction=" + nextAction
               + '}';
    }
}
