/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Combine {@link Style} visibility and {@link DataHolder} columns into
 * a simple cached value class.
 * <p>
 * URGENT: merge BookLevelFieldVisibility, BookLevelFieldUsage
 *  and BaseStyle#optionalFieldOrder
 */
public class BookLevelFieldUsage {
    public final boolean[] cover = new boolean[2];

    public final boolean author;
    public final boolean series;
    public final boolean publisher;
    public final boolean bookshelves;

    public final boolean originalTitle;
    public final boolean condition;
    public final boolean isbn;
    public final boolean publicationDate;
    public final boolean format;
    public final boolean language;
    public final boolean location;
    public final boolean rating;
    public final boolean pages;

    public final boolean signed;
    public final boolean edition;
    public final boolean loanee;

    BookLevelFieldUsage(@NonNull final DataHolder rowData,
                        @NonNull final Style style) {

        cover[0] = style.isShowField(Style.Screen.List, DBKey.COVER[0])
                   && rowData.contains(DBKey.BOOK_UUID);
        cover[1] = style.isShowField(Style.Screen.List, DBKey.COVER[1])
                   && rowData.contains(DBKey.BOOK_UUID);

        author = style.isShowField(Style.Screen.List, DBKey.FK_AUTHOR)
                 && rowData.contains(DBKey.AUTHOR_FORMATTED);
        series = style.isShowField(Style.Screen.List, DBKey.FK_SERIES)
                 && rowData.contains(DBKey.SERIES_TITLE);
        publisher = style.isShowField(Style.Screen.List, DBKey.FK_PUBLISHER)
                    && rowData.contains(DBKey.PUBLISHER_NAME);
        bookshelves = style.isShowField(Style.Screen.List, DBKey.FK_BOOKSHELF)
                      && rowData.contains(DBKey.BOOKSHELF_NAME_CSV);

        originalTitle = style.isShowField(Style.Screen.List, DBKey.TITLE_ORIGINAL_LANG)
                        && rowData.contains(DBKey.TITLE_ORIGINAL_LANG);
        condition = style.isShowField(Style.Screen.List, DBKey.BOOK_CONDITION)
                    && rowData.contains(DBKey.BOOK_CONDITION);
        isbn = style.isShowField(Style.Screen.List, DBKey.BOOK_ISBN)
               && rowData.contains(DBKey.BOOK_ISBN);
        publicationDate = style.isShowField(Style.Screen.List, DBKey.BOOK_PUBLICATION__DATE)
                          && rowData.contains(DBKey.BOOK_PUBLICATION__DATE);
        format = style.isShowField(Style.Screen.List, DBKey.FORMAT)
                 && rowData.contains(DBKey.FORMAT);

        language = style.isShowField(Style.Screen.List, DBKey.LANGUAGE)
                   && rowData.contains(DBKey.LANGUAGE);
        location = style.isShowField(Style.Screen.List, DBKey.LOCATION)
                   && rowData.contains(DBKey.LOCATION);
        rating = style.isShowField(Style.Screen.List, DBKey.RATING)
                 && rowData.contains(DBKey.RATING);
        pages = style.isShowField(Style.Screen.List, DBKey.PAGE_COUNT)
                && rowData.contains(DBKey.PAGE_COUNT);


        signed = style.isShowField(Style.Screen.List, DBKey.SIGNED__BOOL)
                 && rowData.contains(DBKey.SIGNED__BOOL);
        edition = style.isShowField(Style.Screen.List, DBKey.EDITION__BITMASK)
                  && rowData.contains(DBKey.EDITION__BITMASK);
        loanee = style.isShowField(Style.Screen.List, DBKey.LOANEE_NAME)
                 && rowData.contains(DBKey.LOANEE_NAME);
    }
}
