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

package com.hardbacknutter.nevertoomanybooks.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The debug flags are still here as part of a migration to modules.
 * The plan is to eliminate them.
 */
public final class LoggerFactory {

    /** Dump SQL. */
    public static final boolean DEBUG_EXEC_SQL = false;

    @Nullable
    private static Logger logger;

    private LoggerFactory() {
    }

    @NonNull
    public static Logger getLogger() {
        return Objects.requireNonNull(logger);
    }

    public static synchronized void setLogger(@NonNull final Logger logger) {
        LoggerFactory.logger = logger;
    }
}
