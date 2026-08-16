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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.util.Map;

import okhttp3.Request;

@FunctionalInterface
public interface RequestFactory {

    /**
     * Create a suitable {@code GET} {@link Request}.
     *
     * @param urlStr            to use
     * @param requestProperties (optional) extra headers to add/override
     *
     * @return new {@code GET} request instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    Request createRequest(@NonNull String urlStr,
                          @Nullable Map<String, String> requestProperties)
            throws MalformedURLException;
}
