package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.widget.ArrayAdapter;

import com.eleybourn.bookcatalogue.baseactivity.BaseListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;

import java.util.ArrayList;

/**
 * ENHANCE: add book list to each title, and make the clickable to goto the book
 */
public class AuthorActivity extends BaseListActivity {

    private CatalogueDBAdapter mDb;
    private ArrayList<AnthologyTitle> mList;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_author;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        @SuppressWarnings("ConstantConditions")
        long authorId = extras.getLong(UniqueId.KEY_ID);
        mDb = new CatalogueDBAdapter(this);

        Author author = mDb.getAuthor(authorId);
        //noinspection ConstantConditions
        setTitle(author.getDisplayName());

        mList = mDb.getAnthologyTitleListByAuthor(author);

        ArrayAdapter<AnthologyTitle> adapter = new AnthologyTitleListAdapter(this, R.layout.row_anthology, mList);
        setListAdapter(adapter);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        mDb.close();
    }
}
