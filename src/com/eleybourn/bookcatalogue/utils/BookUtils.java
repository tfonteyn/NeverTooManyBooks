/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BookDetailsActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.io.File;

/**
 * Class to implement common Book functions
 *
 * @author pjw
 */
public class BookUtils {

    private BookUtils() {
    }

    /**
     * Open a new book editing activity with fields copied from saved book.
     * Saved book (original of duplicating) is defined by its row _id in database.
     *
     * @param rowId The id of the book to copy fields
     */
    public static void duplicateBook(@NonNull final Activity activity,
                                     @NonNull final CatalogueDBAdapter db,
                                     final long rowId) {
        Intent intent = new Intent(activity, BookDetailsActivity.class);
        final Bundle book = new Bundle();
        try (Cursor cursor = db.fetchBookById(rowId)) {
            cursor.moveToFirst();
            book.putLong(UniqueId.KEY_ANTHOLOGY_MASK, cursor.getLong(cursor.getColumnIndex(DatabaseDefinitions.DOM_ANTHOLOGY_MASK.name)));
            book.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_DATE_PUBLISHED.name)));
            book.putString(UniqueId.KEY_DESCRIPTION, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_DESCRIPTION.name)));
            book.putString(UniqueId.KEY_BOOK_FORMAT, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_FORMAT.name)));
            book.putString(UniqueId.KEY_BOOK_GENRE, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_GENRE.name)));
            book.putString(UniqueId.KEY_ISBN, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_ISBN.name)));
            book.putString(UniqueId.KEY_BOOK_LIST_PRICE, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LIST_PRICE.name)));
            book.putString(UniqueId.KEY_BOOK_LANGUAGE, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LANGUAGE.name)));
            book.putString(UniqueId.KEY_BOOK_LOCATION, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LOCATION.name)));
            book.putString(UniqueId.KEY_NOTES, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_NOTES.name)));
            book.putString(UniqueId.KEY_BOOK_PAGES, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PAGES.name)));
            book.putString(UniqueId.KEY_PUBLISHER, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_PUBLISHER.name)));
            book.putString(UniqueId.KEY_BOOK_RATING, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_RATING.name)));
            book.putString(UniqueId.KEY_BOOK_READ, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ.name)));
            book.putString(UniqueId.KEY_BOOK_READ_END, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ_END.name)));
            book.putString(UniqueId.KEY_BOOK_READ_START, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ_START.name)));
            book.putString(UniqueId.KEY_BOOK_SIGNED, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_SIGNED.name)));
            book.putString(UniqueId.KEY_TITLE, cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name)));

            book.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, db.getBookAuthorList(rowId));
            book.putSerializable(UniqueId.BKEY_SERIES_ARRAY, db.getBookSeriesList(rowId));
            book.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, db.getBookAnthologyTitleList(rowId));

            intent.putExtra(UniqueId.BKEY_BOOK_DATA, book);

            activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_MANUALLY);
        } catch (CursorIndexOutOfBoundsException e) {
            Toast.makeText(activity, R.string.unknown_error, Toast.LENGTH_LONG).show();
            Logger.logError(e);
        }
    }

    /**
     * Delete book by its database row _id and close current activity.
     *
     * @param rowId The database id of the book for deleting
     */
    public static void deleteBook(@NonNull final Context context,
                                  @NonNull final CatalogueDBAdapter db,
                                  final long rowId,
                                  final Runnable runnable) {
        int res = StandardDialogs.deleteBookAlert(context, db, rowId, new Runnable() {
            @Override
            public void run() {
                db.purgeAuthors();
                db.purgeSeries();
                if (runnable != null) {
                    runnable.run();
                }
            }
        });
        if (res != 0) {
            Toast.makeText(context, res, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Perform sharing of book by its database rowId. Create chooser with matched
     * apps for sharing some text like the next:<br>
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     *
     * @param rowId The database id of the book for deleting
     */
    public static void shareBook(@NonNull final Context context,
                                 @NonNull final CatalogueDBAdapter db,
                                 final long rowId) {
        String title;
        double rating;
        String ratingString = "";
        String author;
        String series;

        try (Cursor cursor = db.fetchBookById(rowId)) {
            cursor.moveToFirst();
            title = cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name));
            rating = cursor.getDouble(cursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_RATING.name));
            author = cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name));
            series = cursor.getString(cursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_FORMATTED.name));
        }

        File image = StorageUtils.getCoverFile(db.getBookUuid(rowId));

        if (!series.isEmpty()) {
            series = " (" + series.replace("#", "%23") + ")";
        }
        //remove trailing 0's
        if (rating > 0) {
            int ratingTmp = (int) rating;
            double decimal = rating - ratingTmp;
            if (decimal > 0) {
                ratingString = rating + "/5";
            } else {
                ratingString = ratingTmp + "/5";
            }
        }

        if (!ratingString.isEmpty()) {
            ratingString = "(" + ratingString + ")";
        }


        // prepare the cover to post
        Uri coverURI = FileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY, image);

        /*
        FIXME: check this
		 * There's a problem with the facebook app in android, so despite it being shown on the list
		 * it will not post any text unless the user types it.
		 */
        Intent share = new Intent(Intent.ACTION_SEND);
        String text = context.getString(R.string.share_book_im_reading, title, author, series, ratingString);
        share.putExtra(Intent.EXTRA_TEXT, text);
        share.putExtra(Intent.EXTRA_STREAM, coverURI);
        share.setType("text/plain");

        context.startActivity(Intent.createChooser(share, context.getString(R.string.share)));
    }

    /**
     * Update the 'read' status of a book in the database
     * The 'book' will have its 'read' status updated ONLY if the update went through.
     *
     * @param db  database
     * @param bookData book to update
     *
     * @return true/false as result from database update
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final CatalogueDBAdapter db,
                                  @NonNull final BookData bookData,
                                  final boolean read) {
        int prevRead = bookData.getInt(UniqueId.KEY_BOOK_READ);
        String prevReadEnd = bookData.getString(UniqueId.KEY_BOOK_READ_END);

        bookData.putInt(UniqueId.KEY_BOOK_READ, read ? 1 : 0);
        bookData.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.todaySqlDateOnly());

        if (!( 1== db.updateBook(bookData.getRowId(), bookData, 0))) {
            bookData.putInt(UniqueId.KEY_BOOK_READ, prevRead);
            bookData.putString(UniqueId.KEY_BOOK_READ_END, prevReadEnd);
            return false;
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final CatalogueDBAdapter db,
                                  final long bookId,
                                  final boolean read) {
        BookData bookData = new BookData(bookId);
        bookData.putBoolean(UniqueId.KEY_BOOK_READ, read);
        bookData.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.todaySqlDateOnly());
        return 1 == db.updateBook(bookId, bookData, 0);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(final long bookId, final boolean read) {
        CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();
        boolean ok =setRead(db, bookId, read);
        db.close();
        return ok;
    }
}
