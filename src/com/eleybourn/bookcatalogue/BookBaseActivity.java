package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;

public abstract class BookBaseActivity extends BaseActivity implements
        BookAbstractFragment.HasBook {

    protected CatalogueDBAdapter mDb;

    protected Book mBook;

    @NonNull
    @Override
    public Book getBook() {
        return mBook;
    }

    @Override
    public void reload(final long bookId) {
        mBook = loadBook(bookId,null);
        DataEditor dataEditorFragment = (DataEditor) getSupportFragmentManager().findFragmentById(R.id.fragment);
        dataEditorFragment.loadFrom(mBook);
    }

    /**
     * This function will populate the forms elements in three different ways
     *
     * 1. If fields have been passed from another activity (e.g. {@link BookSearchActivity}) it
     *    will populate the fields from the bundle
     *
     * 2. If a valid rowId exists it will populate the fields from the database
     *
     * 3. It will leave the fields blank for new books.
     *
     * So *always* returns a Book.
     */
    @NonNull
    protected Book loadBook(final long bookId, final @Nullable Bundle bundle) {
        Book book;
        if (bundle != null && bundle.containsKey(UniqueId.BKEY_BOOK_DATA)) {
            // If we have saved book data, use it
            book = new Book(bookId, bundle.getBundle(UniqueId.BKEY_BOOK_DATA));
        } else {
            // create new book and try to load the data from the database.
            book = new Book(bookId, null);
        }

        initActivityTitle(book);
        return book;
    }

    /**
     * Sets title of the activity depending on show/edit/new
     */
    private void initActivityTitle(final @NonNull Book book) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (book.getBookId() > 0) {
                // editing an existing book
                actionBar.setTitle(book.getString(UniqueId.KEY_TITLE));
                actionBar.setSubtitle(book.getAuthorTextShort());
            } else {
                // new book
                actionBar.setTitle(R.string.menu_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: forwarding to fragment - requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
        frag.onActivityResult(requestCode, resultCode, data);

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }
}
