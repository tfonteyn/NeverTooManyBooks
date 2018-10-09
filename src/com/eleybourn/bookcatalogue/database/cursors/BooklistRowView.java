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

import android.content.res.Resources;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.DateUtils;

/**
 * RowView object for the BooklistCursor.
 *
 * Implements methods to perform common tasks on the 'current' row of the cursor.
 *
 * @author Philip Warner
 */
public class BooklistRowView extends BookRowViewBase {

    /** Underlying builder object */
    private final BooklistBuilder mBuilder;
    /** Max size of thumbnails based on preferences at object creation time */
    private final int mMaxThumbnailWidth;
    /** Max size of thumbnails based on preferences at object creation time */
    private final int mMaxThumbnailHeight;

    private int mLevelCol = -2;
    private int mLevel1Col = -2;
    private int mLevel2Col = -2;
    private int mAbsPosCol = -2;
    private int mRowKindCol = -2;

    /** linking with any table that is linked with the books table */
    private int mBookIdCol = -2;

    /** linking with author table */
    private int mAuthorIdCol = -2;

    /** linking with series table */
    private int mSeriesIdCol = -2;
    private int mSeriesNameCol = -2;
    private int mSeriesNumberCol = -2;

    /**
     * Constructor
     *
     * @param cursor  Underlying Cursor
     * @param builder Underlying Builder
     */
    public BooklistRowView(@NonNull final Cursor cursor, @NonNull final BooklistBuilder builder) {
        super(cursor);
        mBuilder = builder;

        final int extras = mBuilder.getStyle().getExtras();

        // Get thumbnail size
        int maxSize = computeThumbnailSize(extras);
        mMaxThumbnailWidth = maxSize;
        mMaxThumbnailHeight = maxSize;
    }

    /**
     * Return the thumbnail size in DP.
     *
     * @param extras Flags for style
     *
     * @return Requested thumbnail size
     */
    private int computeThumbnailSize(final int extras) {
        int maxSize;

        if ((extras & BooklistStyle.EXTRAS_THUMBNAIL_LARGE) != 0) {
            maxSize = 90;
        } else {
            maxSize = 60;
        }

        DisplayMetrics metrics = BookCatalogueApp.getAppContext().getResources().getDisplayMetrics();
        maxSize = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, maxSize, metrics));
        return maxSize;
    }

    @NonNull
    public BooklistStyle getStyle() {
        return mBuilder.getStyle();
    }

    public int getMaxThumbnailHeight() {
        return mMaxThumbnailHeight;
    }

    public int getMaxThumbnailWidth() {
        return mMaxThumbnailWidth;
    }



    /**
     * Perform any special formatting for a row group.
     *
     * @param level Level of the row group
     * @param s     Source value
     *
     * @return Formatted string, or original string on any failure
     */
    @Nullable
    private String formatRowGroup(final int level, @Nullable final String s) {
        switch (mBuilder.getStyle().getGroupAt(level).kind) {
            case RowKinds.ROW_KIND_MONTH_ADDED:
            case RowKinds.ROW_KIND_MONTH_PUBLISHED:
            case RowKinds.ROW_KIND_MONTH_READ:
            case RowKinds.ROW_KIND_UPDATE_MONTH:
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the name
                    if (i > 0 && i <= 12) {
                        // Create static formatter if necessary
                        return DateUtils.getMonthName(i);
                    }
                } catch (Exception ignored) {
                }
                break;

            case RowKinds.ROW_KIND_RATING:
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the name
                    if (i >= 0 && i <= 5) {
                        Resources r = BookCatalogueApp.getAppContext().getResources();
                        return r.getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (Exception ignored) {
                }
                break;

            default:
                break;
        }
        return s;
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getBookId() {
        if (mBookIdCol < 0) {
            mBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_ID.name);
            if (mBookIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_ID.name);
            }
        }
        return mCursor.getLong(mBookIdCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getSeriesId() {
        if (mSeriesIdCol < 0) {
            mSeriesIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_ID.name);
            if (mSeriesIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_SERIES_ID.name);
            }
        }
        return mCursor.getLong(mSeriesIdCol);
    }

    public boolean hasSeriesId() {
        if (mSeriesIdCol >= 0) {
            return true;
        }
        mSeriesIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_ID.name);
        return (mSeriesIdCol >= 0);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getSeriesName() {
        if (mSeriesNameCol < 0) {
            mSeriesNameCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_NAME.name);
            if (mSeriesNameCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_SERIES_NAME.name);
            }
        }
        return mCursor.getString(mSeriesNameCol);
    }

    /**
     * Checks if list displays series numbers anywhere.
     */
    public boolean hasSeriesNumber() {
        return mCursor.getColumnIndex(UniqueId.KEY_SERIES_NUM) >= 0;
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getSeriesNumber() {
        if (mSeriesNumberCol < 0) {
            mSeriesNumberCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_SERIES_NUM.name);
            if (mSeriesNumberCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_SERIES_NUM.name);
            }
        }
        return mCursor.getString(mSeriesNumberCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getAuthorId() {
        if (mAuthorIdCol < 0) {
            mAuthorIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_ID.name);
            if (mAuthorIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_AUTHOR_ID.name);
            }
        }
        return mCursor.getLong(mAuthorIdCol);
    }

    public boolean hasAuthorId() {
        if (mAuthorIdCol >= 0) {
            return true;
        }
        mAuthorIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_ID.name);
        return (mAuthorIdCol >= 0);
    }

    /**
     * Get the 'absolute position' for the current row. This is a value
     * generated by the builder object.
     */
    public int getAbsolutePosition() {
        if (mAbsPosCol < 0) {
            mAbsPosCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ABSOLUTE_POSITION.name);
            if (mAbsPosCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_ABSOLUTE_POSITION.name);
            }
        }
        return mCursor.getInt(mAbsPosCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public int getRowKind() {
        if (mRowKindCol < 0) {
            mRowKindCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ROW_KIND.name);
            if (mRowKindCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_ROW_KIND.name);
            }
        }
        return mCursor.getInt(mRowKindCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public int getLevel() {
        if (mLevelCol < 0) {
            mLevelCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_LEVEL.name);
            if (mLevelCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_LEVEL.name);
            }
        }
        return mCursor.getInt(mLevelCol);
    }

    /**
     * Get the text associated with the highest level group for the current item.
     */
    @Nullable
    public String getLevel1Data() {
        if (mLevel1Col < 0) {
            final String name = mBuilder.getDisplayDomain(1).name;
            mLevel1Col = mCursor.getColumnIndex(name);
            if (mLevel1Col < 0) {
                throw new DBExceptions.ColumnNotPresent(name);
            }
        }
        return formatRowGroup(0, mCursor.getString(mLevel1Col));
    }

    /**
     * Get the text associated with the second-highest level group for the current item.
     */
    @Nullable
    public String getLevel2Data() {
        if (mBuilder.getStyle().size() < 2) {
            return null;
        }

        if (mLevel2Col < 0) {
            final String name = mBuilder.getDisplayDomain(2).name;
            mLevel2Col = mCursor.getColumnIndex(name);
            if (mLevel2Col < 0) {
                throw new DBExceptions.ColumnNotPresent(name);
            }
        }
        return formatRowGroup(1, mCursor.getString(mLevel2Col));
    }

}
