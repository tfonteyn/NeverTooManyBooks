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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.File;

/**
 * Value class to report back what was written/exported.
 * Used by both backup and sync packages.
 */
public abstract class WriterResults
        implements Parcelable {

    public abstract void addBook(@IntRange(from = 1) long bookId);

    public abstract int getBookCount();

    public void addCover(@NonNull final File file) {
        addCover(file.getName());
    }

    /**
     * @param name the file name, without path
     */
    public abstract void addCover(@NonNull String name);

    public abstract int getCoverCount();
}
