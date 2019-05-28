/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.MenuPicker;
import com.eleybourn.bookcatalogue.dialogs.ValuePicker;
import com.eleybourn.bookcatalogue.dialogs.entities.EditBookshelfDialogFragment;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Admin Activity where we list all bookshelves and can add/delete/edit them.
 */
public class EditBookshelfListActivity
        extends BaseActivity {

    /** Database access. */
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
                    Intent data = new Intent().putExtra(DBDefinitions.KEY_ID, bookshelfId);
                    setResult(Activity.RESULT_OK, data);
                }
            };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_bookshelf;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_edit_bookshelves);
        mDb = new DAO();
        mList = mDb.getBookshelves();
        mAdapter = new BookshelfAdapter(this, mList);

        findViewById(R.id.fab).setOnClickListener(
                v -> editItem(new Bookshelf("", BooklistStyles.getDefaultStyle(mDb))));

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

    /**
     * Using {@link ValuePicker} for context menus.
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
                    UserMessage.showUserMessage(this, R.string.warning_cannot_delete_1st_bs);
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

        public Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
        }
    }

    /**
     * Adapter and row Holder for a {@link Bookshelf}.
     * <p>
     * Displays the name in a TextView.
     */
    public class BookshelfAdapter
            extends RecyclerView.Adapter<Holder> {

        private final AtomicInteger debugViewCounter = new AtomicInteger();

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final List<Bookshelf> mList;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param list    the list
         */
        BookshelfAdapter(@NonNull final Context context,
                         @NonNull final List<Bookshelf> list) {

            mInflater = LayoutInflater.from(context);
            mList = list;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            if (BuildConfig.DEBUG) {
                debugViewCounter.incrementAndGet();
                Logger.debug(this, "onCreateViewHolder",
                             "debugViewCounter=" + debugViewCounter.get(),
                             "viewType=" + viewType);
            }

            View view = mInflater.inflate(R.layout.row_bookshelf, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Bookshelf bookshelf = mList.get(position);

            holder.nameView.setText(bookshelf.getName());

            // click -> edit
            holder.nameView.setOnClickListener((view) -> editItem(bookshelf));

            // long-click -> menu
            holder.nameView.setOnLongClickListener((view) -> {
                Menu menu = MenuPicker.createMenu(holder.nameView.getContext());
                menu.add(Menu.NONE, R.id.MENU_EDIT, 0, R.string.menu_edit)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
                    .setIcon(R.drawable.ic_delete);
                // display
                String menuTitle = bookshelf.getName();
                final MenuPicker<Bookshelf> picker =
                        new MenuPicker<>(holder.nameView.getContext(), menuTitle, menu, bookshelf,
                                         EditBookshelfListActivity.this::onContextItemSelected);
                picker.show();
                return true;
            });

        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}
