package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.TOCAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorActivity
        extends BaseListActivity {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_author;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long authorId = getIntent().getLongExtra(DatabaseDefinitions.KEY_ID, 0);

        mDb = new DBA(this);
        final Author author = mDb.getAuthor(authorId);
        //noinspection ConstantConditions
        setTitle(author.getDisplayName());

        // the list of TOC entries.
        final ArrayList<TocEntry> list = mDb.getTocEntryByAuthor(author);

        final ArrayAdapter<TocEntry> adapter = new TOCAdapter(this, R.layout.row_toc_entry, list);
        setListAdapter(adapter);
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param parent   The AdapterView where the click happened.
     * @param view     The view within the AdapterView that was clicked (this
     *                 will be a view provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id       The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(@NonNull final AdapterView<?> parent,
                            @NonNull final View view,
                            final int position,
                            final long id) {
        final TocEntry entry = (TocEntry) parent.getItemAtPosition(position);
        // see note on dba method about Integer vs. Long
        final ArrayList<Integer> books = mDb.getBookIdsByTocEntry(entry.getId());
        Intent intent = new Intent(this, BooksOnBookshelf.class)
                // clear the back-stack. We want to keep BooksOnBookshelf on top
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // bring up list, filtered on the book id's
                .putExtra(UniqueId.BKEY_ID_LIST, books);
        startActivity(intent);
        finish();
    }
}
