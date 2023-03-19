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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;

public interface ConnectionValidator {

    /**
     * Make a short call to test the connection.
     *
     * @return {@code true} if al is well.
     *
     * @throws CredentialsException  on authentication/login failures
     * @throws CoverStorageException on storage related failures
     * @throws IOException           on generic/other IO failures
     */
    @WorkerThread
    boolean validateConnection()
            throws CredentialsException,
                   CoverStorageException,
                   IOException;

    void cancel();
}
