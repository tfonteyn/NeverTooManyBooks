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

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 */
public class EditBookshelfListActivity
        extends BaseActivity {

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
                    mAdapter.notifyDataSetChanged();
                    Intent data = new Intent().putExtra(DBDefinitions.KEY_PK_ID, bookshelfId);
                    setResult(Activity.RESULT_OK, data);
                }
            };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_bookshelves);
        mDb = new DAO();
        mList = mDb.getBookshelves();
        mAdapter = new BookshelfAdapter(this);

        findViewById(R.id.fab).setOnClickListener(
                v -> editItem(new Bookshelf("",
                                            BooklistStyle.getDefaultStyle(mDb))));

        RecyclerView listView = findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(linearLayoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        listView.setHasFixedSize(true);
        listView.setAdapter(mAdapter);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (EditBookshelfDialogFragment.TAG.equals(fragment.getTag())) {
            ((EditBookshelfDialogFragment) fragment).setListener(mListener);
        }
    }

    private void onCreateContextMenu(final int position) {

        Bookshelf bookshelf = mList.get(position);

        Menu menu = MenuPicker.createMenu(this);
        menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        String title = bookshelf.getName();
        new MenuPicker<>(this, title, null, false, menu, bookshelf, this::onContextItemSelected)
                .show();
    }


    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem  that was selected
     * @param bookshelf to act on
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
                if (!bookshelf.isDefault()) {
                    mDb.deleteBookshelf(bookshelf.getId());
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

        /**
         * Constructor.
         *
         * @param context Current context
         */
        BookshelfAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            View view = mInflater.inflate(R.layout.row_bookshelf, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Bookshelf bookshelf = mList.get(position);

            holder.nameView.setText(bookshelf.getName());

            // click -> edit
            holder.nameView.setOnClickListener(v -> editItem(bookshelf));

            // long-click -> menu
            holder.nameView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });

        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}
