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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Objects;

/**
 * Wraps a {@link CoverStorageException} with an unchecked exception.
 */
public class UncheckedCoverStorageException
        extends RuntimeException {

    private static final long serialVersionUID = 8559698702101274197L;

    /**
     * Constructs an instance of this class.
     *
     * @param message the detail message, can be null
     * @param cause   the {@code CoverStorageException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedCoverStorageException(@Nullable final String message,
                                          @NonNull final CoverStorageException cause) {
        super(message, Objects.requireNonNull(cause));
    }

    /**
     * Constructs an instance of this class.
     *
     * @param cause the {@code CoverStorageException}
     *
     * @throws NullPointerException if the cause is {@code null}
     */
    public UncheckedCoverStorageException(@NonNull final CoverStorageException cause) {
        super(Objects.requireNonNull(cause));
    }

    /**
     * Returns the cause of this exception.
     *
     * @return the {@code CoverStorageException} which is the cause of this exception.
     */
    @Override
    public CoverStorageException getCause() {
        return (CoverStorageException) super.getCause();
    }

    /**
     * Called to read the object from a stream.
     *
     * @throws InvalidObjectException if the object is invalid or has a cause that is not
     *                                a {@code CoverStorageException}
     */
    private void readObject(@NonNull final ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        final Throwable cause = super.getCause();
        if (!(cause instanceof CoverStorageException)) {
            throw new InvalidObjectException("Cause must be an CoverStorageException");
        }
    }
}
