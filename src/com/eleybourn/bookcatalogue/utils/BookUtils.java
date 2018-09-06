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
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BookEdit;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.io.File;

/**
 * Class to implement common Book functions
 * 
 * @author pjw
 */
public class BookUtils {

	private static final String BKEY_BOOK_DATA = "bookData";

	private BookUtils() {
	}

	/**
	 * Open a new book editing activity with fields copied from saved book.
	 * Saved book (original of duplicating) is defined by its row _id in database.
	 *
	 * @param rowId The id of the book to copy fields
	 */
	public static void duplicateBook(Activity activity, @NonNull CatalogueDBAdapter dba, Long rowId){
        if (rowId == null || rowId == 0) {
            Toast.makeText(activity, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
        }
		Intent i = new Intent(activity, BookEdit.class);
		Bundle book = new Bundle();
		try(Cursor thisBook = dba.fetchBookById(rowId)) {
			thisBook.moveToFirst();
			book.putString(ColumnNames.KEY_TITLE, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_TITLE)));
			book.putString(ColumnNames.KEY_ISBN, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_ISBN)));
			book.putString(ColumnNames.KEY_PUBLISHER, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_PUBLISHER)));
			book.putString(ColumnNames.KEY_DATE_PUBLISHED, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_DATE_PUBLISHED)));
			book.putString(ColumnNames.KEY_RATING, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_RATING)));
			book.putString(ColumnNames.KEY_READ, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_READ)));
			book.putString(ColumnNames.KEY_PAGES, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_PAGES)));
			book.putString(ColumnNames.KEY_NOTES, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_NOTES)));
			book.putString(ColumnNames.KEY_LIST_PRICE, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_LIST_PRICE)));
			book.putString(ColumnNames.KEY_ANTHOLOGY_MASK, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_ANTHOLOGY_MASK)));
			book.putString(ColumnNames.KEY_LOCATION, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_LOCATION)));
			book.putString(ColumnNames.KEY_READ_START, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_READ_START)));
			book.putString(ColumnNames.KEY_READ_END, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_READ_END)));
			book.putString(ColumnNames.KEY_FORMAT, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_FORMAT)));
			book.putString(ColumnNames.KEY_SIGNED, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_SIGNED)));
			book.putString(ColumnNames.KEY_DESCRIPTION, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_DESCRIPTION)));
			book.putString(ColumnNames.KEY_GENRE, thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_GENRE)));
			
			book.putSerializable(ColumnNames.KEY_AUTHOR_ARRAY, dba.getBookAuthorList(rowId));
			book.putSerializable(ColumnNames.KEY_SERIES_ARRAY, dba.getBookSeriesList(rowId));
			
			i.putExtra(BKEY_BOOK_DATA, book);

			activity.startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY);
		} catch (CursorIndexOutOfBoundsException e) {
			Toast.makeText(activity, R.string.unknown_error, Toast.LENGTH_LONG).show();
			Logger.logError(e);
		}
	}
	
	/**
	 * Delete book by its database row _id and close current activity. 
	 * @param rowId The database id of the book for deleting
	 */
	public static void deleteBook(Context context, @NonNull final CatalogueDBAdapter dba, Long rowId, final Runnable runnable){
		if (rowId == null || rowId == 0) {
			Toast.makeText(context, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
			return;
		}
		int res = StandardDialogs.deleteBookAlert(context, dba, rowId, new Runnable() {
			@Override
			public void run() {
				dba.purgeAuthors();
				dba.purgeSeries();
				if (runnable != null)
					runnable.run();
			}});
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
	public static void shareBook(Context context, final CatalogueDBAdapter dbHelper, Long rowId){
		if (rowId == null || rowId == 0) {
			Toast.makeText(context, R.string.this_option_is_not_available_until_the_book_is_saved, Toast.LENGTH_LONG).show();
			return;
		}

		String title;
		double rating;
		String ratingString = "";
		String author;
		String series;

		try(Cursor thisBook = dbHelper.fetchBookById(rowId)) {
			thisBook.moveToFirst();
			title = thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_TITLE));
			rating = thisBook.getDouble(thisBook.getColumnIndex(ColumnNames.KEY_RATING));
			author = thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_AUTHOR_FORMATTED_GIVEN_FIRST));
			series = thisBook.getString(thisBook.getColumnIndex(ColumnNames.KEY_SERIES_FORMATTED));
		}

		File image = ImageUtils.fetchThumbnailByUuid(dbHelper.getBookUuid(rowId));

		if (!series.isEmpty()) {
			series = " (" + series.replace("#", "%23") + ")";
		}
		//remove trailing 0's
		if (rating > 0) {
			int ratingTmp = (int)rating;
			double decimal = rating - ratingTmp;
			if (decimal > 0) {
				ratingString = rating + "/5";
			} else {
				ratingString = ratingTmp + "/5";
			}
		}
		
		if (!ratingString.isEmpty()){
			ratingString = "(" + ratingString + ")";
		}

		/*
		 * There's a problem with the facebook app in android, so despite it being shown on the list
		 * it will not post any text unless the user types it.
		 */
		Intent share = new Intent(Intent.ACTION_SEND); 
		String text = context.getString(R.string.share_book_i_reading,title, author, series, ratingString);
		share.putExtra(Intent.EXTRA_TEXT, text); 
		share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + image.getPath()));
        share.setType("text/plain");
        
        context.startActivity(Intent.createChooser(share, context.getString(R.string.share)));
	}

    /**
     * Update the 'read' status of a book in the database
     * The 'book' will have its 'read' status updated ONLY if the update went through.
     *
	 * @param dba     database
     * @param book    book to update
     *
     * @return    true/false as result from database update
	 */
    public static boolean setRead(CatalogueDBAdapter dba, BookData book, boolean read) {
        int prev = book.getInt(ColumnNames.KEY_READ);
        book.putInt(ColumnNames.KEY_READ, read ? 1 : 0);
		if (!dba.updateBook(book.getRowId(), book, 0)) {
            book.putInt(ColumnNames.KEY_READ, prev);
            return false;
        }
        return true;
	}
	
	public static boolean setRead(CatalogueDBAdapter dba, long bookId, boolean read) {
        return dba.updateBook(bookId, new BookData((bookId)), 0);
    }
}
