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

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

public interface SiteAuthModule {

    /** Preference. Suffix added to the site PreferenceKey. */
    String PK_SUFFIX_LOGIN_TO_SEARCH = ".login.to.search";
    /** Preference. Suffix added to the site PreferenceKey. */
    String PK_SUFFIX_HOST_USER = ".host.user";
    /** Preference. Suffix added to the site PreferenceKey. */
    String PK_SUFFIX_HOST_PASSWORD = ".host.password";

    /**
     * Performs a login using the stored credentials.
     * <p>
     * Implementations should check (e.g. check the cookie locally) if we're already
     * logged in during this session* and return with success immediately.
     *
     * @param context Current context
     *
     * @return the valid user id
     *
     * @throws CredentialsException on authentication/login failures
     * @throws IOException          on generic/other IO failures
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    @NonNull
    String login(@NonNull Context context)
            throws IOException, CredentialsException, StorageException;

    /**
     * Get the user id for the <strong>current</strong> session.
     * This is not necessarily the same as the username.
     *
     * @return a valid non-empty user id if present
     */
    @NonNull
    Optional<String> getUserId();

    /**
     * Cancel any current/ongoing authentication request to the website.
     */
    void cancel();

}
