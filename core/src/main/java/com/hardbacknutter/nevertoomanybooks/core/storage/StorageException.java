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
package com.hardbacknutter.nevertoomanybooks.core.storage;

import androidx.annotation.Nullable;

/**
 * Dev note: DO NOT make this an IOException (again).
 */
public class StorageException
        extends Exception {

    private static final long serialVersionUID = 6521262373361215281L;

    protected StorageException(@Nullable final String message) {
        super(message);
    }

    protected StorageException(@Nullable final String message,
                               @Nullable final Throwable cause) {
        super(message, cause);
    }

    protected StorageException(@Nullable final Throwable cause) {
        super(cause);
    }
}
