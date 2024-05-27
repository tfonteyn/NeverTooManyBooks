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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LogCatLogger
        implements Logger {

    @Override
    public void e(@NonNull final String tag,
                  @Nullable final Throwable e,
                  @Nullable final Object... params) {
        if (params == null) {
            Log.e(tag, "", e);
        } else if (params.length == 1) {
            Log.e(tag, String.valueOf(params[0]), e);
        } else {
            final List<String> ps = Arrays.stream(params)
                                          .map(String::valueOf)
                                          .collect(Collectors.toList());
            Log.e(tag, String.join("|", ps), e);
        }
    }

    @Override
    public void w(@NonNull final String tag,
                  @Nullable final Object... params) {
        if (params == null) {
            Log.w(tag, "");
        } else if (params.length == 1) {
            Log.w(tag, String.valueOf(params[0]));
        } else {
            final List<String> ps = Arrays.stream(params)
                                          .map(String::valueOf)
                                          .collect(Collectors.toList());
            Log.w(tag, String.join("|", ps));
        }
    }

    @Override
    public void d(@NonNull final String tag,
                  @Nullable final Object... params) {
        if (params == null) {
            Log.d(tag, "");
        } else if (params.length == 1) {
            Log.d(tag, String.valueOf(params[0]));
        } else {
            final List<String> ps = Arrays.stream(params)
                                          .map(String::valueOf)
                                          .collect(Collectors.toList());
            Log.d(tag, String.join("|", ps));
        }
    }
}
