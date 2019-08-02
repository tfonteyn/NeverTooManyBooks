package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel {

    /** Database access. */
    private DAO mDb;

    private boolean mAtLeastOneBookDeleted;

    @Nullable
    private Author mAuthor;
    @Nullable
    private ArrayList<TocEntry> mTocEntries;

    /** Initially we get toc entries and books. */
    private boolean mWithTocEntries = true;
    /** Initially we get toc entries and books. */
    private boolean mWithBooks = true;

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
        if (mAuthor == null || authorId != mAuthor.getId()) {
            mAuthor = mDb.getAuthor(authorId);
            if (mAuthor != null) {
                mWithTocEntries = args.getBoolean(AuthorWorksFragment.BKEY_WITH_TOCS,
                                                  mWithTocEntries);
                mWithBooks = args.getBoolean(AuthorWorksFragment.BKEY_WITH_BOOKS, mWithBooks);
                mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
            } else {
                throw new IllegalArgumentException("author was NULL for id=" + authorId);
            }
        }
    }

    @NonNull
    public Author getAuthor() {
        Objects.requireNonNull(mAuthor);
        return mAuthor;
    }

    public void loadTocEntries(final boolean withTocEntries,
                               final boolean withBooks) {
        mWithTocEntries = withTocEntries;
        mWithBooks = withBooks;
        //noinspection ConstantConditions
        mTocEntries = mDb.getTocEntryByAuthor(mAuthor, mWithTocEntries, mWithBooks);
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
            case Toc:
                if (mDb.deleteTocEntry(item.getId()) == 1) {
                    mTocEntries.remove(item);
                }
                break;

            case Book:
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
