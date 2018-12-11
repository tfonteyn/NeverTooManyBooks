package com.eleybourn.bookcatalogue.booklist;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.utils.UniqueMap;

import java.util.Map;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_LAST_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE_LETTER;

/**
 * Static definitions of the kinds of rows that can be displayed and summarized.
 * Adding new row types needs to involve changes to:
 *
 * - {@link BooklistBuilder} (to build the correct SQL)
 * - {@link BooksMultiTypeListHandler} (to know what to do with the new type)
 *
 * @author Philip Warner
 */
public final class RowKinds {

    // The code relies on BOOK being == 0
    public static final int ROW_KIND_BOOK = 0;
    public static final int ROW_KIND_AUTHOR = 1;
    public static final int ROW_KIND_SERIES = 2;
    public static final int ROW_KIND_GENRE = 3;
    public static final int ROW_KIND_PUBLISHER = 4;
    public static final int ROW_KIND_READ_STATUS = 5;
    public static final int ROW_KIND_LOANED = 6;
    public static final int ROW_KIND_DATE_PUBLISHED_YEAR = 7;
    public static final int ROW_KIND_DATE_PUBLISHED_MONTH = 8;
    public static final int ROW_KIND_TITLE_LETTER = 9;
    public static final int ROW_KIND_DATE_ADDED_YEAR = 10;
    public static final int ROW_KIND_DATE_ADDED_MONTH = 11;
    public static final int ROW_KIND_DATE_ADDED_DAY = 12;
    public static final int ROW_KIND_FORMAT = 13;
    public static final int ROW_KIND_DATE_READ_YEAR = 14;
    public static final int ROW_KIND_DATE_READ_MONTH = 15;
    public static final int ROW_KIND_DATE_READ_DAY = 16;
    public static final int ROW_KIND_LOCATION = 17;
    public static final int ROW_KIND_LANGUAGE = 18;
    public static final int ROW_KIND_DATE_LAST_UPDATE_YEAR = 19;
    public static final int ROW_KIND_DATE_LAST_UPDATE_MONTH = 20;
    public static final int ROW_KIND_DATE_LAST_UPDATE_DAY = 21;
    public static final int ROW_KIND_RATING = 22;
    public static final int ROW_KIND_BOOKSHELF = 23;
    public static final int ROW_KIND_DATE_ACQUIRED_YEAR = 24;
    public static final int ROW_KIND_DATE_ACQUIRED_MONTH = 25;
    public static final int ROW_KIND_DATE_ACQUIRED_DAY = 26;

    // NEWKIND: ROW_KIND_x
    // the highest valid index of kinds  ALWAYS update after adding a row kind...
    public static final int ROW_KIND_MAX = 26;

    //TOMF: add support for all? new columns added in CatalogueDBHelper: 2018-11-14
    // don't forget MONTH special formatting, search specific for the MONTH kinds
    /*
        DOM_FIRST_PUBLICATION + " date"
        DOM_BOOK_EDITION_BITMASK + " integer NOT NULL default 0"

        DOM_BOOK_PRICE_LISTED
        DOM_BOOK_PRICE_LISTED_CURRENCY + " text default ''"
        DOM_BOOK_PRICE_PAID + " text default ''"
        DOM_BOOK_PRICE_PAID_CURRENCY + " text default ''"
     */

    private static final Map<Integer, RowKind> allKinds = new UniqueMap<>();

    static {
        RowKind rowkind;

        rowkind = new RowKind(ROW_KIND_BOOK, R.string.lbl_book);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_AUTHOR, R.string.lbl_author, "a", DOM_FK_AUTHOR_ID);
        rowkind.setDisplayDomain(DOM_AUTHOR_FORMATTED);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_SERIES, R.string.lbl_series, "s", DOM_FK_SERIES_ID);
        rowkind.setDisplayDomain(DOM_SERIES_NAME);
        allKinds.put(rowkind.kind, rowkind);

        //all others will use the underlying domain as the displayDomain
        rowkind = new RowKind(ROW_KIND_GENRE, R.string.lbl_genre, "g", DOM_BOOK_GENRE);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_PUBLISHER, R.string.lbl_publisher, "p", DOM_BOOK_PUBLISHER);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_READ_STATUS, R.string.read_amp_unread, "r", DOM_READ_STATUS);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_LOANED, R.string.loaned, "l", DOM_LOANED_TO);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_PUBLISHED_YEAR, R.string.lbl_publication_year, "yrp", DOM_DATE_PUBLISHED_YEAR);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_PUBLISHED_MONTH, R.string.lbl_publication_month, "mnp", DOM_DATE_PUBLISHED_MONTH);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_TITLE_LETTER, R.string.style_builtin_title_first_letter, "t", DOM_TITLE_LETTER);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ADDED_YEAR, R.string.lbl_added_year, "yra", DOM_DATE_ADDED_YEAR);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ADDED_MONTH, R.string.lbl_added_month, "mna", DOM_DATE_ADDED_MONTH);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ADDED_DAY, R.string.lbl_added_day, "dya", DOM_DATE_ADDED_DAY);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_FORMAT, R.string.lbl_format, "fmt", DOM_BOOK_FORMAT);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_READ_YEAR, R.string.lbl_read_year, "yrr", DOM_DATE_READ_YEAR);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_READ_MONTH, R.string.lbl_read_month, "mnr", DOM_DATE_READ_MONTH);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_READ_DAY, R.string.lbl_read_day, "dyr", DOM_DATE_READ_DAY);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_LOCATION, R.string.lbl_location, "loc", DOM_BOOK_LOCATION);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_LANGUAGE, R.string.lbl_language, "lang", DOM_BOOK_LANGUAGE);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_LAST_UPDATE_YEAR, R.string.lbl_update_year, "yru", DOM_DATE_LAST_UPDATE_YEAR);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_LAST_UPDATE_MONTH, R.string.lbl_update_month, "mnu", DOM_DATE_UPDATE_MONTH);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_LAST_UPDATE_DAY, R.string.lbl_update_day, "dyu", DOM_DATE_UPDATE_DAY);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_RATING, R.string.lbl_rating, "rat", DOM_BOOK_RATING);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_BOOKSHELF, R.string.lbl_bookshelf, "shelf", DOM_BOOKSHELF);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ACQUIRED_YEAR, R.string.lbl_date_acquired_year, "yrac", DOM_DATE_ACQUIRED_YEAR);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ACQUIRED_MONTH, R.string.lbl_date_acquired_month, "mnac", DOM_DATE_ACQUIRED_MONTH);
        allKinds.put(rowkind.kind, rowkind);

        rowkind = new RowKind(ROW_KIND_DATE_ACQUIRED_DAY, R.string.lbl_date_acquired_day, "dyac", DOM_DATE_ACQUIRED_DAY);
        allKinds.put(rowkind.kind, rowkind);

        // NEWKIND: ROW_KIND_x

        // Sanity check as our code relies on this
        for (int kind = 0; kind < (ROW_KIND_MAX - 1); kind++) {
            if (!allKinds.containsKey(kind))
                throw new IllegalStateException("Missing kind " + kind);
        }
        // Sanity check as our code relies on this (for loop starting at 1)
        if (ROW_KIND_BOOK != 0) {
            throw new IllegalStateException("ROW_KIND_BOOK was " + ROW_KIND_BOOK);
        }
    }

    /**
     * Don't use {@link #ROW_KIND_MAX} for code. Use this method.
     */
    public static int size() {
        return allKinds.size();
    }

    @NonNull
    public static RowKind getRowKind(final @IntRange(from = 0, to = ROW_KIND_MAX) int kind) {
        return allKinds.get(kind);
    }

    public static class RowKind {
        @IntRange(from = 0, to = RowKinds.ROW_KIND_MAX)
        public final int kind;

        private final BooklistStyle.CompoundKey mCompoundKey;

        @StringRes
        private final int mLabelId;

        private DomainDefinition mDisplayDomain;

        /**
         * Just define a domain. Used for {@link #ROW_KIND_BOOK} only.
         * (honest? to defy lint...)
         */
        RowKind(final @IntRange(from = 0, to = ROW_KIND_MAX) int kind,
                final @StringRes int labelId) {
            this.kind = kind;
            this.mLabelId = labelId;
            mCompoundKey = null;
            this.mDisplayDomain = null;
        }

        /**
         * @param domains all underlying domains. The first element will be used as the displayDomain.
         */
        RowKind(final @IntRange(from = 0, to = ROW_KIND_MAX) int kind,
                final @StringRes int labelId,
                final @NonNull String prefix,
                final @NonNull DomainDefinition... domains) {
            this.kind = kind;
            this.mLabelId = labelId;
            mCompoundKey = new BooklistStyle.CompoundKey(prefix, domains);
            mDisplayDomain = domains[0];
        }

        @NonNull
        public DomainDefinition getDisplayDomain() {
            return mDisplayDomain;
        }

        void setDisplayDomain(final DomainDefinition displayDomain) {
            this.mDisplayDomain = displayDomain;
        }

        /**
         * Compound key of this RowKind ({@link BooklistGroup}).
         *
         * The name will be of the form 'prefix/<n>' where 'prefix' if the prefix specific to the RowKind,
         * and <n> the id of the row, eg. 's/18' for Series with id=18
         */
        @NonNull
        BooklistStyle.CompoundKey getCompoundKey() {
            //noinspection ConstantConditions
            return mCompoundKey;
        }

        String getName() {
            return BookCatalogueApp.getResourceString(mLabelId);
        }

    }
}
