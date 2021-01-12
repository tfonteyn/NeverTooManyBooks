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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Importing data can give a detailed reason of failure.
 */
public class ImportException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = 6654228248803658711L;

    public ImportException(@NonNull final String message) {
        super(message);
    }

    public ImportException(@NonNull final String message,
                           @NonNull final Throwable e) {
        super(message, e);
    }

    @NonNull
    @Override
    public String getLocalizedMessage(@NonNull final Context context) {
        //TODO: look at cause and give more details
        return context.getString(R.string.error_import_file_not_supported);
    }
}
