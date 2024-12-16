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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * MLA specifies the title must be in italics.
 * We're <strong>assuming</strong> that the user will be using html.
 * If not... oh well :/
 * <p>
 * Note we don't add edition information.
 * <p>
 * Example:
 * <pre>
 *     Adams, Douglas and Lloyd, John <i>The Deeper Meaning of Liff</i>. Pan Books, 1990.
 * </pre>
 */
class MLACitation
        implements Citation {

    @NonNull
    @Override
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {
        final List<Author> authors = book.getAuthors();
        final String authorStr;
        switch (authors.size()) {
            case 1: {
                authorStr = authors.get(0).getFormattedName(false);
                break;
            }
            case 2:
                authorStr = authors.get(0).getFormattedName(false)
                            + ' ' + context.getString(R.string.list_and)
                            + ' ' + authors.get(1).getFormattedName(false);
                break;
            default:
                authorStr = context.getString(R.string.and_others_textual,
                                              authors.get(0).getFormattedName(false));
        }

        final StringJoiner p2sj = new StringJoiner(", ");

        book.getPrimaryPublisher().ifPresent(publisher -> p2sj.add(publisher.getName()));

        final PartialDate firstPublicationDate = book.getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            p2sj.add(String.valueOf(firstPublicationDate.getYearValue()));
        } else {
            final String isoDate = book.getString(DBKey.BOOK_PUBLICATION__DATE);
            if (isoDate.length() >= 4) {
                p2sj.add(isoDate.substring(0, 4));
            }
        }
        final String p2 = p2sj.toString();


        final StringBuilder sb = new StringBuilder(authorStr);
        sb.append(" <i>").append(book.getTitle()).append("</i>.");
        if (!p2.isEmpty()) {
            sb.append(" ").append(p2).append(".");
        }

        return sb.toString();
    }
}
