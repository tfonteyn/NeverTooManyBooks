package com.eleybourn.bookcatalogue;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistPseudoCursor;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.entities.Bookshelf;

public class BooksOnBookshelfModel
        extends ViewModel {

    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = "BooksOnBookshelf.TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = "BooksOnBookshelf.TopRowOffset";
    /**
     * Holder for all (semi)supported search criteria.
     * See {@link SearchCriteria} for more info.
     */
    private SearchCriteria mSearchCriteria;

    /**
     * Flag (potentially) set in {@link BooksOnBookshelf}#onActivityResult}.
     * Indicates if list rebuild is needed.
     */
    @Nullable
    private Boolean mAfterOnActivityResultDoFullRebuild;
    /** Flag to indicate that a list has been successfully loaded. Affects the way we save state. */
    private boolean mListHasBeenLoaded;
    /** Stores the book id for the current list position, e.g. while a book is viewed/edited. */
    private long mCurrentPositionedBookId;
    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTopRow = -1;

    /** Preferred booklist state in next rebuild. */
    private int mRebuildState;

    /** Current displayed list cursor. */
    private BooklistPseudoCursor mListCursor;

    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;

    /** Saved position of top row. */
    private int mTopRow;

    /**
     * Saved position of last top row offset from view top.
     * <p>
     * {@link ListView#setSelectionFromTop(int position, int y)} :
     * * @param y The distance from the top edge of the ListView (plus padding) that the
     * *        item will be positioned.
     */
    private int mTopRowOffset;

    /** Currently selected bookshelf. */
    private Bookshelf mCurrentBookshelf;

    /**
     * @param intent with the search-text.
     * @param args   Bundle savedInstance/Extras
     */
    void init(@NonNull final Intent intent,
              @Nullable final Bundle args,
              @NonNull final DBA db) {
        if (mSearchCriteria != null) {
            // already initialized.
            return;
        }
        mSearchCriteria = new SearchCriteria();

        if (args == null) {
            // Get preferred booklist state to use from preferences;
            // default to always expanded (MUCH faster than 'preserve' with lots of books)
            mRebuildState = BooklistBuilder.getListRebuildState();
        } else {
            // Always preserve state when rebuilding/recreating etc
            mRebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;

            mSearchCriteria.from(args);
        }

        // Restore bookshelf
        mCurrentBookshelf = Bookshelf.getPreferred(db);
        // Restore list position on bookshelf
        mTopRow = App.getPrefs().getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = App.getPrefs().getInt(PREF_BOB_TOP_ROW_OFFSET, 0);

        String searchText = "";
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Return the search results instead of all books (for the bookshelf)
            searchText = intent.getStringExtra(SearchManager.QUERY).trim();

        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Handle a suggestions click (because the suggestions all use ACTION_VIEW)
            searchText = intent.getDataString();
        }
        mSearchCriteria.setText(searchText);

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;
    }

    void savePosition(final int topRow,
                      final int topRowOffset) {
        mTopRow = topRow;
        mTopRowOffset = topRowOffset;

        App.getPrefs().edit()
           .putInt(BooksOnBookshelfModel.PREF_BOB_TOP_ROW, mTopRow)
           .putInt(BooksOnBookshelfModel.PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
           .apply();
    }

    @NonNull
    public SearchCriteria getSearchCriteria() {
        return mSearchCriteria;
    }

    public BooklistPseudoCursor getListCursor() {
        return mListCursor;
    }

    public void setListCursor(@NonNull final BooklistPseudoCursor listCursor) {
        mListCursor = listCursor;
    }

    public int getRebuildState() {
        return mRebuildState;
    }

    public void setRebuildState(final int rebuildState) {
        mRebuildState = rebuildState;
    }

    public int getTopRow() {
        return mTopRow;
    }

    public void setTopRow(final int topRow) {
        mTopRow = topRow;
    }

    public int getTopRowOffset() {
        return mTopRowOffset;
    }

    public void setTopRowOffset(final int topRowOffset) {
        mTopRowOffset = topRowOffset;
    }

    public Bookshelf getCurrentBookshelf() {
        return mCurrentBookshelf;
    }

    public void setCurrentBookshelf(final Bookshelf currentBookshelf) {
        mCurrentBookshelf = currentBookshelf;
    }

    @Nullable
    public Boolean getAfterOnActivityResultDoFullRebuild() {
        return mAfterOnActivityResultDoFullRebuild;
    }

    public void setAfterOnActivityResultDoFullRebuild(@Nullable final Boolean rebuild) {
        mAfterOnActivityResultDoFullRebuild = rebuild;
    }

    public boolean hasListBeenLoaded() {
        return mListHasBeenLoaded;
    }

    public void setListHasBeenLoaded(final boolean listHasBeenLoaded) {
        mListHasBeenLoaded = listHasBeenLoaded;
    }

    public long getCurrentPositionedBookId() {
        return mCurrentPositionedBookId;
    }

    public void setCurrentPositionedBookId(final long currentPositionedBookId) {
        mCurrentPositionedBookId = currentPositionedBookId;
    }

    public int getLastTopRow() {
        return mLastTopRow;
    }

    public void setLastTopRow(final int lastTopRow) {
        mLastTopRow = lastTopRow;
    }

    public int getTotalBooks() {
        return mTotalBooks;
    }

    public void setTotalBooks(final int totalBooks) {
        mTotalBooks = totalBooks;
    }

    public int getUniqueBooks() {
        return mUniqueBooks;
    }

    public void setUniqueBooks(final int uniqueBooks) {
        mUniqueBooks = uniqueBooks;
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     * <p>
     * All individual criteria are supported by the {@link BooklistBuilder},
     * but not necessarily in {@link BooksOnBookshelf}.
     * <p>
     * Only some are supported by
     * {@link com.eleybourn.bookcatalogue.entities.Book}#onSearchRequested()}.
     */
    static class SearchCriteria {

        /**
         * Author to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String author = "";
        /**
         * Title to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String title = "";
        /**
         * Series to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String series = "";
        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not in this class yet.
         */
        @Nullable
        String loanee = "";
        /**
         * List of bookId's to display. The result of a search.
         */
        @Nullable
        ArrayList<Integer> bookList;
        /**
         * Text to use in search query.
         * <p>
         * Always use the setter!
         */
        @NonNull
        private String mText = "";

        void clear() {
            mText = "";
            author = "";
            title = "";
            series = "";
            loanee = "";
            bookList = null;
        }

        @NonNull
        public String getText() {
            return mText;
        }

        public void setText(@Nullable final String text) {
            if (text == null || text.isEmpty() || ".".equals(text)) {
                mText = "";
            } else {
                mText = text;
            }
        }

        void from(@NonNull final Bundle bundle) {
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setText(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                author = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(DBDefinitions.KEY_TITLE)) {
                title = bundle.getString(DBDefinitions.KEY_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_SERIES)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                bookList = bundle.getIntegerArrayList(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * @param intent which will be used for a
         *               {@link BooksOnBookshelfModel} #startActivityForResult}
         */
        void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, mText)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, author)
                  .putExtra(DBDefinitions.KEY_TITLE, title)
                  .putExtra(DBDefinitions.KEY_SERIES, series)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)
                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        /**
         * @param outState from {@link BooksOnBookshelfModel}#onSaveInstanceState}
         */
        void to(final Bundle outState) {
            outState.putString(UniqueId.BKEY_SEARCH_TEXT, mText);
            outState.putString(UniqueId.BKEY_SEARCH_AUTHOR, author);
            outState.putString(DBDefinitions.KEY_TITLE, title);
            outState.putString(DBDefinitions.KEY_SERIES, series);
            outState.putString(DBDefinitions.KEY_LOANEE, loanee);
            outState.putIntegerArrayList(UniqueId.BKEY_ID_LIST, bookList);
        }

        boolean isEmpty() {
            return mText.isEmpty()
                    && (author == null || author.isEmpty())
                    && (title == null || title.isEmpty())
                    && (series == null || series.isEmpty())
                    && (loanee == null || loanee.isEmpty())
                    && (bookList == null || bookList.isEmpty());
        }
    }
}
