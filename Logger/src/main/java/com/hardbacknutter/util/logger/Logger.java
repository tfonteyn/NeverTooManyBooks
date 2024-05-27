/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.util.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Logger {

    /**
     * ERROR message.
     *
     * @param tag    log tag
     * @param e      cause
     * @param params objects/text to log
     */
    void e(@NonNull String tag,
           @Nullable Throwable e,
           @Nullable Object... params);

    /**
     * WARN message.
     *
     * @param tag    log tag
     * @param params objects/text to log
     */
    void w(@NonNull String tag,
           @Nullable Object... params);

    /**
     * DEBUG message.
     *
     * @param tag    log tag
     * @param params objects/text to log
     */
    void d(@NonNull String tag,
           @Nullable Object... params);
}
