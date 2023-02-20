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

import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * Used to transmogrify an {@link IOException} caused by an {@link ErrnoException},
 * where the errno == OsConstants.ENOSPC, to a simple DiskFullException.
 * <p>
 * The idea is that in some places where we can and should skip most IOExceptions,
 * we still need to throw if the disk is full.
 * <p>
 * Example: a cover download is corrupt -> skip
 * but if the cover cannot be written due to disk full -> throw
 * <p>
 * Dev note: DO NOT make this an IOException (again)!
 */
public class DiskFullException
        extends StorageException {

    private static final long serialVersionUID = 5177125869592785634L;

    public DiskFullException(@NonNull final Throwable cause) {
        super(cause);
    }

    public static boolean isDiskFull(@Nullable final Exception e) {
        return e instanceof IOException
               && e.getCause() instanceof ErrnoException
               && ((ErrnoException) e.getCause()).errno == OsConstants.ENOSPC;
    }
}
