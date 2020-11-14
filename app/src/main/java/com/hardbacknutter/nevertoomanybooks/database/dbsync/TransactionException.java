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
package com.hardbacknutter.nevertoomanybooks.database.dbsync;

import androidx.annotation.Nullable;

public class TransactionException
        extends RuntimeException {

    public static final String REQUIRED = "TX required";

    static final String ALREADY_STARTED = "TX already started";
    static final String NOT_STARTED = "No TX started";
    static final String INSIDE_SHARED_TX = "Inside shared TX";
    static final String WRONG_LOCK = "Wrong lock";

    private static final long serialVersionUID = 8342179163992505514L;

    public TransactionException(@Nullable final String message) {
        super(message);
    }

    TransactionException(@Nullable final String message,
                         @Nullable final Exception cause) {
        super(message, cause);
    }
}
