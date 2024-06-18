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
package com.hardbacknutter.nevertoomanybooks.utils.provider;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Note that the only reason we create this subclass is to have a central point to manage the
 * authority string transparently to the rest of the app.
 * <pre>
 * gradle.build
 * {@code
 *      android.buildTypes.[type].resValue("string", "fileProviderAuthority", "[value]")
 * }
 *
 * AndroidManifest.xml
 * {@code
 * <provider
 *     android:name=".utils.GenericFileProvider"
 *     android:authorities="@string/fileProviderAuthority"
 *     android:exported="false"
 *     android:grantUriPermissions="true">
 *     <meta-data
 *         android:name="android.support.FILE_PROVIDER_PATHS"
 *         android:resource="@xml/provider_paths" />
 * </provider>
 * }
 *
 * res/xml/provider_paths.xml
 * {@code
 * <paths>
 *     <!-- Access limited to the Pictures directory. -->
 *     <external-files-path
 *         name="external-path-pics"
 *         path="Pictures/" />
 *     <!-- Log and Upgrades; just expose the root. -->
 *     <files-path
 *         name="files-path-root"
 *         path="./" />
 *     <!-- For the debug-report attachments, just expose the root. -->
 *     <cache-path
 *         name="cache-path-root"
 *         path="./" />
 * </paths>
 * }
 * </pre>
 *
 * <a href="https://issuetracker.google.com/issues/37125252">Google BUG 37125252 won't fix??</a>
 *
 * @see <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">
 *         FileProvider</a>
 * @see <a href="https://developer.android.com/reference/android/content/ContentProvider.html">
 *         ContentProvider</a>
 */
public class GenericFileProvider
        extends ExtFileProvider {

    /**
     * Get a FileProvider URI for the given file.
     *
     * @param context Current context
     * @param file    A {@link File} pointing to the filename for which you want a
     *                {@code content} {@link Uri}.
     *
     * @return A content URI for the file.
     *
     * @throws IllegalArgumentException When the given {@link File} is outside
     *                                  the paths supported by the provider.
     */
    @NonNull
    public static Uri createUri(@NonNull final Context context,
                                @NonNull final File file) {
        return getUriForFile(context, context.getString(R.string.fileProviderAuthority), file);
    }

    /**
     * Get a FileProvider URI for the given file.
     *
     * @param context     Current context
     * @param file        A {@link File} pointing to the filename for which you want a
     *                    {@code content} {@link Uri}.
     * @param displayName the name to display instead of the original file name
     *
     * @return A content URI for the file.
     *
     * @throws IllegalArgumentException When the given {@link File} is outside
     *                                  the paths supported by the provider.
     */
    @NonNull
    public static Uri createUri(@NonNull final Context context,
                                @NonNull final File file,
                                @NonNull final String displayName) {
        return getUriForFile(context, context.getString(R.string.fileProviderAuthority), file,
                             displayName);
    }
}
