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

package com.hardbacknutter.nevertoomanybooks.citations;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * The BibTeX format.
 *
 * <pre>
 *     book
 *     A book with an explicit publisher.
 *     Required fields: author/editor, title, publisher, year
 *     Optional fields: volume/number, series, address,
 *                      edition, month, note, key, url
 * </pre>
 * Example:
 * <pre>
 * {@code
 *      @book{NeverTooManyBooks,
 *        author    = {Douglas {Adams} and John {Lloyd}},
 *        title     = {The Deeper Meaning of Liff},
 *        publisher = {Pan Books},
 *        year      = {1990},
 *        series    = {The Meaning of Liff},
 *        number    = {2}
 *      }
 * }
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/BibTeX#Database_files">Database files</a>
 * @see <a href="https://nwalsh.com/tex/texhelp/bibtx-23.html">texhelp/bibtx-23</a>
 */
class BibTeXCitation
        implements Citation {

    /**
     * Format for a line; the "9" is the length of the longest label,
     * currently being {@link #PUBLISHER}.
     */
    private static final String NAME_VALUE = "  %1$-9s = {%2$s}";

    private static final String AUTHOR = "author";
    private static final String ISBN = "isbn";
    private static final String NUMBER = "number";
    private static final String PUBLISHER = "publisher";
    private static final String SERIES = "series";
    private static final String TITLE = "title";
    private static final String URL = "url";
    private static final String YEAR = "year";

    /** concat 2 authors; the BibTeX standard requires English 'and'. */
    private static final String AND = " and ";

    @NonNull
    private final Style style;
    private final IdentifierDao identifierDao;

    BibTeXCitation(@NonNull final Style style) {
        this.style = style;
        identifierDao = ServiceLocator.getInstance().getIdentifierDao();
    }

    @Override
    @NonNull
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {

        final StringJoiner sj = new StringJoiner(",\n");
        sj.add("@book{" + context.getString(R.string.app_name));

        sj.add(String.format(NAME_VALUE, TITLE, escape(book.getTitle())));
        sj.add(String.format(NAME_VALUE, AUTHOR, formatAuthors(book.getAuthors())));

        final String isbn = book.getRawProductCode();
        if (!isbn.isEmpty()) {
            sj.add(String.format(NAME_VALUE, ISBN, escape(isbn)));
        }

        final List<Publisher> publishers = book.getPublishers();
        if (publishers.isEmpty()) {
            sj.add(String.format(NAME_VALUE, PUBLISHER, context.getString(R.string.unknown)));
        } else {
            sj.add(String.format(NAME_VALUE, PUBLISHER, formatPublishers(publishers)));
        }

        // need the year only
        final int year = book.getFirstPublicationDate().getYear()
                             .or(() -> book.getPublicationDate().getYear())
                             // mandatory field ...
                             .orElse(0);
        sj.add(String.format(NAME_VALUE, YEAR, escape(String.valueOf(year))));

        //TODO: check BibTeX series format when multiple series
        book.getPrimarySeries()
            .ifPresent(series -> {
                sj.add(String.format(NAME_VALUE, SERIES, escape(series.getTitle())));
                final String number = series.getNumber();
                if (!number.isEmpty()) {
                    sj.add(String.format(NAME_VALUE, NUMBER, escape(number)));
                }
            });

        book.getIdentifiers().forEach(iv -> identifierDao
                .find(iv.getKey(), Identifier.EntityType.Book)
                .flatMap(identifier -> identifier.getUri(iv.getSid()))
                .ifPresent(bookUri -> sj.add(String.format(NAME_VALUE, URL, bookUri))));

        return sj + "\n}\n";
    }

    /**
     * Paranoia...  escape all curly brackets.
     *
     * @param s to clean
     *
     * @return escaped result
     */
    private String escape(@NonNull final String s) {
        return s.replace("{", "\\{")
                .replace("}", "\\}");
    }

    @NonNull
    private String formatAuthors(@NonNull final List<Author> authors) {
        return authors.stream()
                      .map(this::format)
                      .collect(Collectors.joining(AND));
    }

    @NonNull
    private String format(@NonNull final Author author) {
        if (author.getGivenNames().isEmpty()) {
            return escape(author.getFamilyName());
        } else {
            if (style.isShowAuthorByGivenName()) {
                return escape(author.getGivenNames())
                       + " {" + escape(author.getFamilyName()) + '}';
            } else {
                return '{' + escape(author.getFamilyName()) + "}, "
                       + escape(author.getGivenNames());
            }
        }
    }

    @NonNull
    private String formatPublishers(@NonNull final List<Publisher> publishers) {
        return publishers.stream()
                         .map(Publisher::getName)
                         .map(this::escape)
                         .collect(Collectors.joining(AND));
    }
}
