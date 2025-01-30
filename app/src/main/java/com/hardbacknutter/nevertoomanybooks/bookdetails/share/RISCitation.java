/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Example:
 * <pre>
 *     TY  - BOOK
 *     T1  - The Meaning of Liff: The Original Dictionary Of Things There Should Be Words For
 *     A1  - Adams, D.
 *     A1  - Lloyd, J.
 *     SN  - 9781447262602
 *     UR  - https://books.google.co.uk/books?id=zXEgAQAAQBAJ
 *     Y1  - 2013
 *     PB  - Pan Macmillan
 *     ER  -
 * </pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/RIS_(file_format)">RIS_(file_format)</a>
 */
public class RISCitation
        implements Citation {

    @NonNull
    @Override
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {

        final StringJoiner sj = new StringJoiner("\r\n");
        sj.add("TY  - BOOK");
        sj.add("T1  - " + book.getTitle());
        book.getAuthors().forEach(author -> sj
                .add("A1  - " + author.getFormattedName(false)));

        final String isbn = book.getString(DBKey.BOOK_ISBN);
        if (!isbn.isEmpty()) {
            sj.add("SN  - " + isbn);
        }

        final String lang = book.getString(DBKey.LANGUAGE);
        if (!lang.isEmpty()) {
            sj.add("LA  - " + ServiceLocator.getInstance().getLanguages()
                                            .getDisplayLanguageFromISO3(context, lang));
        }

        book.getPrimarySeries().ifPresent(series -> {
            sj.add("T3  - " + series.getTitle());
            final String number = series.getNumber();
            if (!number.isEmpty()) {
                sj.add("SV  - " + number);
            }
        });

        book.getPrimaryPublisher().ifPresent(publisher ->
                                                     sj.add("PB  - " + publisher.getName()));

        final PartialDate firstPublicationDate = book.getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            sj.add("Y1  - " + firstPublicationDate.getDelimString("/"));
        } else {
            final String isoDate = book.getString(DBKey.BOOK_PUBLICATION__DATE);
            if (isoDate.length() >= 4) {
                sj.add("Y1  - " + isoDate.substring(0, 4));
            }
        }

        final IdentifierDao identifierDao = ServiceLocator.getInstance().getIdentifierDao();
        book.getIdentifiers().forEach(iv -> {
            identifierDao.findByKey(iv.getKey()).ifPresent(identifier -> {
                final String bookUri = identifier.getBookUri(context);
                if (bookUri != null) {
                    sj.add("UR  - " + String.format(bookUri, iv.getSid()));
                }
            });
        });


        sj.add("ER  -");
        return sj.toString();
    }
}
