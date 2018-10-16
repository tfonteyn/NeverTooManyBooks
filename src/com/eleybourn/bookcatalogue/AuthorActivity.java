package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
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

    public static void startActivity(@NonNull final Activity activity,
                                     final long authorId) {
        Intent intent = new Intent(activity, AuthorActivity.class);
        intent.putExtra(UniqueId.KEY_ID, authorId);
        activity.startActivity(intent);
    }

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        @SuppressWarnings("ConstantConditions")
        long authorId = extras.getLong(UniqueId.KEY_ID);
        mDb = new CatalogueDBAdapter(this);

        Author author = mDb.getAuthor(authorId);
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
