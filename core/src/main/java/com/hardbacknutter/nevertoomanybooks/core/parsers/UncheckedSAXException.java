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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Objects;

import org.xml.sax.SAXException;

/**
 * Wraps a {@link SAXException} with an unchecked exception.
 */
public class UncheckedSAXException
        extends RuntimeException {

    private static final long serialVersionUID = 7389841331152934803L;

    /**
     * Constructs an instance of this class.
     *
     * @param message the detail message, can be null
     * @param cause   the {@code SAXException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedSAXException(@Nullable final String message,
                                 @NonNull final SAXException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    /**
     * Constructs an instance of this class.
     *
     * @param cause the {@code SAXException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedSAXException(@NonNull final SAXException cause) {
        super(Objects.requireNonNull(cause));
    }

    /**
     * Returns the cause of this exception.
     *
     * @return the {@code SAXException} which is the cause of this exception.
     */
    @Override
    @Nullable
    public SAXException getCause() {
        return (SAXException) super.getCause();
    }

    /**
     * Called to read the object from a stream.
     *
     * @throws InvalidObjectException if the object is invalid or has a cause that is not
     *                                an {@code SAXException}
     */
    private void readObject(@NonNull final ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        final Throwable cause = super.getCause();
        if (!(cause instanceof SAXException)) {
            throw new InvalidObjectException("Cause must be an SAXException");
        }
    }
}
