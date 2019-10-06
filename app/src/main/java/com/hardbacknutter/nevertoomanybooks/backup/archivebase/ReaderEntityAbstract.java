/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.archivebase;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Basic implementation of the format-agnostic {@link ReaderEntity} methods.
 */
public abstract class ReaderEntityAbstract
        implements ReaderEntity {

    /** Entity type. */
    @NonNull
    private final Type mType;

    /**
     * Constructor.
     *
     * @param type Entity type
     */
    protected ReaderEntityAbstract(@NonNull final Type type) {
        mType = type;
    }

    @NonNull
    @Override
    public Type getType() {
        return mType;
    }

    @Override
    public void save(@NonNull final Context context)
            throws IOException {

        // Build the new File and save
        File destFile = new File(StorageUtils.getCoverDir(context), getName());
        try (OutputStream os = new FileOutputStream(destFile)) {
            StorageUtils.copy(getInputStream(), os);
        } finally {
            if (destFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destFile.setLastModified(getDateModified().getTime());
            }
        }
    }
}
