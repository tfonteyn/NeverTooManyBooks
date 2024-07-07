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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@FunctionalInterface
public interface DateParser {

    /**
     * Attempt to parse a date string.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code Optional.empty()}
     */
    @NonNull
    Optional<LocalDateTime> parse(@Nullable CharSequence dateStr);

    /**
     * Attempt to parse a date string.
     * <p>
     * The parser should/will try with the passed locale first, but is free to ignore it.
     * If that fails, it might/can use other Locales.
     *
     * @param dateStr String to parse
     * @param locale  (optional) Locale to try first
     *
     * @return Resulting date if successfully parsed, otherwise {@code Optional.empty()}
     */
    @NonNull
    default Optional<LocalDateTime> parse(@Nullable final CharSequence dateStr,
                                          @Nullable final Locale locale) {
        return parse(dateStr);
    }
}
