package com.eleybourn.bookcatalogue;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;

public class AuthorWorksModel
        extends ViewModel {

    private Author author;
    private ArrayList<TocEntry> mTocEntries;

    void init(@NonNull final DBA db,
              final long authorId,
              @SuppressWarnings("SameParameterValue") final boolean withBooks) {
        if (author == null || authorId != author.getId()) {
            author = db.getAuthor(authorId);
            if (author != null) {
                mTocEntries = db.getTocEntryByAuthor(author, withBooks);

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
}
