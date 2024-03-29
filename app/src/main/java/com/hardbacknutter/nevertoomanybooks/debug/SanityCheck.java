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
    @NonNull
    public static String requireValue(@Nullable final String value,
                                      @Nullable final String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
