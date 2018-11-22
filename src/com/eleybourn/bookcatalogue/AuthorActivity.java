package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.widget.ArrayAdapter;

import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TOCEntry;

import java.util.ArrayList;
import java.util.Objects;

/**
 * ENHANCE: add book list to each title, and make the clickable to goto the book
 */
public class AuthorActivity extends BaseListActivity {

    private CatalogueDBAdapter mDb;
    private ArrayList<TOCEntry> mList;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_author;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        Objects.requireNonNull(extras);
        long authorId = extras.getLong(UniqueId.KEY_ID);
        mDb = new CatalogueDBAdapter(this);

        Author author = mDb.getAuthor(authorId);
        Objects.requireNonNull(author);
        setTitle(author.getDisplayName());

        mList = mDb.getTOCEntriesByAuthor(author);

        ArrayAdapter<TOCEntry> adapter = new TOCListAdapter(this, R.layout.row_anthology, mList);
        setListAdapter(adapter);
        Tracker.exitOnCreate(this);
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
