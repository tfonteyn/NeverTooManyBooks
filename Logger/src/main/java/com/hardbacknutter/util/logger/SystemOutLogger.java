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

import static com.hardbacknutter.util.logger.Logger.concat;

/**
 * This is really a test-logger only, but due to some gradle dependency issues
 * it's just so much easier to dump it into the regular source set. Oh well...
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class SystemOutLogger
        implements Logger {

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {
        System.out.println("JUnit|ERROR|" + tag + "|" + concat(e, params));
    }

    @Override
    public void w(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|WARN|" + tag + "|" + concat(params));
    }

    @Override
    public void d(@NonNull final String tag,
                  @Nullable final Object... params) {
        System.out.println("JUnit|DEBUG|" + tag + "|" + concat(params));
    }
}
