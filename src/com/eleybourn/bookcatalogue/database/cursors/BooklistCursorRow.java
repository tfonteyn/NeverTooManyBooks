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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_ROW_KIND;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;

/**
 * CursorRow object for the BooklistCursor.
 * <p>
 * Provides methods to perform common tasks on the 'current' row of the cursor.
 *
 * @author Philip Warner
 */
public class BooklistCursorRow
        extends BookCursorRowBase {

    /** Underlying builder object. */
    @NonNull
    private final BooklistBuilder mBuilder;

    /**
     * level text. Uses a dynamically set domain.
     * Why 10 members? because at 2 it took me an hour to figure out why we had crashed...
     * i.o.w. there can be as many levels as there are groups,
     * FIXME: Note that if a user adds more then 10 groups to a style, we'll crash...
     */
    private final int[] mLevelCol = {-2, -2, -2, -2, -2, -2, -2, -2, -2, -2};

    /**
     * Constructor.
     *
     * @param cursor  Underlying Cursor
     * @param builder Underlying Builder
     */
    public BooklistCursorRow(@NonNull final Cursor cursor,
                             @NonNull final BooklistBuilder builder) {
        super(cursor);
        mBuilder = builder;
        mMapper.addDomains(DOM_FK_BOOK_ID,

                           DOM_FK_SERIES_ID,
                           DOM_SERIES_IS_COMPLETE,
                           DOM_BOOK_SERIES_NUM,

                           DOM_FK_AUTHOR_ID,
                           DOM_AUTHOR_IS_COMPLETE,

                           DOM_BL_ABSOLUTE_POSITION,
                           DOM_BL_NODE_ROW_KIND,
                           DOM_BL_NODE_LEVEL);
    }

    public long getBookId() {
        return mMapper.getLong(DOM_FK_BOOK_ID);
    }

    public long getAuthorId() {
        return mMapper.getLong(DOM_FK_AUTHOR_ID);
    }

    public boolean hasAuthorId() {
        return mCursor.getColumnIndex(DOM_FK_AUTHOR_ID.name) >= 0;
    }

    public boolean isAuthorComplete() {
        return mMapper.getBoolean(DOM_AUTHOR_IS_COMPLETE);
    }

    public long getSeriesId() {
        return mMapper.getLong(DOM_FK_SERIES_ID);
    }

    public boolean hasSeriesId() {
        return mCursor.getColumnIndex(DOM_FK_SERIES_ID.name) >= 0;
    }

    public boolean isSeriesComplete() {
        return mMapper.getBoolean(DOM_SERIES_IS_COMPLETE);
    }

    /**
     * @return {@code true} if the list can display a series number.
     */
    public boolean hasSeriesNumber() {
        return mCursor.getColumnIndex(DOM_BOOK_SERIES_NUM.name) >= 0;
    }

    @Nullable
    public String getSeriesNumber() {
        return mMapper.getString(DOM_BOOK_SERIES_NUM);
    }

    /**
     * Get the absolute position (index) of this row in the total list of rows.
     * Note this is not the same as the {@link Cursor#getPosition()}.
     *
     * @return the absolute position
     */
    public int getAbsolutePosition() {
        return mMapper.getInt(DOM_BL_ABSOLUTE_POSITION);
    }

    /**
     * @return the row kind for this row.
     */
    @IntRange(from = 0, to = BooklistGroup.RowKind.ROW_KIND_MAX)
    public int getRowKind() {
        return mMapper.getInt(DOM_BL_NODE_ROW_KIND);
    }

    /**
     * @return the level of this row.
     */
    @IntRange(from = 1)
    public int getLevel() {
        return mMapper.getInt(DOM_BL_NODE_LEVEL);
    }


    @Nullable
    public String[] getLevelText(@NonNull final Context context) {
        return new String[]{getLevelText(context, 1),
                            getLevelText(context, 2)};
    }

    /**
     * Get the text associated with the matching level group for the current item.
     *
     * @param level to get
     *
     * @return the text for that level, or {@code null} if none present.
     */
    @Nullable
    public String getLevelText(@NonNull final Context context,
                               @IntRange(from = 1) final int level) {
        // bail out if there is no data on level
        if (mBuilder.getStyle().groupCount() < level) {
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
            final String name = mBuilder.getStyle().getGroupAt(index).getDisplayDomain().name;
            mLevelCol[index] = mCursor.getColumnIndex(name);
            if (mLevelCol[index] < 0) {
                throw new ColumnNotPresentException(name);
            }
        }

        //FIXME: seen once, can't reproduce:
        //     android.database.CursorIndexOutOfBoundsException: Index 0 requested, with a size of 0
        //        at android.database.AbstractCursor.checkPosition(AbstractCursor.java:460)
        //        at android.database.AbstractWindowedCursor.checkPosition(AbstractWindowedCursor.java:136)
        //        at android.database.AbstractWindowedCursor.getString(AbstractWindowedCursor.java:50)
        //        at com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor.getString(BooklistPseudoCursor.java:338)
        //        at com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow.getLevelText(BooklistCursorRow.java:201)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf.setHeaderText(BooksOnBookshelf.java:1551)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf.access$400(BooksOnBookshelf.java:102)
        //        at com.eleybourn.bookcatalogue.BooksOnBookshelf$4.onScrolled(BooksOnBookshelf.java:488)
        return formatRowGroup(context, level, mCursor.getString(mLevelCol[index]));
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
        if (mBuilder.getStyle().groupCount() < level) {
            throw new IllegalArgumentException(
                    "groupCount=" + mBuilder.getStyle().groupCount() + " < level=" + level);
        }

        Locale locale = LocaleUtils.from(context);

        int index = level - 1;

        switch (mBuilder.getStyle().getGroupKindAt(index)) {
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
