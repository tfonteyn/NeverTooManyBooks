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

package com.hardbacknutter.nevertoomanybooks.bookdetails.share;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * The BibTex format.
 *
 * <pre>
 *     book
 *     A book with an explicit publisher.
 *     Required fields: author/editor, title, publisher, year
 *     Optional fields: volume/number, series, address, edition, month, note, key, url
 * </pre>
 * Example:
 * <pre>
 * {@code
 *      @book{NeverTooManyBooks,
 *          author    = {Douglas {Adams} and John {Lloyd}},
 *          title     = {The Deeper Meaning of Liff},
 *          publisher = {Pan Books},
 *          year      = {1990},
 *          series    = {The Meaning of Liff},
 *          number    = {2}
 * }
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/BibTeX#Database_files">Database files</a>
 */
class BibTexCitation
        implements Citation {

    private static final String AUTHOR = "author    = {";
    private static final String ISBN = "isbn      = {";
    private static final String NUMBER = "number    = {";
    private static final String PUBLISHER = "publisher = {";
    private static final String SERIES = "series    = {";
    private static final String TITLE = "title     = {";
    private static final String YEAR = "year      = {";

    /** concat 2 authors. */
    private static final String AND = " and ";

    @NonNull
    private final Style style;

    BibTexCitation(@NonNull final Style style) {
        this.style = style;
    }

    @Override
    @NonNull
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {

        final String appName = context.getString(R.string.app_name);
        final StringJoiner sj = new StringJoiner(",\n");
        sj.add("@book{" + appName);

        sj.add(TITLE + escape(book.getTitle()) + '}');
        sj.add(AUTHOR + formatAuthors(book.getAuthors()) + '}');

        final String isbn = book.getString(DBKey.BOOK_ISBN);
        if (!isbn.isEmpty()) {
            sj.add(ISBN + isbn + '}');
        }

        final List<Publisher> publishers = book.getPublishers();
        if (publishers.isEmpty()) {
            sj.add(PUBLISHER + context.getString(R.string.unknown) + '}');
        } else {
            sj.add(PUBLISHER + formatPublishers(publishers) + '}');
        }

        final PartialDate firstPublicationDate = book.getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            sj.add(YEAR + firstPublicationDate.getYearValue() + '}');
        } else {
            final String isoDate = book.getString(DBKey.BOOK_PUBLICATION__DATE);
            if (isoDate.length() >= 4) {
                sj.add(YEAR + isoDate.substring(0, 4) + '}');
            } else {
                // mandatory field ...
                sj.add(YEAR + "0}");
            }
        }

        //TODO: check bibtex series format when multiple series
        book.getPrimarySeries()
            .ifPresent(series -> {
                sj.add(SERIES + escape(series.getTitle()) + '}');
                final String number = series.getNumber();
                if (!number.isEmpty()) {
                    sj.add(NUMBER + number + '}');
                }
            });

        return sj.toString() + '\n' + '}';
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
