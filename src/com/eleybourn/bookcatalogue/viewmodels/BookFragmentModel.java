package com.eleybourn.bookcatalogue.viewmodels;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.eleybourn.bookcatalogue.BookFragment;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.database.DAO;

/**
 * In addition to the {@link BookBaseFragmentModel}, this model holds the flattened book list
 * for sweeping left/right.
 */
public class BookFragmentModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    @Nullable
    private FlattenedBooklist mFlattenedBooklist;

    @Override
    protected void onCleared() {
        if (mFlattenedBooklist != null) {
            mFlattenedBooklist.close();
            mFlattenedBooklist.deleteData();
        }

        if (mDb != null) {
            mDb.close();
        }
    }

    public void init(@Nullable final Bundle args,
                     final long bookId) {
        if (mDb == null) {
            mDb = new DAO();

            // no arguments ? -> no list!
            if (args == null) {
                return;
            }
            String list = args.getString(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST);
            if (list == null || list.isEmpty()) {
                return;
            }

            // looks like we have a list, but...
            mFlattenedBooklist = new FlattenedBooklist(mDb, list);
            // Check to see it really exists. The underlying table disappeared once in testing
            // which is hard to explain; it theoretically should only happen if the app closes
            // the database or if the activity pauses with 'isFinishing()' returning true.
            if (!mFlattenedBooklist.exists()) {
                mFlattenedBooklist.close();
                mFlattenedBooklist = null;
                return;
            }

            // ok, we absolutely have a list, get the position we need to be on.
            int pos = args.getInt(BookFragment.REQUEST_BKEY_FLAT_BOOKLIST_POSITION, 0);

            mFlattenedBooklist.moveTo(pos);
            // the book might have moved around. So see if we can find it.
            while (mFlattenedBooklist.getBookId() != bookId) {
                if (!mFlattenedBooklist.moveNext()) {
                    break;
                }
            }

            if (mFlattenedBooklist.getBookId() != bookId) {
                // book not found ? eh? give up...
                mFlattenedBooklist.close();
                mFlattenedBooklist = null;
                return;
            }
        }
    }

    @Nullable
    public FlattenedBooklist getFlattenedBooklist() {
        return mFlattenedBooklist;
    }
}
