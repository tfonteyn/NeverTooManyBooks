/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file inputStream part of Book Catalogue.
 *
 * Book Catalogue inputStream free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue inputStream distributed in the hope that it will be useful,
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
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookDetailsActivity;
import com.eleybourn.bookcatalogue.entities.BookRowView;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.BookData;

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
     * Saved book (original of duplicating) inputStream defined by its row _id in database.
     *
     * @param bookId The id of the book to copy fields
     */
    public static void duplicateBook(@NonNull final Activity activity,
                                     @NonNull final CatalogueDBAdapter db,
                                     final long bookId) {
        Intent intent = new Intent(activity, BookDetailsActivity.class);
        final Bundle book = new Bundle();
        try (BooksCursor cursor = db.fetchBookById(bookId)) {
            BookRowView bookRowView = cursor.getRowView();

            cursor.moveToFirst();

            book.putLong(UniqueId.KEY_ANTHOLOGY_MASK, bookRowView.getAnthologyMask());
            book.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, bookRowView.getDatePublished());
            book.putString(UniqueId.KEY_DESCRIPTION, bookRowView.getDescription());
            book.putString(UniqueId.KEY_BOOK_FORMAT, bookRowView.getFormat());
            book.putString(UniqueId.KEY_BOOK_GENRE, bookRowView.getGenre());
            book.putString(UniqueId.KEY_ISBN, bookRowView.getIsbn());
            book.putString(UniqueId.KEY_BOOK_LIST_PRICE, bookRowView.getListPrice());
            book.putString(UniqueId.KEY_BOOK_LANGUAGE, bookRowView.getLanguage());
            book.putString(UniqueId.KEY_BOOK_LOCATION, bookRowView.getLocation());
            book.putString(UniqueId.KEY_NOTES, bookRowView.getNotes());
            book.putString(UniqueId.KEY_BOOK_PAGES, bookRowView.getPages());
            book.putString(UniqueId.KEY_BOOK_PUBLISHER, bookRowView.getPublisherName());
            book.putDouble(UniqueId.KEY_BOOK_RATING, bookRowView.getRating());
            book.putInt(UniqueId.KEY_BOOK_READ, bookRowView.getRead());
            book.putString(UniqueId.KEY_BOOK_READ_END, bookRowView.getReadEnd());
            book.putString(UniqueId.KEY_BOOK_READ_START, bookRowView.getReadStart());
            book.putInt(UniqueId.KEY_BOOK_SIGNED, bookRowView.getSigned());
            book.putString(UniqueId.KEY_TITLE, bookRowView.getTitle());

            book.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, db.getBookAuthorList(bookId));
            book.putSerializable(UniqueId.BKEY_SERIES_ARRAY, db.getBookSeriesList(bookId));
            book.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, db.getBookAnthologyTitleList(bookId));

            intent.putExtra(UniqueId.BKEY_BOOK_DATA, book);

            activity.startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_BOOK_MANUALLY);
        } catch (CursorIndexOutOfBoundsException e) {
            StandardDialogs.showQuickNotice(activity, R.string.unknown_error);
            Logger.logError(e);
        }
    }

    /**
     * Delete book by its database row _id and close current activity.
     *
     * @param bookId  the book to delete
     */
    public static void deleteBook(@NonNull final Activity activity,
                                  @NonNull final CatalogueDBAdapter db,
                                  final long bookId,
                                  final Runnable runnable) {
        int res = StandardDialogs.deleteBookAlert(activity, db, bookId, new Runnable() {
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
            StandardDialogs.showQuickNotice(activity, res);
        }
    }

    /**
     * Perform sharing of book by its database rowId. Create chooser with matched
     * apps for sharing some text like the next:<br>
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     *
     * @param bookId  of the book
     */
    public static void shareBook(@NonNull final Context context,
                                 @NonNull final CatalogueDBAdapter db,
                                 final long bookId) {
        String title;
        double rating;
        String ratingString = "";
        String author;
        String series;

        try (BooksCursor cursor = db.fetchBookById(bookId)) {
            cursor.moveToFirst();
            BookRowView bookRowView = cursor.getRowView();
            title = bookRowView.getTitle();
            rating = bookRowView.getRating();
            author = bookRowView.getPrimaryAuthorNameFormatted();
            series = bookRowView.getPrimarySeriesFormatted();
        }

        File image = StorageUtils.getCoverFile(db.getBookUuid(bookId));

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
     * Update the 'read' status of a book in the database + sets the 'read end' to today
     * The bookData will have its 'read' status updated ONLY if the update went through.
     *
     * @param db       database
     * @param bookData to update
     *
     * @return <tt>true</tt> if the update was successful, false on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final CatalogueDBAdapter db,
                                  @NonNull final BookData bookData,
                                  final boolean read) {
        int prevRead = bookData.getInt(UniqueId.KEY_BOOK_READ);
        String prevReadEnd = bookData.getString(UniqueId.KEY_BOOK_READ_END);

        bookData.putInt(UniqueId.KEY_BOOK_READ, read ? 1 : 0);
        bookData.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.todaySqlDateOnly());

        if (db.updateBook(bookData.getBookId(), bookData, 0) != 1) {
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
        return (db.updateBook(bookId, bookData, 0) == 1);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(final long bookId, final boolean read) {
        CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();
        boolean ok = setRead(db, bookId, read);
        db.close();
        return ok;
    }
}
