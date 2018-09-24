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

package com.eleybourn.bookcatalogue.booklist;

import android.content.res.Resources;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LEVEL;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ROW_KIND;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * RowView object for the BooklistCursor.
 *
 * Implements methods to perform common tasks on the 'current' row of the cursor.
 *
 * @author Philip Warner
 */
public class BooklistRowView {
    /** ID counter */
    private static Integer mBooklistRowViewIdCounter = 0;
    /** Underlying cursor */
    private final Cursor mCursor;
    /** Underlying builder object */
    private final BooklistBuilder mBuilder;
    /** Max size of thumbnails based on preferences at object creation time */
    private final int mMaxThumbnailWidth;
    /** Max size of thumbnails based on preferences at object creation time */
    private final int mMaxThumbnailHeight;
    /** Internal ID for this RowView */
    private final long mId;

    private int mLevel1Col = -2;
    private int mLevel2Col = -2;
    private int mAbsPosCol = -2;
    private int mBookIdCol = -2;
    private int mBookUuidCol = -2;
    private int mSeriesIdCol = -2;
    private int mAuthorIdCol = -2;
    private int mKindCol = -2;
    private int mTitleCol = -2;
    private int mPublisherCol = -2;
    private int mLanguageCol = -2;
    private int mLevelCol = -2;
    private int mFormatCol = -2;
    private int mGenreCol = -2;
    private int mLocationCol = -2;
    private int mSeriesNameCol = -2;
    private int mSeriesNumberCol = -2;
    private int mReadCol = -2;

    /**
     * Constructor
     *
     * @param c       Underlying Cursor
     * @param builder Underlying Builder
     */
    BooklistRowView(@NonNull final BooklistCursor c, @NonNull final BooklistBuilder builder) {
        // Allocate ID
        synchronized (mBooklistRowViewIdCounter) {
            mId = ++mBooklistRowViewIdCounter;
        }

        // Save underlying objects.
        mCursor = c;
        mBuilder = builder;

        final int extras = mBuilder.getStyle().getExtras();

        // Get thumbnail size
        int maxSize = computeThumbnailSize(extras);
        mMaxThumbnailWidth = maxSize;
        mMaxThumbnailHeight = maxSize;
    }

    /**
     * Constructor
     *
     * @param c       Underlying Cursor
     * @param builder Underlying Builder
     */
    BooklistRowView(@NonNull final BooklistPseudoCursor c, @NonNull final BooklistBuilder builder) {
        // Allocate ID
        synchronized (mBooklistRowViewIdCounter) {
            mId = ++mBooklistRowViewIdCounter;
        }

        // Save underlying objects.
        mCursor = c;

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

    /**
     * Accessor
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mBuilder.getStyle();
    }

    public long getId() {
        return mId;
    }

    public int getMaxThumbnailHeight() {
        return mMaxThumbnailHeight;
    }

    public int getMaxThumbnailWidth() {
        return mMaxThumbnailWidth;
    }

    /**
     * Checks if list displays series numbers anywhere.
     */
    public boolean hasSeries() {
        return hasColumn(UniqueId.KEY_SERIES_NUM);
    }

    /**
     * Query underlying cursor for column index.
     */
    public int getColumnIndex(@NonNull final String columnName) {
        return mCursor.getColumnIndex(columnName);
    }

    /**
     * Get string from underlying cursor given a column index.
     */
    @Nullable
    public String getString(final int columnIndex) {
        return mCursor.getString(columnIndex);
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
                throw new RuntimeException("Column " + name + " not present in cursor");
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
                throw new RuntimeException("Column " + name + " not present in cursor");
            }
        }
        return formatRowGroup(1, mCursor.getString(mLevel2Col));
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
     * Check if a given column is present in underlying cursor.
     */
    private boolean hasColumn(@NonNull final String name) {
        return mCursor.getColumnIndex(name) >= 0;
    }

    /**
     * Get the 'absolute position' for the current row. This is a value
     * generated by the builder object.
     */
    public int getAbsolutePosition() {
        if (mAbsPosCol < 0) {
            final String name = DOM_ABSOLUTE_POSITION.name;
            mAbsPosCol = mCursor.getColumnIndex(name);
            if (mAbsPosCol < 0) {
                throw new RuntimeException("Column " + name + " not present in cursor");
            }
        }
        return mCursor.getInt(mAbsPosCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getBookId() {
        if (mBookIdCol < 0) {
            mBookIdCol = mCursor.getColumnIndex(DOM_BOOK_ID.name);
            if (mBookIdCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_ID + " not present in cursor");
            }
        }
        return mCursor.getLong(mBookIdCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getBookUuid() {
        if (mBookUuidCol < 0) {
            mBookUuidCol = mCursor.getColumnIndex(DOM_BOOK_UUID.name);
            if (mBookUuidCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_UUID + " not present in cursor");
            }
        }
        return mCursor.getString(mBookUuidCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getSeriesId() {
        if (mSeriesIdCol < 0) {
            mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
            if (mSeriesIdCol < 0) {
                throw new RuntimeException("Column " + DOM_SERIES_ID + " not present in cursor");
            }
        }
        return mCursor.getLong(mSeriesIdCol);
    }

    public boolean hasSeriesId() {
        if (mSeriesIdCol >= 0) {
            return true;
        }
        mSeriesIdCol = mCursor.getColumnIndex(DOM_SERIES_ID.name);
        return (mSeriesIdCol >= 0);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public long getAuthorId() {
        if (mAuthorIdCol < 0) {
            mAuthorIdCol = mCursor.getColumnIndex(DOM_AUTHOR_ID.name);
            if (mAuthorIdCol < 0) {
                throw new RuntimeException("Column " + DOM_AUTHOR_ID + " not present in cursor");
            }
        }
        return mCursor.getLong(mAuthorIdCol);
    }

    public boolean hasAuthorId() {
        if (mAuthorIdCol >= 0) {
            return true;
        }
        mAuthorIdCol = mCursor.getColumnIndex(DOM_AUTHOR_ID.name);
        return (mAuthorIdCol >= 0);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public int getKind() {
        if (mKindCol < 0) {
            mKindCol = mCursor.getColumnIndex(DOM_ROW_KIND.name);
            if (mKindCol < 0) {
                throw new RuntimeException("Column " + DOM_ROW_KIND + " not present in cursor");
            }
        }
        return mCursor.getInt(mKindCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getTitle() {
        if (mTitleCol < 0) {
            mTitleCol = mCursor.getColumnIndex(DOM_TITLE.name);
            if (mTitleCol < 0) {
                throw new RuntimeException("Column " + DOM_TITLE + " not present in cursor");
            }
        }
        return mCursor.getString(mTitleCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getPublisherName() {
        if (mPublisherCol < 0) {
            mPublisherCol = mCursor.getColumnIndex(DOM_PUBLISHER.name);
            if (mPublisherCol < 0) {
                throw new RuntimeException("Column " + DOM_PUBLISHER + " not present in cursor");
            }
        }
        return mCursor.getString(mPublisherCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getLanguage() {
        if (mLanguageCol < 0) {
            mLanguageCol = mCursor.getColumnIndex(DOM_BOOK_LANGUAGE.name);
            if (mLanguageCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_LANGUAGE + " not present in cursor");
            }
        }
        return mCursor.getString(mLanguageCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public int getLevel() {
        if (mLevelCol < 0) {
            mLevelCol = mCursor.getColumnIndex(DOM_LEVEL.name);
            if (mLevelCol < 0) {
                throw new RuntimeException("Column " + DOM_LEVEL + " not present in cursor");
            }
        }
        return mCursor.getInt(mLevelCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getFormat() {
        if (mFormatCol < 0) {
            mFormatCol = mCursor.getColumnIndex(DOM_BOOK_FORMAT.name);
            if (mFormatCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_FORMAT + " not present in cursor");
            }
        }
        return mCursor.getString(mFormatCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getGenre() {
        if (mGenreCol < 0) {
            mGenreCol = mCursor.getColumnIndex(DOM_BOOK_GENRE.name);
            if (mGenreCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_GENRE + " not present in cursor");
            }
        }
        return mCursor.getString(mGenreCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getLocation() {
        if (mLocationCol < 0) {
            mLocationCol = mCursor.getColumnIndex(DOM_BOOK_LOCATION.name);
            if (mLocationCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_LOCATION + " not present in cursor");
            }
        }
        return mCursor.getString(mLocationCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getSeriesName() {
        if (mSeriesNameCol < 0) {
            mSeriesNameCol = mCursor.getColumnIndex(DOM_SERIES_NAME.name);
            if (mSeriesNameCol < 0) {
                throw new RuntimeException("Column " + DOM_SERIES_NAME + " not present in cursor");
            }
        }
        return mCursor.getString(mSeriesNameCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @Nullable
    public String getSeriesNumber() {
        if (mSeriesNumberCol < 0) {
            mSeriesNumberCol = mCursor.getColumnIndex(DOM_SERIES_NUM.name);
            if (mSeriesNumberCol < 0) {
                throw new RuntimeException("Column " + DOM_SERIES_NUM + " not present in cursor");
            }
        }
        return mCursor.getString(mSeriesNumberCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    public boolean isRead() {
        if (mReadCol < 0) {
            mReadCol = mCursor.getColumnIndex(DOM_BOOK_READ.name);
            if (mReadCol < 0) {
                throw new RuntimeException("Column " + DOM_BOOK_READ + " not present in cursor");
            }
        }
        return mCursor.getLong(mReadCol) == 1;
    }
}
