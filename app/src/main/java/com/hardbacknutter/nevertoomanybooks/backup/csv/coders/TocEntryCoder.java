/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * StringList factory for a {@link TocEntry}.
 * <p>
 * Format:
 * <ul>
 *      <li>title (date) * authorName * {json}</li>
 *      <li>title * authorName * {json}</li>
 * </ul>
 * authorName: see {@link AuthorCoder}
 * <br>date: see {@link #DATE_PATTERN}.
 *
 * <br><strong>Note:</strong> the " * {json}" suffix is optional and can be missing.
 */
public class TocEntryCoder
        implements StringList.Coder<TocEntry> {

    /**
     * Find the publication year in a string like "some title (1978-04-22)".
     * <p>
     * The pattern finds (1987), (1978-04) or (1987-04-22)
     * Result is found in group 1.
     */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\("
                            + "([1|2]\\d\\d\\d"
                            + "|[1|2]\\d\\d\\d-\\d\\d"
                            + "|[1|2]\\d\\d\\d-\\d\\d-\\d\\d)"
                            + "\\)");

    TocEntryCoder() {
    }

    /**
     * Attempts to parse a single string into a {@link TocEntry}.
     * <ul>The date *must* match a pattern of a (partial) ISO date string:
     * <li>(YYYY)</li>
     * <li>(YYYY-MM)</li>
     * <li>(YYYY-MM-DD)</li>
     * <li>(YYYY-DD-MM) might work depending on the user's Locale. Not tested.</li>
     * </ul>
     * BookCatalogue had no dates: Giants In The Sky * Blish, James
     * <ul>We now also accept:
     *      <li>Giants In The Sky (1952) * Blish, James</li>
     *      <li>Giants In The Sky (1952-03) * Blish, James</li>
     *      <li>Giants In The Sky (1952-03-22) * Blish, James</li>
     * </ul>
     */
    @Override
    @NonNull
    public TocEntry decode(@NonNull final String element) {
        final List<String> parts = StringList.newInstance().decodeElement(element);
        String title = parts.get(0);
        final Author author = Author.from(parts.get(1));

        final Matcher matcher = DATE_PATTERN.matcher(title);
        if (matcher.find()) {
            final String g1 = matcher.group(0);
            if (g1 != null) {
                // strip out the found pattern (including the brackets)
                title = title.replace(g1, "").trim();
                return new TocEntry(author, title, matcher.group(1));
            }
        }
        return new TocEntry(author, title);
    }
}
