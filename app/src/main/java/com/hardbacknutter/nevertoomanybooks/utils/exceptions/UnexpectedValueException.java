/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.NonNull;

public class UnexpectedValueException
        extends IllegalArgumentException {

    private static final String UNEXPECTED_VALUE = "Unexpected value: ";
    private static final long serialVersionUID = 4161967687140484359L;

    public UnexpectedValueException(@NonNull final String s) {
        super(UNEXPECTED_VALUE + s);
    }

    public UnexpectedValueException(final long l) {
        super(UNEXPECTED_VALUE + l);
    }

    public UnexpectedValueException(@NonNull final String message,
                                    @NonNull final Throwable cause) {
        super(UNEXPECTED_VALUE + message, cause);
    }
}
