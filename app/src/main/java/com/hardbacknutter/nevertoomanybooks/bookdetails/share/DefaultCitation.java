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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Details;

/**
 * The original/legacy twitter citation.
 * <p>
 * Output:
 * <pre>
 *      "I'm reading " + title + series + " by " + author + ratingString
 * </pre>
 */
class DefaultCitation
        implements Citation {

    @NonNull
    private final Style style;

    DefaultCitation(@NonNull final Style style) {
        this.style = style;
    }

    @Override
    @NonNull
    public String cite(@NonNull final Context context,
                       @NonNull final Book book) {
        final String title = book.getTitle();

        final Author author = book.getPrimaryAuthor();
        final String authorStr = author != null
                                 ? author.getLabel(context, Details.AutoSelect, style)
                                 : context.getString(R.string.unknown_author);

        final String seriesStr = book
                .getPrimarySeries()
                .map(value -> context.getString(R.string.brackets,
                                                value.getLabel(context,
                                                               Details.AutoSelect,
                                                               style)))
                .orElse("");

        final RealNumberParser realNumberParser =
                new RealNumberParser(LocaleListUtils.asList(context));

        //remove trailing 0's
        final float rating = book.getFloat(DBKey.RATING, realNumberParser);
        String ratingStr;
        if (rating > 0) {
            // force rounding down and check the fraction
            final int ratingTmp = (int) rating;
            ratingStr = String.valueOf(rating - ratingTmp > 0 ? rating : ratingTmp);
            ratingStr = context.getString(R.string.brackets, ratingStr + '/' + Book.RATING_STARS);

        } else {
            ratingStr = "";
        }

        return context.getString(R.string.citation_share_book_im_reading,
                                 title, seriesStr, authorStr, ratingStr);
    }
}
