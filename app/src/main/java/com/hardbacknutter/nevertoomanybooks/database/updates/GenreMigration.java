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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.entities.Tag;

public final class GenreMigration {

    /** Genre string migration splitter characters. */
    static final Pattern GENRE_SPLITTER_PATTERN = Pattern.compile("[/,;>]");

    private GenreMigration() {
    }

    /**
     * Convert the {@code genre} string to a list of {@link Tag}s.
     *
     * @param genre to convert
     *
     * @return a list of new Tags, with id {@code 0}
     */
    @NonNull
    public static List<Tag> convert(@NonNull final String genre) {
        // sanity
        if (genre.isBlank()) {
            return List.of();
        }
        return Arrays.stream(GENRE_SPLITTER_PATTERN.split(genre))
                     .map(String::strip)
                     .map(Tag::new)
                     .collect(Collectors.toList());
    }
}
