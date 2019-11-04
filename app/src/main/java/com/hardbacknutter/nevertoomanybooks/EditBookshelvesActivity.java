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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 */
public class EditBookshelvesActivity
        extends BaseActivity {

    private static final String TAG = "EditBookshelvesActivity";

    public static final String BKEY_CURRENT_BOOKSHELF = TAG + ":current";

    /** Database Access. */
    private DAO mDb;

    /** The list we're editing. */
    private ArrayList<Bookshelf> mList;
    /** The adapter for the list. */
    private BookshelfAdapter mAdapter;
    private final EditBookshelfDialogFragment.BookshelfChangedListener mListener =
            new EditBookshelfDialogFragment.BookshelfChangedListener() {
                @Override
                public void onBookshelfChanged(final long bookshelfId,
                                               final int booksMoved) {
                    mList.clear();
                    mList.addAll(mDb.getBookshelves());

                    for (int i = 0; i < mList.size(); i++) {
                        Bookshelf bookshelf = mList.get(i);
                        if (bookshelf.getId() == bookshelfId) {
                            mAdapter.setSelectedPosition(i);
                            break;
                        }
                    }

                    mAdapter.notifyDataSetChanged();
                }
            };
    private long mInitialBookshelfId;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (EditBookshelfDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditBookshelfDialogFragment) fragment).setListener(mListener);
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mInitialBookshelfId = args.getLong(BKEY_CURRENT_BOOKSHELF);
            if (mInitialBookshelfId == 0) {
                throw new UnexpectedValueException(mInitialBookshelfId);
            }
        }

        setTitle(R.string.title_edit_bookshelves);
        mDb = new DAO();
        mList = mDb.getBookshelves();
        mAdapter = new BookshelfAdapter(this, mInitialBookshelfId);

        FloatingActionButton fabButton = findViewById(R.id.fab);
        fabButton.setOnClickListener(v -> editItem(
                new Bookshelf("", BooklistStyle.getDefaultStyle(mDb))));

        RecyclerView listView = findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(linearLayoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        listView.setHasFixedSize(true);
        listView.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {

        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_delete);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        // only enable if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS)
            .setEnabled(mAdapter.getSelected() != null);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_PURGE_BLNS) {
            Bookshelf selected = mAdapter.getSelected();
            if (selected != null) {
                StandardDialogs.purgeBLNSDialog(this, R.string.lbl_bookshelf, selected, () ->
                        mDb.purgeNodeStatesByBookshelf(selected.getId()));
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void onCreateContextMenu(final int position) {
        Menu menu = MenuPicker.createMenu(this);
        menu.add(Menu.NONE, R.id.MENU_EDIT, MenuHandler.ORDER_EDIT, R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE, MenuHandler.ORDER_DELETE, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        Bookshelf bookshelf = mList.get(position);
        String title = bookshelf.getName();
        new MenuPicker<>(this, title, menu, bookshelf, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem  that was selected
     * @param bookshelf in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Bookshelf bookshelf) {

        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editItem(bookshelf);
                return true;

            case R.id.MENU_DELETE:
                if (bookshelf.getId() > Bookshelf.DEFAULT_ID) {
                    long bookshelfId = bookshelf.getId();
                    mDb.deleteBookshelf(bookshelfId);
                    mList.remove(bookshelf);
                    mAdapter.notifyDataSetChanged();

                } else {
                    //TODO: why not ? as long as we make sure there is another one left..
                    // e.g. count > 2, then you can delete '1'
                    UserMessage.show(this, R.string.warning_cannot_delete_1st_bs);
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * Start the fragment dialog to edit a Bookshelf.
     *
     * @param bookshelf to edit
     */
    private void editItem(@NonNull final Bookshelf bookshelf) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(EditBookshelfDialogFragment.TAG) == null) {
            EditBookshelfDialogFragment.newInstance(bookshelf)
                                       .show(fm, EditBookshelfDialogFragment.TAG);
        }
    }

    @Override
    public void onBackPressed() {
        Intent data = new Intent();

        Bookshelf selectedBookshelf = mAdapter.getSelected();
        if (selectedBookshelf != null) {
            data.putExtra(DBDefinitions.KEY_PK_ID, selectedBookshelf.getId());
        }

        setResult(Activity.RESULT_OK, data);
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(BKEY_CURRENT_BOOKSHELF, mInitialBookshelfId);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Holder pattern for each row.
     */
    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView nameView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
        }
    }

    /**
     * Adapter and row Holder for a {@link Bookshelf}.
     * <p>
     * Displays the name in a TextView.
     */
    class BookshelfAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;

        /** The id of the item which should be / is selected at creation time. */
        private final long mInitialSelectedItemId;

        /** Currently selected row. */
        private int mSelectedPosition = RecyclerView.NO_POSITION;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        BookshelfAdapter(@NonNull final Context context,
                         final long initialSelectedItemId) {
            mInflater = LayoutInflater.from(context);
            mInitialSelectedItemId = initialSelectedItemId;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = mInflater.inflate(R.layout.row_edit_bookshelf, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Bookshelf bookshelf = mList.get(position);

            holder.nameView.setText(bookshelf.getName());

            // select the original row if there was nothing selected (yet).
            if (mSelectedPosition == RecyclerView.NO_POSITION
                && bookshelf.getId() == mInitialSelectedItemId) {
                mSelectedPosition = position;
            }

            // update the current row
            holder.itemView.setSelected(mSelectedPosition == position);

            // click -> set the row as 'selected'.
            holder.nameView.setOnClickListener(v -> {
                // update the previous, now unselected, row.
                notifyItemChanged(mSelectedPosition);
                // get/update the newly selected row.
                mSelectedPosition = holder.getAdapterPosition();
                notifyItemChanged(mSelectedPosition);
            });

            // long-click -> menu
            holder.nameView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });

        }

        /**
         * Update the selection.
         *
         * @param position the newly selected row
         */
        void setSelectedPosition(final int position) {
            mSelectedPosition = position;
        }

        /**
         * Get the currently selected item.
         *
         * @return bookshelf, or {@code null} if none selected (which should never happen... flw)
         */
        @Nullable
        Bookshelf getSelected() {
            return mSelectedPosition != RecyclerView.NO_POSITION ? mList.get(mSelectedPosition)
                                                                 : null;
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}
