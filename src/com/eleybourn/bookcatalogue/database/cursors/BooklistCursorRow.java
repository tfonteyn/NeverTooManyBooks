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
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
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
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_TITLE;

/**
 * CursorRow object for the BooklistCursor.
 * <p>
 * Implements methods to perform common tasks on the 'current' row of the cursor.
 *
 * @author Philip Warner
 */
public class BooklistCursorRow
        extends BookCursorRowBase {

    /** Underlying builder object. */
    @NonNull
    private final BooklistBuilder mBuilder;

    /** level text. Uses a dynamically set domain. */
    private final int[] mLevelCol = {-2, -2};

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
                           DOM_SERIES_TITLE,
                           DOM_SERIES_IS_COMPLETE,
                           DOM_BOOK_SERIES_NUM,

                           DOM_FK_AUTHOR_ID,
                           DOM_AUTHOR_IS_COMPLETE,

                           DOM_BL_ABSOLUTE_POSITION,
                           DOM_BL_NODE_ROW_KIND,
                           DOM_BL_NODE_LEVEL);
    }

    @NonNull
    public BooklistStyle getStyle() {
        return mBuilder.getStyle();
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

    @Nullable
    public String getSeriesName() {
        return mMapper.getString(DOM_SERIES_TITLE);
    }

    public boolean isSeriesComplete() {
        return mMapper.getBoolean(DOM_SERIES_IS_COMPLETE);
    }

    /**
     * @return <tt>true</tt> if the list can display a series number.
     */
    public boolean hasSeriesNumber() {
        return mCursor.getColumnIndex(DOM_BOOK_SERIES_NUM.name) >= 0;
    }

    @Nullable
    public String getSeriesNumber() {
        return mMapper.getString(DOM_BOOK_SERIES_NUM);
    }


    /**
     * @return the absolute position (index) of this row in the total list of rows.
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
    @IntRange(from = 1, to = 2)
    public int getLevel() {
        return mMapper.getInt(DOM_BL_NODE_LEVEL);
    }

    /**
     * Get the text associated with the matching level group for the current item.
     *
     * @param level to get; 1 or 2.
     *
     * @return the text for that level
     */
    @Nullable
    public String getLevelText(@IntRange(from = 1, to = 2) final int level) {
        // bail out if there is no data on level
        if (mBuilder.getStyle().groupCount() < level) {
            return null;
        }

        int index = level - 1;
        if (mLevelCol[index] < 0) {
            final String name = mBuilder.getStyle().getGroupAt(index).getDisplayDomain().name;
            mLevelCol[index] = mCursor.getColumnIndex(name);
            if (mLevelCol[index] < 0) {
                throw new ColumnNotPresentException(name);
            }
        }
        return formatRowGroup(level, mCursor.getString(mLevelCol[index]));
    }

    /**
     * Perform any special formatting for a row group.
     *
     * @param level Level of the row group
     * @param s     Source value
     *
     * @return Formatted string, or original string when no special format
     * was needed or on any failure
     */
    @Nullable
    private String formatRowGroup(@IntRange(from = 1, to = 2) final int level,
                                  @Nullable final String s) {
        if (s == null) {
            return null;
        }

        Context context = mBuilder.getContext();
        Locale locale = LocaleUtils.from(context);

        int index = level - 1;

        switch (mBuilder.getStyle().getGroupKindAt(index)) {
            case BooklistGroup.RowKind.READ_STATUS:
                switch (s) {
                    case "0":
                        return context.getString(R.string.lbl_unread);
                    case "1":
                        return context.getString(R.string.lbl_read);
                    default:
                        if (BuildConfig.DEBUG) {
                            Logger.debug(this,
                                  "formatRowGroup",
                                           "Unknown read status=" + s);
                        }
                        break;
                }
                return s;

            case BooklistGroup.RowKind.LANGUAGE:
                LocaleUtils.getDisplayName(locale, s);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
            case BooklistGroup.RowKind.DATE_ADDED_MONTH:
            case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
            case BooklistGroup.RowKind.DATE_READ_MONTH:
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        return DateUtils.getMonthName(locale, i, false);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
                break;

            case BooklistGroup.RowKind.RATING:
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the name
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        return context.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
                break;

        }
        return s;
    }
}
