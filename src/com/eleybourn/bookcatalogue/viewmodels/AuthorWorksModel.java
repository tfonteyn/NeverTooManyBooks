package com.eleybourn.bookcatalogue.viewmodels;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.AuthorWorksFragment;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    private boolean mAtLeastOneBookDeleted;

    private Author author;
    private ArrayList<TocEntry> mTocEntries;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    public void init(@NonNull final Bundle args) {
        long authorId = args.getLong(DBDefinitions.KEY_ID, 0);
        boolean withBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, true);

        if (mDb == null || authorId != author.getId()) {

            mDb = new DAO();
            author = mDb.getAuthor(authorId);
            if (author != null) {
                mTocEntries = mDb.getTocEntryByAuthor(author, withBooks);
            } else {
                throw new IllegalArgumentException("author was NULL for id=" + authorId);
            }
        }
    }

    @NonNull
    public Author getAuthor() {
        return author;
    }

    @NonNull
    public ArrayList<TocEntry> getTocEntries() {
        return mTocEntries;
    }

    @NonNull
    public ArrayList<Long> getBookIds(@NonNull final TocEntry item) {
        return mDb.getBookIdsByTocEntry(item.getId());
    }

    public void delTocEntry(@NonNull final TocEntry item) {
        switch (item.getType()) {
            case TocEntry.TYPE_TOC:
                if (mDb.deleteTocEntry(item.getId()) == 1) {
                    mTocEntries.remove(item);
                }
                break;

            case TocEntry.TYPE_BOOK:
                if (mDb.deleteBook(item.getId()) == 1) {
                    mTocEntries.remove(item);
                    mAtLeastOneBookDeleted = true;
                }
                break;

            default:
                throw new IllegalArgumentException("type=" + item.getType());
        }
    }

    public boolean isAtLeastOneBookDeleted() {
        return mAtLeastOneBookDeleted;
    }
}
