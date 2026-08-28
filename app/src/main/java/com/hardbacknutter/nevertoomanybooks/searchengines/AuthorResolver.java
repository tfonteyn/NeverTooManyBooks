/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

@FunctionalInterface
public interface AuthorResolver {

    /**
     * Update the given {@link Author}.
     * <ul>
     *     <li>Resolve pen-names</li>
     *     <li>Fix author names with any missing diacritics</li>
     *     <li>Add {@code Identifier}s if possible</li>
     *     <li>Add any fields which have no data yet (e.g. birth/death dates, picture, ...)</li>
     * </ul>
     *
     * Implementation <strong>MUST NOT</strong> write the changes to the database.
     * That is the responsibility of the caller.
     *
     * @param context current Context
     * @param author  to lookup
     *
     * @return {@code true} if the {@link Author} was modified; {@code false} otherwise
     *
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    @WorkerThread
    boolean resolve(@NonNull Context context,
                    @NonNull Author author)
            throws SearchException, CredentialsException;
}
