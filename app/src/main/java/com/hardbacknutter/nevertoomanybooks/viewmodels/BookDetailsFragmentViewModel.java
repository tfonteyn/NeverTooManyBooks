/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNavigator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;

public class BookDetailsFragmentViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "BookDetailsFragmentViewModel";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_LIST_TABLE_NAME = TAG + ":LTName";
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";
    /** The fields used. */
    private final Fields mFieldsMap = new Fields();
    /** Database Access. */
    private DAO mDb;
    /** <strong>Optionally</strong> passed in via the arguments. */
    @Nullable
    private BooklistStyle mStyle;
    @Nullable
    private BooklistNavigator mNavHelper;

    @Override
    protected void onCleared() {
        if (mNavHelper != null) {
            mNavHelper.close();
        }
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args,
                     @NonNull final Book book) {
        if (mDb == null) {
            mDb = new DAO(TAG);

            if (args != null) {
                final String styleUuid = args.getString(BooklistStyle.BKEY_STYLE_UUID);
                if (styleUuid != null) {
                    mStyle = StyleDAO.getStyleOrDefault(context, mDb, styleUuid);
                }

                // the list/rowId is optional
                // If present, the user can swipe to the next/previous book in the list.
                final String listTableName = args.getString(BKEY_LIST_TABLE_NAME);
                if (listTableName != null && !listTableName.isEmpty()) {
                    // ok, we have a list, get the rowId we need to be on.
                    final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                    if (rowId > 0) {
                        mNavHelper = new BooklistNavigator(mDb.getSyncDb(), listTableName);
                        // move to book.
                        if (!mNavHelper.moveTo(rowId)
                            // Paranoia: is it the book we wanted ?
                            || mNavHelper.getBookId() != book.getId()) {
                            // Should never happen... flw
                            mNavHelper = null;
                        }
                    }
                }
            }
        }
    }

    @NonNull
    public Fields getFields() {
        return mFieldsMap;
    }

    /**
     * Called after the user swipes back/forwards through the flattened booklist.
     *
     * @param book      Current book
     * @param direction to move
     *
     * @return {@code true} if we moved
     */
    public boolean move(@NonNull final Book book,
                        @NonNull final BooklistNavigator.Direction direction) {

        if (mNavHelper != null && mNavHelper.move(direction)) {
            final long bookId = mNavHelper.getBookId();
            // reload if it's a different book
            if (bookId != book.getId()) {
                book.load(bookId, mDb);
                return true;
            }
        }
        return false;
    }

    @NonNull
    public List<Pair<Long, String>> getBookTitles(@NonNull final TocEntry tocEntry) {
        return tocEntry.getBookTitles(mDb);
    }

    @NonNull
    public DAO getDb() {
        return mDb;
    }

    @NonNull
    public List<Bookshelf> getAllBookshelves() {
        // not cached.
        // This allows the user to edit the global list of shelves while editing a book.
        return mDb.getBookshelves();
    }


    /**
     * Check if this cover should should be shown / is used.
     * <p>
     * The order we use to decide:
     * <ol>
     *     <li>Global visibility is set to HIDE -> return {@code false}</li>
     *     <li>The fragment has no access to the style -> return the global visibility</li>
     *     <li>The global style is set to HIDE -> {@code false}</li>
     *     <li>return the visibility as set in the style.</li>
     * </ol>
     *
     * @param context     current context
     * @param preferences Global preferences
     * @param cIdx        0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isCoverUsed(@NonNull final Context context,
                               @NonNull final SharedPreferences preferences,
                               @IntRange(from = 0, to = 1) final int cIdx) {

        // Globally disabled overrules style setting
        if (!DBDefinitions.isCoverUsed(preferences, cIdx)) {
            return false;
        }

        if (mStyle == null) {
            // there is no style and the global preference was true.
            return true;
        } else {
            // let the style decide
            return mStyle.getDetailScreenBookFields().isShowCover(context,
                                                                  preferences, cIdx);
        }
    }
}
