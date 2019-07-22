/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database.cursors;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

/**
 * CursorRow object for the BooklistCursor.
 * <p>
 * Provides methods access the 'level' texts and perform some common tasks on the 'current' row.
 */
public class BooklistMappedCursorRow
        extends MappedCursorRow {

    /** Style to use while using this cursor. */
    @NonNull
    private final BooklistStyle mStyle;

    /**
     * level text. Uses a dynamically set domain.
     * Why 10 members? because at 2 it took me an hour to figure out why we had crashed...
     * i.o.w. there can be as many levels as there are groups,
     * FIXME: If a user adds more then 10 groups to a style, we'll crash...
     */
    private final int[] mLevelCol = {-2, -2, -2, -2, -2, -2, -2, -2, -2, -2};

    /**
     * Constructor.
     *
     * @param cursor Underlying Cursor
     * @param style  to apply
     */
    public BooklistMappedCursorRow(@NonNull final Cursor cursor,
                                   @NonNull final BooklistStyle style) {
        super(cursor, DBDefinitions.TBL_BOOKS,
              DBDefinitions.KEY_FK_BOOK,

              DBDefinitions.KEY_FK_AUTHOR,
              DBDefinitions.KEY_AUTHOR_IS_COMPLETE,

              DBDefinitions.KEY_FK_SERIES,
              DBDefinitions.KEY_SERIES_IS_COMPLETE,
              DBDefinitions.KEY_BOOK_NUM_IN_SERIES,

              DBDefinitions.KEY_LOANEE_AS_BOOLEAN,

              DBDefinitions.KEY_BL_ABSOLUTE_POSITION,
              DBDefinitions.KEY_BL_NODE_ROW_KIND,
              DBDefinitions.KEY_BL_NODE_LEVEL);

        mStyle = style;
    }

    /**
     * @return {@code true} if the list can display a series number.
     */
    public boolean hasSeriesNumber() {
        return getColumnIndex(DBDefinitions.KEY_BOOK_NUM_IN_SERIES) >= 0;
    }

    /**
     * Convenience method to check if the cursor has a valid Author ID.
     *
     * @return {@code true} if the list can display an Author.
     */
    public boolean hasAuthorId() {
        return getColumnIndex(DBDefinitions.KEY_FK_AUTHOR) >= 0
                && getLong(DBDefinitions.KEY_FK_AUTHOR) > 0;
    }

    /**
     * Convenience method to check if the cursor has a valid Series ID.
     *
     * @return {@code true} if the list can display a Series.
     */
    public boolean hasSeriesId() {
        return getColumnIndex(DBDefinitions.KEY_FK_SERIES) >= 0
                && getLong(DBDefinitions.KEY_FK_SERIES) > 0;
    }

    /**
     * Get the full set of 'level' texts for this row.
     *
     * @param context Current context
     *
     * @return level-text array
     */
    @Nullable
    public String[] getLevelText(@NonNull final Context context) {
        return new String[]{getLevelText(context, 1),
                            getLevelText(context, 2)};
    }

    /**
     * Get the text associated with the matching level group for the current item.
     *
     * @param context Current context
     * @param level   to get
     *
     * @return the text for that level, or {@code null} if none present.
     */
    @Nullable
    public String getLevelText(@NonNull final Context context,
                               @IntRange(from = 1) final int level) {
        // bail out if there is no data on level
        if (mStyle.groupCount() < level) {
            return null;
        }
        if (BuildConfig.DEBUG) {
            if (level > mLevelCol.length) {
                throw new IllegalArgumentException(
                        "level=" + level + " is larger than mLevelCol size");
            }
        }

        int index = level - 1;
        if (mLevelCol[index] < 0) {
            final String name = mStyle.getGroupAt(index).getDisplayDomain().name;
            mLevelCol[index] = getColumnIndex(name);
            if (mLevelCol[index] < 0) {
                throw new ColumnNotPresentException(name);
            }
        }

        //FIXME: from BoB, click book. Move sideways book to book (10.. 13x) then Back to BoB

        // ==> https://github.com/eleybourn/Book-Catalogue/issues/504

        //    android.database.CursorIndexOutOfBoundsException: Index 0 requested, with a size of 0
        //        at android.database.AbstractCursor.checkPosition(AbstractCursor.java:460)
        //        at android.database.AbstractWindowedCursor.checkPosition(AbstractWindowedCursor.java:136)
        //        at android.database.AbstractWindowedCursor.getString(AbstractWindowedCursor.java:50)
        //        at com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor.getString(BooklistPseudoCursor.java:340)
        //        at com.eleybourn.bookcatalogue.database.cursors.MappedCursorRow.getString(MappedCursorRow.java:57)
        //        at com.eleybourn.bookcatalogue.database.cursors.BooklistMappedCursorRow.getLevelText(BooklistMappedCursorRow.java:170)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf.setHeaderText(BooksOnBookshelf.java:1687)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf.access$400(BooksOnBookshelf.java:103)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf$4.onScrolled(BooksOnBookshelf.java:489)

        int columnIndex = mLevelCol[index];
        try {
            //booom
            String text = getString(columnIndex);

            return formatRowGroup(context, level, text);

        } catch (@NonNull final CursorIndexOutOfBoundsException e) {
            Logger.warnWithStackTrace(this,
                                      "columnIndex=" + columnIndex,
                                      "level=" + level,
                                      "index=" + index,
                                      "getCount=" + getCount(),
                                      "getPosition=" + getPosition());
            return null;
        }
    }

    /**
     * Perform any special formatting for a row group.
     *
     * @param context Current context
     * @param level   Level of the row group
     * @param source  Source value
     *
     * @return Formatted string, or original string when no special format
     * was needed or on any failure
     */
    @Nullable
    private String formatRowGroup(@NonNull final Context context,
                                  @IntRange(from = 1) final int level,
                                  @Nullable final String source) {
        if (source == null) {
            return null;
        }

        // BooklistBuilder will insert 'UNKNOWN' in the SQL row returned if the data is not set.
        if (source.equalsIgnoreCase(context.getString(R.string.unknown))) {
            return source;
        }

        // sanity check.
        if (mStyle.groupCount() < level) {
            throw new IllegalArgumentException(
                    "groupCount=" + mStyle.groupCount() + " < level=" + level);
        }

        Locale locale = LocaleUtils.from(context);

        int index = level - 1;

        switch (mStyle.getGroupKindAt(index)) {
            case BooklistGroup.RowKind.READ_STATUS:
                switch (source) {
                    case "0":
                        return context.getString(R.string.lbl_unread);
                    case "1":
                        return context.getString(R.string.lbl_read);
                    default:
                        Logger.warn(this, "formatRowGroup",
                                    "Unknown read status=" + source);
                        break;
                }
                return source;

            case BooklistGroup.RowKind.LANGUAGE:
                LocaleUtils.getDisplayName(locale, source);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
            case BooklistGroup.RowKind.DATE_ADDED_MONTH:
            case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
            case BooklistGroup.RowKind.DATE_READ_MONTH:
                try {
                    int i = Integer.parseInt(source);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        return DateUtils.getMonthName(locale, i, false);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(this, e);
                }
                break;

            case BooklistGroup.RowKind.RATING:
                try {
                    int i = Integer.parseInt(source);
                    // If valid, get the name
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        return context.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(this, e);
                }
                break;

        }
        return source;
    }
}
