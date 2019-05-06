package com.eleybourn.bookcatalogue.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel {

    private DBA mDb;

    private Author author;
    private ArrayList<TocEntry> mTocEntries;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
    }

    public void init(final long authorId,
                     @SuppressWarnings("SameParameterValue") final boolean withBooks) {
        if (author == null || authorId != author.getId()) {

            mDb = new DBA();
            author = mDb.getAuthor(authorId);
            if (author != null) {
                mTocEntries = mDb.getTocEntryByAuthor(author, withBooks);

                // for testing.
//                for (int i = 0; i < 300; i++) {
//                    mTocEntries.add(new TocEntry(author, "blah " + i, "1978"));
//                }
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
    public ArrayList<Integer> getBookIds(@NonNull final TocEntry item) {
        return mDb.getBookIdsByTocEntry(item.getId());
    }
}
