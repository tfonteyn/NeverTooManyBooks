/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;

import org.xml.sax.SAXException;

/**
 * Process the response to a GET method.
 *
 * @param <T> the response from the remote server
 *            Typically passed in as a {@code String} or {@code InputStream}.
 * @param <R> the resulting/parsed vale
 */
@FunctionalInterface
public interface ResponseProcessor<T, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param con for getting headers, url,..
     *            Do <strong>NOT</strong> call {@link HttpURLConnection#getInputStream()}!
     *            This is (2025-04-20) only really needed by JSoup as
     *            we need to access the final url and response headers.
     * @param t   to read and parse
     *
     * @return the resulting {@code R}
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException The covers directory is not available
     * @throws SAXException     on parser problems if a SAX parser was used
     */
    R apply(@NonNull HttpURLConnection con,
            @NonNull T t)
            throws IOException,
                   StorageException,
                   SAXException;
}
