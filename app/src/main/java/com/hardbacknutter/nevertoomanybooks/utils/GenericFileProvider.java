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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">
 * https://developer.android.com/reference/androidx/core/content/FileProvider</a>
 */
public class GenericFileProvider
        extends FileProvider {

    /**
     * Get a FileProvider URI for the given file.
     * <p>
     * The string matches the Manifest entry:
     * {@code
     * android:authorities="${packageName}.GenericFileProvider "
     * }
     *
     * @param context Current context
     * @param file    to uri-fy
     *
     * @return the uri
     */
    public static Uri getUriForFile(@NonNull final Context context,
                                    @NonNull final File file) {
        return getUriForFile(context, BuildConfig.APPLICATION_ID + ".GenericFileProvider",
                             file);
    }
}
