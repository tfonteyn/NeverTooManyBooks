/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * CursorRow object for the BooklistCursor.
 * <p>
 * Provides methods access the 'level' texts and perform some common tasks on the 'current' row.
 */
public class BooklistMappedCursorRow {

    /** Associated cursor object. */
    @NonNull
    private final Cursor mCursor;
    /** Style to use while using this cursor. */
    @NonNull
    private final BooklistStyle mStyle;

    /**
     * level text. Uses a dynamically set domain.
     * Why 30 members? There can be as many levels as there are groups;
     * currently 28 of them (2019-09-29). While it's unlikely, the user COULD simply select all
     * groups... so let's guard against that.
     */
    private final int[] mLevelCol = {-2, -2, -2, -2, -2, -2, -2, -2, -2, -2,
                                     -2, -2, -2, -2, -2, -2, -2, -2, -2, -2,
                                     -2, -2, -2, -2, -2, -2, -2, -2, -2, -2};
    /** The mapper helper. */
    @NonNull
    private final CursorMapper mMapper;

    /**
     * Constructor.
     *
     * @param cursor Underlying Cursor
     * @param style  to apply
     */
    public BooklistMappedCursorRow(@NonNull final Cursor cursor,
                                   @NonNull final BooklistStyle style) {
        mCursor = cursor;
        mMapper = new CursorMapper(mCursor);
        mStyle = style;
    }

    @NonNull
    public CursorMapper getCursorMapper() {
        return mMapper;
    }

    /**
     * Get the full set of 'level' texts for this row.
     *
     * @param context Current context
     *
     * @return level-text array
     */
    @NonNull
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
        if (BuildConfig.DEBUG /* always */) {
            if (level > mLevelCol.length) {
                throw new IllegalArgumentException(
                        "level=" + level + " is larger than mLevelCol size");
            }
        }

        int index = level - 1;
        if (mLevelCol[index] < 0) {
            final String name = mStyle.getGroupAt(index).getDisplayDomain().getName();
            mLevelCol[index] = mCursor.getColumnIndex(name);
            if (mLevelCol[index] < 0) {
                throw new ColumnNotPresentException(name);
            }
        }

        //FIXME: from BoB, click book. Move sideways book to book
        // (up to BooklistPseudoCursor#CURSOR_SIZE times) then go Back to BoB

        // ==> https://github.com/eleybourn/Book-Catalogue/issues/504

        // android.database.CursorIndexOutOfBoundsException: Index 0 requested, with a size of 0
        // at android.database.AbstractCursor.checkPosition(AbstractCursor.java:460)
        // at android.database.AbstractWindowedCursor.checkPosition(AbstractWindowedCursor.java:136)
        // at android.database.AbstractWindowedCursor.getString(AbstractWindowedCursor.java:50)
        // at com.hardbacknutter.nevertoomanybooks.booklist.BooklistPseudoCursor
        // .getString(BooklistPseudoCursor.java:340)
        // at com.hardbacknutter.nevertoomanybooks.database.cursors
        // .BooklistMappedCursorRow.getLevelText(BooklistMappedCursorRow.java:170)
        // at com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf
        // .setHeaderText(BooksOnBookshelf.java:1687)
        // at com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf
        // .access$400(BooksOnBookshelf.java:103)
        // at com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf$4
        // .onScrolled(BooksOnBookshelf.java:489)

        int columnIndex = mLevelCol[index];
        try {
            //boom
            String text = mCursor.getString(columnIndex);

            return formatRowGroup(context, level, text);

        } catch (@NonNull final CursorIndexOutOfBoundsException e) {
            //DO NOT add this.toString() ... will recursively throw CursorIndexOutOfBoundsException
            Logger.error(context, this, e, "level=" + level,
                         "columnIndex=" + columnIndex);
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

        int index = level - 1;
        @BooklistGroup.RowKind.Kind
        int kind = mStyle.getGroupKindAt(index);
        switch (kind) {
            case BooklistGroup.RowKind.READ_STATUS: {
                switch (source) {
                    case "0":
                        return context.getString(R.string.lbl_unread);
                    case "1":
                        return context.getString(R.string.lbl_read);
                    default:
                        break;
                }
                break;
            }
            case BooklistGroup.RowKind.LANGUAGE: {
                return LanguageUtils.getDisplayName(Locale.getDefault(), source);
            }
            case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
            case BooklistGroup.RowKind.DATE_ADDED_MONTH:
            case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
            case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
            case BooklistGroup.RowKind.DATE_READ_MONTH: {
                try {
                    int i = Integer.parseInt(source);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        return DateUtils.getMonthName(i, false);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(context, this, e);
                }
                break;
            }
            case BooklistGroup.RowKind.RATING: {
                try {
                    int i = Integer.parseInt(source);
                    // If valid, get the name
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        return context.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(context, this, e);
                }
                break;
            }

            case BooklistGroup.RowKind.AUTHOR:
            case BooklistGroup.RowKind.BOOKSHELF:
            case BooklistGroup.RowKind.BOOK:
            case BooklistGroup.RowKind.DATE_ACQUIRED_DAY:
            case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
            case BooklistGroup.RowKind.DATE_ADDED_DAY:
            case BooklistGroup.RowKind.DATE_ADDED_YEAR:
            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_YEAR:
            case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY:
            case BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR:
            case BooklistGroup.RowKind.DATE_PUBLISHED_YEAR:
            case BooklistGroup.RowKind.DATE_READ_DAY:
            case BooklistGroup.RowKind.DATE_READ_YEAR:
            case BooklistGroup.RowKind.FORMAT:
            case BooklistGroup.RowKind.GENRE:
            case BooklistGroup.RowKind.LOANED:
            case BooklistGroup.RowKind.LOCATION:
            case BooklistGroup.RowKind.PUBLISHER:
            case BooklistGroup.RowKind.SERIES:
            case BooklistGroup.RowKind.TITLE_LETTER:
                // no special formatting needed.
                return source;

            default:
                throw new UnexpectedValueException(kind);

        }
        Logger.warnWithStackTrace(context, this, "formatRowGroup",
                                  "source=" + source,
                                  "level=" + level,
                                  "kind=" + kind);
        return source;
    }

    @NonNull
    @Override
    public String toString() {
        return "BooklistMappedCursorRow{"
               + "mCursor=" + mCursor
               + ", mStyle=" + mStyle
               + ", mLevelCol=" + Arrays.toString(mLevelCol)
               + ", mMapper=" + mMapper
               + '}';
    }
}
