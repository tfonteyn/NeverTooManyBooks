/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.backup.csv.util;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class DateVerifier {

    @NonNull
    private final DateParser<LocalDateTime> dateParser;

    public DateVerifier(@NonNull final DateParser<LocalDateTime> dateParser) {
        this.dateParser = dateParser;
    }

    /**
     * Verify the given date keys for containing valid dates.
     *
     * @param book        to verify
     * @param keys        to verify
     * @param partialDate flag: {@code true} to cut dates down to partial dates.
     *                    i.e. remove time and any tailing "-01".
     * @param keepTime    flag: whether to keep a time component or strip it
     */
    public void verify(@NonNull final Book book,
                       @NonNull final Set<String> keys,
                       final boolean partialDate,
                       final boolean keepTime) {
        keys.stream().filter(book::contains).forEach(key -> {
            final String s = book.getString(key);
            final Optional<LocalDateTime> date = dateParser.parse(s);
            if (date.isPresent()) {
                String iso = SqlEncode.dateTime(date.get());

                // cut off the time if present & required
                if (!keepTime && iso.length() > 10) {
                    iso = iso.substring(0, 10);
                }

                // Cut 'YYYY-MM-DD' down to month or year if possible & required
                if (partialDate && iso.length() > 4) {
                    while (iso.endsWith("-01")) {
                        iso = iso.substring(0, iso.length() - 3);
                    }
                }
                book.putString(key, iso);
            } else {
                book.remove(key);
            }
        });
    }
}
