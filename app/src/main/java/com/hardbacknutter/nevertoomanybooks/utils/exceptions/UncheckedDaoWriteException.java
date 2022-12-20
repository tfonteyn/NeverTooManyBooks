/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;

/**
 * Wraps a {@link DaoWriteException} with an unchecked exception.
 */
public class UncheckedDaoWriteException
        extends RuntimeException {

    private static final long serialVersionUID = -6815509975593789600L;

    /**
     * Constructs an instance of this class.
     *
     * @param message the detail message, can be null
     * @param cause   the {@code DaoWriteException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedDaoWriteException(@Nullable final String message,
                                      @NonNull final DaoWriteException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    /**
     * Constructs an instance of this class.
     *
     * @param cause the {@code DaoWriteException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedDaoWriteException(@NonNull final DaoWriteException cause) {
        super(Objects.requireNonNull(cause));
    }

    /**
     * Returns the cause of this exception.
     *
     * @return the {@code DaoWriteException} which is the cause of this exception.
     */
    @Override
    public DaoWriteException getCause() {
        return (DaoWriteException) super.getCause();
    }

    /**
     * Called to read the object from a stream.
     *
     * @throws InvalidObjectException if the object is invalid or has a cause that is not
     *                                an {@code DaoWriteException}
     */
    private void readObject(@NonNull final ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        final Throwable cause = super.getCause();
        if (!(cause instanceof DaoWriteException)) {
            throw new InvalidObjectException("Cause must be an DaoWriteException");
        }
    }
}
