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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.FileProvider;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;

import java.io.File;
import java.util.Objects;

/**
 * Class to implement common Book functions
 *
 * @author pjw
 */
public class BookUtils {

    private BookUtils() {
    }

    /**
     * Duplicate a book by putting applicable fields in a Bundle ready for further processing.
     *
     * @param bookId from which to copy fields
     *
     * @return bundle with book data
     */
    public static Bundle duplicateBook(@NonNull final CatalogueDBAdapter db,
                                       final long bookId) {
        final Bundle bookData = new Bundle();
        try (BooksCursor cursor = db.fetchBookById(bookId)) {
            if (cursor.moveToFirst()) {
                BookRowView bookRowView = cursor.getRowView();

                bookData.putString(UniqueId.KEY_TITLE, bookRowView.getTitle());
                bookData.putString(UniqueId.KEY_BOOK_ISBN, bookRowView.getIsbn());
                bookData.putString(UniqueId.KEY_DESCRIPTION, bookRowView.getDescription());

                bookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, db.getBookAuthorList(bookId));
                bookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, db.getBookSeriesList(bookId));

                bookData.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, bookRowView.getAnthologyBitMask());
                bookData.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, db.getAnthologyTitleListByBook(bookId));

                bookData.putString(UniqueId.KEY_BOOK_PUBLISHER, bookRowView.getPublisherName());
                bookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, bookRowView.getDatePublished());
                bookData.putString(UniqueId.KEY_BOOK_FORMAT, bookRowView.getFormat());
                bookData.putString(UniqueId.KEY_BOOK_GENRE, bookRowView.getGenre());
                bookData.putString(UniqueId.KEY_BOOK_LIST_PRICE, bookRowView.getListPrice());
                bookData.putString(UniqueId.KEY_BOOK_LANGUAGE, bookRowView.getLanguage());
                bookData.putString(UniqueId.KEY_BOOK_PAGES, bookRowView.getPages());
                bookData.putDouble(UniqueId.KEY_BOOK_RATING, bookRowView.getRating());
                bookData.putString(UniqueId.KEY_BOOK_LOCATION, bookRowView.getLocation());
                bookData.putString(UniqueId.KEY_BOOK_READ_END, bookRowView.getReadEnd());
                bookData.putString(UniqueId.KEY_BOOK_READ_START, bookRowView.getReadStart());
                bookData.putInt(UniqueId.KEY_BOOK_READ, bookRowView.getRead());
                bookData.putInt(UniqueId.KEY_BOOK_SIGNED, bookRowView.getSigned());
                bookData.putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bookRowView.getEditionBitMask());
                bookData.putString(UniqueId.KEY_FIRST_PUBLICATION, bookRowView.getFirstPublication());
                bookData.putString(UniqueId.KEY_NOTES, bookRowView.getNotes());
            }

        } catch (DBExceptions.ColumnNotPresent e) {
            // fetchBookById had an incomplete selectClause, the log will tell us which column
            Logger.error(e);
            return null;
        }

        return bookData;
    }

    /**
     * Delete book by its database row _id and close current activity.
     *
     * TOMF: how about using {@link EditBookActivity.PostConfirmOrCancelAction}
     *
     * @param bookId  the book to delete
     */
    public static void deleteBook(@NonNull final Activity activity,
                                  @NonNull final CatalogueDBAdapter db,
                                  final long bookId,
                                  @Nullable final Runnable onDeleted) {
        @StringRes
        int errorMsgId = StandardDialogs.deleteBookAlert(activity, db, bookId, new Runnable() {
            @Override
            public void run() {
                db.purgeAuthors();
                db.purgeSeries();
                if (onDeleted != null) {
                    onDeleted.run();
                }
            }
        });
        if (errorMsgId != 0) {
            StandardDialogs.showUserMessage(activity, errorMsgId);
        }
    }

    /**
     * Perform sharing of book. Create chooser with matched apps for sharing some text like:
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     *
     * @param bookId to share
     */
    public static void shareBook(@NonNull final Activity activity,
                                 @NonNull final CatalogueDBAdapter db,
                                 final long bookId) {
        String title;
        double rating;
        String author;
        String series;

        try (BooksCursor cursor = db.fetchBookById(bookId)) {
            if (cursor.moveToFirst()) {
                BookRowView bookRowView = cursor.getRowView();
                title = bookRowView.getTitle();
                rating = bookRowView.getRating();
                author = bookRowView.getPrimaryAuthorNameFormattedGivenFirst();
                series = bookRowView.getPrimarySeriesFormatted();
            } else {
                StandardDialogs.showUserMessage(activity, R.string.unable_to_find_book);
                return;
            }
        }

        File image = StorageUtils.getCoverFile(db.getBookUuid(bookId));

        if (!series.isEmpty()) {
            series = " (" + series.replace("#", "%23") + ")";
        }

        //remove trailing 0's
        String ratingString = "";
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
        Uri coverURI = FileProvider.getUriForFile(activity, GenericFileProvider.AUTHORITY, image);

        /*
        FIXME: There's a problem with the facebook app in android,
         so despite it being shown on the list it will not post any text unless the user types it.
		 */
        Intent share = new Intent(Intent.ACTION_SEND);
        String text = activity.getString(R.string.share_book_im_reading, title, author, series, ratingString);
        share.putExtra(Intent.EXTRA_TEXT, text);
        share.putExtra(Intent.EXTRA_STREAM, coverURI);
        share.setType("text/plain");

        activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share)));
    }

    /**
     * Update the 'read' status of a book in the database + sets the 'read end' to today
     * The book will have its 'read' status updated ONLY if the update went through.
     *
     * @param db       database
     * @param book to update
     *
     * @return <tt>true</tt> if the update was successful, false on onCancel
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final CatalogueDBAdapter db,
                                  @NonNull final Book book,
                                  final boolean read) {
        int prevRead = book.getInt(UniqueId.KEY_BOOK_READ);
        String prevReadEnd = book.getString(UniqueId.KEY_BOOK_READ_END);

        book.putInt(UniqueId.KEY_BOOK_READ, read ? 1 : 0);
        book.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.todaySqlDateOnly());

        if (db.updateBook(book.getBookId(), book, 0) != 1) {
            book.putInt(UniqueId.KEY_BOOK_READ, prevRead);
            book.putString(UniqueId.KEY_BOOK_READ_END, prevReadEnd);
            return false;
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final CatalogueDBAdapter db,
                                  final long bookId,
                                  final boolean read) {
        Book book = db.getBookById(bookId);
        Objects.requireNonNull(book);
        book.putBoolean(UniqueId.KEY_BOOK_READ, read);
        book.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.todaySqlDateOnly());
        return (db.updateBook(bookId, book, 0) == 1);
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
