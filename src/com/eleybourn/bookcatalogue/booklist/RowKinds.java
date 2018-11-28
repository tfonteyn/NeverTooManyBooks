package com.eleybourn.bookcatalogue.booklist;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.UniqueMap;

import java.util.Map;

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
    public static final int ROW_KIND_BOOK = 0;                      // Supported
    public static final int ROW_KIND_AUTHOR = 1;                    // Supported
    public static final int ROW_KIND_SERIES = 2;                    // Supported
    public static final int ROW_KIND_GENRE = 3;                     // Supported
    public static final int ROW_KIND_PUBLISHER = 4;                 // Supported
    public static final int ROW_KIND_READ_AND_UNREAD = 5;           // Supported
    public static final int ROW_KIND_LOANED = 6;                    // Supported
    public static final int ROW_KIND_DATE_PUBLISHED_YEAR = 7;       // Supported
    public static final int ROW_KIND_DATE_PUBLISHED_MONTH = 8;      // Supported
    public static final int ROW_KIND_TITLE_LETTER = 9;              // Supported
    public static final int ROW_KIND_DATE_ADDED_YEAR = 10;          // Supported
    public static final int ROW_KIND_DATE_ADDED_MONTH = 11;         // Supported
    public static final int ROW_KIND_DATE_ADDED_DAY = 12;           // Supported
    public static final int ROW_KIND_FORMAT = 13;                   // Supported
    public static final int ROW_KIND_DATE_READ_YEAR = 14;           // Supported
    public static final int ROW_KIND_DATE_READ_MONTH = 15;          // Supported
    public static final int ROW_KIND_DATE_READ_DAY = 16;            // Supported
    public static final int ROW_KIND_LOCATION = 17;                 // Supported
    public static final int ROW_KIND_LANGUAGE = 18;                 // Supported
    public static final int ROW_KIND_DATE_LAST_UPDATE_YEAR = 19;    // Supported
    public static final int ROW_KIND_DATE_LAST_UPDATE_MONTH = 20;   // Supported
    public static final int ROW_KIND_DATE_LAST_UPDATE_DAY = 21;     // Supported
    public static final int ROW_KIND_RATING = 22;                   // Supported
    public static final int ROW_KIND_BOOKSHELF = 23;                // Supported
    public static final int ROW_KIND_DATE_ACQUIRED_YEAR = 24;       // Supported
    public static final int ROW_KIND_DATE_ACQUIRED_MONTH = 25;      // Supported
    public static final int ROW_KIND_DATE_ACQUIRED_DAY = 26;        // Supported

    // NEWKIND: ROW_KIND_x
    // the total number of kinds (e.g. the size()) ALWAYS update after adding a row kind...
    private static final int ROW_KIND_TOTAL = 27;

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
        allKinds.put(ROW_KIND_BOOK, new RowKind(R.string.lbl_book, "" /* not used */));

        allKinds.put(ROW_KIND_AUTHOR, new RowKind(R.string.lbl_author, "a"));
        allKinds.put(ROW_KIND_SERIES, new RowKind(R.string.lbl_series, "s"));
        allKinds.put(ROW_KIND_GENRE, new RowKind(R.string.lbl_genre, "g"));
        allKinds.put(ROW_KIND_PUBLISHER, new RowKind(R.string.lbl_publisher, "p"));
        allKinds.put(ROW_KIND_READ_AND_UNREAD, new RowKind(R.string.read_amp_unread, "r"));
        allKinds.put(ROW_KIND_LOANED, new RowKind(R.string.loaned, "l"));
        allKinds.put(ROW_KIND_DATE_PUBLISHED_YEAR, new RowKind(R.string.publication_year, "yrp"));
        allKinds.put(ROW_KIND_DATE_PUBLISHED_MONTH, new RowKind(R.string.publication_month, "mnp"));
        allKinds.put(ROW_KIND_TITLE_LETTER, new RowKind(R.string.style_builtin_title_first_letter, "t"));
        allKinds.put(ROW_KIND_DATE_ADDED_YEAR, new RowKind(R.string.added_year, "yra"));
        allKinds.put(ROW_KIND_DATE_ADDED_MONTH, new RowKind(R.string.added_month, "mna"));
        allKinds.put(ROW_KIND_DATE_ADDED_DAY, new RowKind(R.string.added_day, "dya"));
        allKinds.put(ROW_KIND_FORMAT, new RowKind(R.string.lbl_format, "fmt"));
        allKinds.put(ROW_KIND_DATE_READ_YEAR, new RowKind(R.string.read_year, "yrr"));
        allKinds.put(ROW_KIND_DATE_READ_MONTH, new RowKind(R.string.read_month, "mnr"));
        allKinds.put(ROW_KIND_DATE_READ_DAY, new RowKind(R.string.read_day, "dyr"));
        allKinds.put(ROW_KIND_LOCATION, new RowKind(R.string.lbl_location, "loc"));
        allKinds.put(ROW_KIND_LANGUAGE, new RowKind(R.string.lbl_language, "lang"));
        allKinds.put(ROW_KIND_DATE_LAST_UPDATE_YEAR, new RowKind(R.string.update_year, "yru"));
        allKinds.put(ROW_KIND_DATE_LAST_UPDATE_MONTH, new RowKind(R.string.update_month, "mnu"));
        allKinds.put(ROW_KIND_DATE_LAST_UPDATE_DAY, new RowKind(R.string.update_day, "dyu"));
        allKinds.put(ROW_KIND_RATING, new RowKind(R.string.lbl_rating, "rat"));
        allKinds.put(ROW_KIND_BOOKSHELF, new RowKind(R.string.lbl_bookshelf, "shelf"));
        allKinds.put(ROW_KIND_DATE_ACQUIRED_YEAR, new RowKind(R.string.lbl_date_acquired_year, "yrac"));
        allKinds.put(ROW_KIND_DATE_ACQUIRED_MONTH, new RowKind(R.string.lbl_date_acquired_month, "mnac"));
        allKinds.put(ROW_KIND_DATE_ACQUIRED_DAY, new RowKind(R.string.lbl_date_acquired_day, "dyac"));
        // NEWKIND: ROW_KIND_x

        // Sanity check as our code relies on this
        for (int kind = 0; kind < ROW_KIND_TOTAL; kind++) {
            if (!allKinds.containsKey(kind))
                throw new IllegalStateException("Missing kind " + kind);
        }
        // Sanity check as our code relies on this
        if (ROW_KIND_BOOK != 0) {
            throw new IllegalStateException("ROW_KIND_BOOK was " + ROW_KIND_BOOK);
        }
    }

    public static int size() {
        return allKinds.size();
    }

    public static String getName(final int kind) {
        return BookCatalogueApp.getResourceString(allKinds.get(kind).nameId);
    }

    public static String getPrefix(final int kind) {
        return allKinds.get(kind).prefix;
    }

    static class RowKind {
        @StringRes
        final int nameId;
        final String prefix;

        RowKind(final @StringRes int nameId, final @NonNull String prefix) {
            this.nameId = nameId;
            this.prefix = prefix;
        }
    }
}
