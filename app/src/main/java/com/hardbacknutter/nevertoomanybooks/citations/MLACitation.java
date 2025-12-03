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

package com.hardbacknutter.nevertoomanybooks.citations;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
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
    private final Style style;

    MLACitation(@NonNull final Style style) {
        this.style = style;
    }

    @NonNull
    private static String getPublishingInformation(@NonNull final Book book) {
        final StringJoiner sj = new StringJoiner(", ");

        book.getPrimaryPublisher().ifPresent(publisher -> sj.add(publisher.getName()));

        // need the year only
        book.getFirstPublicationDate().getYear()
            .or(() -> book.getPublicationDate().getYear())
            .ifPresent(year -> sj.add(String.valueOf(year)));

        return sj.toString();
    }

    @NonNull
    private String getAutorInformation(@NonNull final Context context,
                                       @NonNull final Book book) {
        final boolean byGivenName = style.isShowAuthorByGivenName();
        final String authorStr;
        final List<Author> authors = book.getAuthors();
        switch (authors.size()) {
            case 1: {
                authorStr = authors.get(0).getFormattedName(byGivenName);
                break;
            }
            case 2: {
                authorStr = context.getString(R.string.list_and,
                                              authors.get(0).getFormattedName(byGivenName),
                                              authors.get(1).getFormattedName(byGivenName));
                break;
            }
            default: {
                authorStr = context.getString(R.string.and_others_textual,
                                              authors.get(0).getFormattedName(byGivenName));
            }
        }
        return authorStr;
    }

    @NonNull
    @Override
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {
        final String autorInformation = getAutorInformation(context, book);
        final String publishingInformation = getPublishingInformation(book);

        final StringBuilder sb = new StringBuilder(autorInformation);
        sb.append(" <i>").append(book.getTitle()).append("</i>.");
        if (!publishingInformation.isEmpty()) {
            sb.append(" ").append(publishingInformation).append(".");
        }

        return sb.toString();
    }
}
