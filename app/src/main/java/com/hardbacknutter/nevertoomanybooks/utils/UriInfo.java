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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.Objects;

/**
 * Determine the name and size of the content behind a Uri.
 * <p>
 * Dev Note: alternatively use {@link DocumentFile} 'from' methods
 * and {@link DocumentFile#getName()} / {@link DocumentFile#length()} but that's TWO queries.
 */
public class UriInfo {

    private static final String ERROR_UNKNOWN_SCHEME_FOR_URI = "Unknown scheme for uri: ";

    @NonNull
    private final Uri mUri;
    @Nullable
    private String mDisplayName;
    private long mSize;

    private boolean mResolved;

    /**
     * Constructor.
     *
     * @param uri to inspect
     */
    public UriInfo(@NonNull final Uri uri) {
        mUri = uri;
    }

    public UriInfo(@NonNull final Uri uri,
                   @NonNull final String displayName,
                   final int size) {
        mUri = uri;
        mDisplayName = displayName;
        mSize = size;
        mResolved = true;
    }

    @NonNull
    public Uri getUri() {
        return mUri;
    }

    @NonNull
    public String getDisplayName(@NonNull final Context context) {
        if (!mResolved) {
            resolve(context);
        }
        return Objects.requireNonNull(mDisplayName, "mDisplayName");
    }

    public long getSize(@NonNull final Context context) {
        if (!mResolved) {
            resolve(context);
        }
        return mSize;
    }

    private void resolve(@NonNull final Context context) {
        final String scheme = mUri.getScheme();
        if (scheme == null) {
            throw new IllegalStateException(ERROR_UNKNOWN_SCHEME_FOR_URI + mUri);
        }

        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            final ContentResolver contentResolver = context.getContentResolver();
            final String[] columns = {OpenableColumns.DISPLAY_NAME,
                                      OpenableColumns.SIZE};
            try (Cursor cursor = contentResolver.query(mUri, columns, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {

                    // display name
                    final String name = cursor.getString(0);
                    // 0 for a directory
                    final long size = cursor.getLong(1);

                    // sanity check, according to the android.provider.OpenableColumns
                    // documentation, the name and size MUST be present.
                    if (name != null && !name.isEmpty()) {
                        mDisplayName = name;
                        mSize = size;
                        mResolved = true;
                        return;
                    }
                }
            }
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            final String path = mUri.getPath();
            if (path != null) {
                final File file = new File(path);
                // sanity check
                if (file.exists()) {
                    mDisplayName = file.getName();
                    mSize = file.length();
                    mResolved = true;
                    return;
                }
            }
        } else if (scheme.startsWith("http")) {
            mDisplayName = mUri.toString();
            mSize = 0;
            mResolved = true;
            return;
        }

        throw new IllegalStateException(ERROR_UNKNOWN_SCHEME_FOR_URI + mUri);
    }
}
