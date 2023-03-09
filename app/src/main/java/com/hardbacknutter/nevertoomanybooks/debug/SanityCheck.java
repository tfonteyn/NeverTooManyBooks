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
package com.hardbacknutter.nevertoomanybooks.debug;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Quick note: this class came into being as a solution to the missing "assert" functionality
 * in Android.
 * <p>
 * Meanwhile (2020-10-15) it seems the latest Android build tools CAN solve the assert issue.
 * To be investigated.
 */
public final class SanityCheck {

    private SanityCheck() {
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long requireNonZero(final long value,
                                      @Nullable final String message) {
        if (value == 0) {
            throw new SanityException(message);
        }
        return value;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long requirePositiveValue(final long value,
                                            @Nullable final String message) {
        if (value <= 0) {
            throw new SanityException(message);
        }
        return value;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static float requirePositiveValue(final float value,
                                             @Nullable final String message) {
        if (value <= 0) {
            throw new SanityException(message);
        }
        return value;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public static String requireValue(@Nullable final String value,
                                      @Nullable final String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        if (value.isEmpty()) {
            throw new SanityException(message);
        }
        return value;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public static <T extends Collection<?>> T requireValue(
            @Nullable final T value,
            @NonNull final Supplier<String> message) {

        return requireValue(value, message.get());
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public static <T extends Collection<?>> T requireValue(@Nullable final T value,
                                                           @Nullable final String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        if (value.isEmpty()) {
            throw new SanityException(message);
        }
        return value;
    }

    public static class SanityException
            extends NullPointerException {

        private static final long serialVersionUID = 4418513924924222373L;

        public SanityException(@Nullable final String message) {
            super(message);
        }
    }
}
