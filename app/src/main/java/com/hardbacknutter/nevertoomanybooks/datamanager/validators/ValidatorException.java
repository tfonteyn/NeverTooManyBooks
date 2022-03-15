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
package com.hardbacknutter.nevertoomanybooks.datamanager.validators;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Exception class for all validation errors.
 */
public class ValidatorException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = -6649344132897810680L;

    public ValidatorException(@NonNull final String localizedMessage) {
        super(localizedMessage);
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        // if a custom message was added, use that.
        if (getMessage() != null) {
            return getMessage();
        }

        return context.getString(R.string.error_unknown);
    }
}
