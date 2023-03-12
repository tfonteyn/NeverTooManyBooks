/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.io;

import androidx.annotation.NonNull;

/**
 * Thrown during an export of data.
 * <p>
 * This class serves as a wrapper around q (typically) RuntimeException.
 */
public class DataWriterException
        extends Exception {


    private static final long serialVersionUID = -1706696680423435433L;

    /**
     * Constructor.
     *
     * @param cause the Exception to wrap
     */
    public DataWriterException(@NonNull final Throwable cause) {
        super(cause);
    }
}
