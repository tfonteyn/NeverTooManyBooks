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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public final class DateUtils {

    private DateUtils() {
    }

    /**
     * Convert a UTC DateTime to the local system time zone.
     *
     * @param utc DateTime to convert
     *
     * @return local timezone DateTime
     */
    @NonNull
    public static ZonedDateTime utcToZoned(@NonNull final LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault());
    }

    /**
     * Pretty format a local timezone date to a user viewable date+time string.
     *
     * @param context       Current context
     * @param localDateTime to format
     *
     * @return human readable datetime string; if the input is null, {@code ""} is returned
     */
    @NonNull
    public static String displayDateTime(@NonNull final Context context,
                                         @Nullable final TemporalAccessor localDateTime) {

        if (localDateTime == null) {
            return "";
        } else {
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                    .withLocale(userLocale)
                                    .format(localDateTime);
        }
    }

    /**
     * Pretty format a local timezone date to a user viewable date string.
     *
     * @param context       Current context
     * @param localDateTime to format
     *
     * @return human readable date string; if the input is null, {@code ""} is returned
     */
    @NonNull
    public static String displayDate(@NonNull final Context context,
                                     @Nullable final TemporalAccessor localDateTime) {

        if (localDateTime == null) {
            return "";
        } else {
            final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(userLocale)
                                    .format(localDateTime);
        }
    }
}
