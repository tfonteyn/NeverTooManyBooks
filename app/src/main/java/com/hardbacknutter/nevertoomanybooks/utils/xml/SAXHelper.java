/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.xml;

import androidx.annotation.NonNull;

import java.io.IOException;

import org.xml.sax.SAXException;

public final class SAXHelper {

    private SAXHelper() {
    }

    /**
     * If the passed exceptions is an {@link IOException} embedded in a {@link SAXException}
     * , unwrap it and throw it as the actual IOException.
     *
     * @param e to unwrap
     *
     * @throws IOException if applicable
     */
    public static void unwrapIOException(@NonNull final Exception e)
            throws IOException {
        if (e instanceof SAXException
            && ((SAXException) e).getException() instanceof IOException) {
            throw (IOException) ((SAXException) e).getException();
        }
    }
}
