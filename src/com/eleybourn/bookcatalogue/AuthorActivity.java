package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.adapters.TOCAdapter;
import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TOCEntry;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Display all TOCEntries for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorActivity
        extends BaseListActivity {

    /** The database. */
    private DBA mDb;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_author;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        Objects.requireNonNull(extras);
        final long authorId = extras.getLong(UniqueId.KEY_ID);
        mDb = new DBA(this);

        final Author author = mDb.getAuthor(authorId);
        Objects.requireNonNull(author);
        setTitle(author.getDisplayName());

        // the list of TOC entries.
        final ArrayList<TOCEntry> list = mDb.getTOCEntriesByAuthor(author);

        final ArrayAdapter<TOCEntry> adapter = new TOCAdapter(this, R.layout.row_anthology, list);
        setListAdapter(adapter);
    }

    /**
     * User tapped ona an entry; get the book(s) for that entry and display.
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
        final TOCEntry entry = (TOCEntry) parent.getItemAtPosition(position);
        // see note on dba method about Integer vs. Long
        final ArrayList<Integer> books = mDb.getBookIdsByTOCEntry(entry.getId());
        Intent intent = new Intent(this, BooksOnBookshelf.class);
        // clear the back-stack. We want to keep BooksOnBookshelf on top
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // bring up list, filtered on the book id's
        intent.putExtra(UniqueId.BKEY_BOOK_ID_LIST, books);
        startActivity(intent);
        finish();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
