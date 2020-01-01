/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.baseactivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Base class for editing a list of objects.
 * <p>
 * {@link #createListAdapter} needs to be implemented returning a suitable RecyclerView adapter.
 *
 * @param <T> the object type as used in the List
 */
public abstract class EditObjectListActivity<T extends Parcelable>
        extends BaseActivity {

    /** The key to use in the Bundle to get/put the array. */
    @NonNull
    private final String mBKey;

    /** the rows. */
    protected ArrayList<T> mList;

    /** Main screen name field. */
    protected AutoCompleteTextView mAutoCompleteTextView;
    /** AutoCompleteTextView adapter. */
    protected ArrayAdapter<String> mAutoCompleteAdapter;
    /** The adapter for the list. */
    protected RecyclerViewAdapterBase mListAdapter;
    protected EditObjectListModel mModel;

    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mModel.setDirty(true);
                }
            };
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * Constructor.
     *
     * @param bkey The key to use in the Bundle to get the list
     */
    protected EditObjectListActivity(@NonNull final String bkey) {
        mBKey = bkey;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(EditObjectListModel.class);
        mModel.init(this, Objects.requireNonNull(getIntent().getExtras()));

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState
                                                        : getIntent().getExtras();
        if (currentArgs != null) {
            mList = currentArgs.getParcelableArrayList(mBKey);
        }

        // The View for the list.
        RecyclerView listView = findViewById(android.R.id.list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = createListAdapter(mList, vh -> mItemTouchHelper.startDrag(vh));
        listView.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(listView);

        TextView titleView = findViewById(R.id.title);
        String bookTitle = mModel.getBookTitle();
        if (bookTitle == null || bookTitle.isEmpty()) {
            titleView.setVisibility(View.GONE);
        } else {
            titleView.setText(bookTitle);
        }

        findViewById(R.id.btn_add).setOnClickListener(this::onAdd);
    }

    @Override
    public void onBackPressed() {
        Intent resultData = mModel.getResultData()
                                  .putExtra(mBKey, mList);

        String name = mAutoCompleteTextView.getText().toString().trim();
        if (!name.isEmpty()) {
            // if the user had entered a (partial) new name, check if it's ok to leave
            StandardDialogs.unsavedEditsDialog(this, () -> {
                setResult(Activity.RESULT_OK, resultData);
                finish();
            });
            return;
        }

        // no current edit, so we're good to go.
        setResult(Activity.RESULT_OK, resultData);
        super.onBackPressed();
    }

    /**
     * Get the specific adapter from the child class.
     *
     * @param list              List of Series
     * @param dragStartListener Listener to handle the user moving rows up and down
     *
     * @return adapter
     */
    protected abstract RecyclerViewAdapterBase
    createListAdapter(@NonNull ArrayList<T> list,
                      @NonNull StartDragListener dragStartListener);

    /**
     * The user entered new data in the edit field and clicked 'add'.
     *
     * @param target The view that was clicked ('add' button).
     */
    protected abstract void onAdd(@NonNull View target);

    /**
     * Ensure that the list is saved.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(mBKey, mList);
    }
}
