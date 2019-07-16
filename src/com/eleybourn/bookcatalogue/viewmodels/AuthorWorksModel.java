package com.eleybourn.bookcatalogue.viewmodels;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

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

    @Nullable
    private Author author;
    @Nullable
    private ArrayList<TocEntry> mTocEntries;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    /**
     * Pseudo constructor.
     * If we already have been initialized and the incoming bookId has not changed, return silently.
     *
     * @param args Bundle with arguments
     */
    public void init(@NonNull final Bundle args) {
        long authorId = args.getLong(DBDefinitions.KEY_PK_ID, 0);
        if (mDb == null) {
            mDb = new DAO();
        }
        if (author == null || authorId != author.getId()) {
            author = mDb.getAuthor(authorId);
            if (author != null) {
                boolean withBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, true);
                mTocEntries = mDb.getTocEntryByAuthor(author, withBooks);
            } else {
                throw new IllegalArgumentException("author was NULL for id=" + authorId);
            }
        }
    }

    @NonNull
    public Author getAuthor() {
        Objects.requireNonNull(author);
        return author;
    }

    @NonNull
    public ArrayList<TocEntry> getTocEntries() {
        Objects.requireNonNull(mTocEntries);
        return mTocEntries;
    }

    @NonNull
    public ArrayList<Long> getBookIds(@NonNull final TocEntry item) {
        return mDb.getBookIdsByTocEntry(item.getId());
    }

    public void delTocEntry(@NonNull final TocEntry item) {
        Objects.requireNonNull(mTocEntries);
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
