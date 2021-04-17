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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;

/**
 * Thrown when external storage media is not available.
 */
public class ExternalStorageException
        extends IOException
        implements LocalizedException {

    private static final long serialVersionUID = -3542125839470576149L;
    @NonNull
    private final AppDir mAppDir;

    public ExternalStorageException(@NonNull final AppDir appDir,
                                    @Nullable final String message) {
        super(message);
        mAppDir = appDir;
    }

    public ExternalStorageException(@NonNull final AppDir appDir,
                                    @Nullable final String message,
                                    @Nullable final Throwable cause) {
        super(message, cause);
        mAppDir = appDir;
    }

    /**
     * Used for logging.
     * Will give the actual folder which is having problems + the underlying cause.
     *
     * @return message
     */
    @NonNull
    @Override
    public String getMessage() {
        final Throwable cause = getCause();
        if (cause != null) {
            return mAppDir.toString() + ": " + super.getMessage() + ": " + cause.getMessage();
        } else {
            return mAppDir.toString() + ": " + super.getMessage();
        }
    }

    @NonNull
    public AppDir getAppDir() {
        return mAppDir;
    }

    /**
     * The default user displayable message.
     * For a detailed  message, use {@link #getAppDir()} and/or {@link #getCause()}.
     *
     * @param context Current context
     *
     * @return text
     */
    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        return context.getString(R.string.error_storage_not_accessible);
    }

    @Override
    @NonNull
    public String toString() {
        return "ExternalStorageException{"
               + "mAppDir=" + mAppDir.name()
               + ", " + super.toString()
               + '}';
    }
}
