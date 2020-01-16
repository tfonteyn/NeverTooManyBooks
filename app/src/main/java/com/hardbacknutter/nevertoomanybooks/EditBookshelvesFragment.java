/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;

/**
 * Lists all bookshelves and can add/delete/edit them.
 */
public class EditBookshelvesFragment
        extends Fragment {

    static final String TAG = "EditBookshelvesFragment";

    private RecyclerView mListView;

    /** The adapter for the list. */
    private BookshelfAdapter mAdapter;
    private EditBookshelvesModel mModel;

    private final EditBookshelfDialogFragment.BookshelfChangedListener mListener =
            new EditBookshelfDialogFragment.BookshelfChangedListener() {
                @Override
                public void onBookshelfChanged(final long bookshelfId,
                                               final int booksMoved) {
                    mModel.reloadList(bookshelfId);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_bookshelves, container, false);
        mListView = view.findViewById(R.id.bookshelfList);
        return view;
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (EditBookshelfDialogFragment.TAG.equals(childFragment.getTag())) {
            ((EditBookshelfDialogFragment) childFragment).setListener(mListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(EditBookshelvesModel.class);
        mModel.init(getArguments());
        mModel.getSelectedPosition().observe(getViewLifecycleOwner(), position ->
                mAdapter.setSelectedPosition(position));

        getActivity().setTitle(R.string.title_edit_bookshelves);
        //noinspection ConstantConditions
        mAdapter = new BookshelfAdapter(getContext());

        FloatingActionButton fabButton = getActivity().findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_add);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> editItem(
                new Bookshelf("", BooklistStyle.getDefaultStyle(getContext(), mModel.getDb()))));

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_delete);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // only enable if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS)
            .setEnabled(mModel.getSelectedBookshelf() != null);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_PURGE_BLNS) {
            Bookshelf bookshelf = mModel.getSelectedBookshelf();
            if (bookshelf != null) {
                //noinspection ConstantConditions
                StandardDialogs
                        .purgeBLNSDialog(getContext(), R.string.lbl_bookshelf, bookshelf, () ->
                                mModel.getDb().purgeNodeStatesByBookshelf(bookshelf.getId()));
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onCreateContextMenu(final int position) {

        Bookshelf bookshelf = mModel.getBookshelf(position);

        Resources r = getResources();
        //noinspection ConstantConditions
        Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT,
                 r.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE,
                 r.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        String title = bookshelf.getName();
        new MenuPicker<>(getContext(), title, menu, position, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Integer position) {

        Bookshelf bookshelf = mModel.getBookshelf(position);

        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editItem(bookshelf);
                return true;

            case R.id.MENU_DELETE:
                if (bookshelf.getId() > Bookshelf.DEFAULT_ID) {
                    mModel.deleteBookshelf(bookshelf);
                    mAdapter.notifyDataSetChanged();

                } else {
                    //TODO: why not ? as long as we make sure there is another one left..
                    // e.g. count > 2, then you can delete '1'
                    Snackbar.make(mListView, R.string.warning_cannot_delete_1st_bs,
                                  Snackbar.LENGTH_LONG).show();
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
        EditBookshelfDialogFragment
                .newInstance(bookshelf)
                .show(getChildFragmentManager(), EditBookshelfDialogFragment.TAG);
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
    private class BookshelfAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;

        /**
         * Currently selected row.
         */
        private int mSelectedPosition = RecyclerView.NO_POSITION;

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
            View view = mInflater.inflate(R.layout.row_edit_bookshelf, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Bookshelf bookshelf = mModel.getBookshelf(position);

            holder.nameView.setText(bookshelf.getName());

            // select the original row if there was nothing selected (yet).
            if (mSelectedPosition == RecyclerView.NO_POSITION
                && bookshelf.getId() == mModel.getInitialBookshelfId()) {
                mSelectedPosition = position;
                mModel.setSelectedBookshelf(mSelectedPosition);
            }

            // update the current row
            holder.itemView.setSelected(mSelectedPosition == position);

            // click -> set the row as 'selected'.
            holder.nameView.setOnClickListener(v -> {
                // update the previous, now unselected, row.
                notifyItemChanged(mSelectedPosition);
                // get/update the newly selected row.
                mSelectedPosition = holder.getAdapterPosition();
                mModel.setSelectedBookshelf(mSelectedPosition);
                notifyItemChanged(mSelectedPosition);
            });

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
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mModel.getBookshelves().size();
        }

    }
}
