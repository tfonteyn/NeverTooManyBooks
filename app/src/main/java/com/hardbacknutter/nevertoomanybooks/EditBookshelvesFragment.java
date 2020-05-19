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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;

/**
 * Lists all bookshelves and can add/delete/edit them.
 */
public class EditBookshelvesFragment
        extends Fragment {

    static final String TAG = "EditBookshelvesFragment";

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
    /** View Binding. */
    private FragmentEditBookshelvesBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(EditBookshelvesModel.class);
        mModel.init(getArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookshelvesBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mModel.getSelectedPosition().observe(getViewLifecycleOwner(), position ->
                mAdapter.setSelectedPosition(position));

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_edit_bookshelves);
        //noinspection ConstantConditions
        mAdapter = new BookshelfAdapter(getContext());

        // The FAB lives in the activity.
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_add);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editItem(mModel.createNewBookshelf(getContext())));

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mVb.bookshelfList.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mVb.bookshelfList.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mVb.bookshelfList.setHasFixedSize(true);
        mVb.bookshelfList.setAdapter(mAdapter);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);

        if (childFragment instanceof MenuPickerDialogFragment) {
            ((MenuPickerDialogFragment) childFragment).setListener(this::onContextItemSelected);

        } else if (childFragment instanceof EditBookshelfDialogFragment) {
            ((EditBookshelfDialogFragment) childFragment).setListener(mListener);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

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
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PURGE_BLNS: {
                final Bookshelf bookshelf = mModel.getSelectedBookshelf();
                if (bookshelf != null) {
                    //noinspection ConstantConditions
                    StandardDialogs.purgeBLNS(getContext(), R.string.lbl_bookshelf,
                                              bookshelf, () -> mModel.purgeBLNS());
                }
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        if (MenuPicker.__COMPILE_TIME_USE_FRAGMENT) {
            onCreateContextMenu2(position);
            return;
        }

        final Resources r = getResources();
        final Bookshelf bookshelf = mModel.getBookshelf(position);

        //noinspection ConstantConditions
        final Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT,
                 r.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.action_edit_ellipsis)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE,
                 r.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_delete);

        final String title = bookshelf.getName();
        new MenuPicker(getContext(), title, menu, position, this::onContextItemSelected)
                .show();
    }

    private void onCreateContextMenu2(final int position) {
        final Resources r = getResources();
        final Bookshelf bookshelf = mModel.getBookshelf(position);

        final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();
        menu.add(new MenuPickerDialogFragment.Pick(R.id.MENU_EDIT,
                                                   r.getInteger(R.integer.MENU_ORDER_EDIT),
                                                   getString(R.string.action_edit_ellipsis),
                                                   R.drawable.ic_edit));
        menu.add(new MenuPickerDialogFragment.Pick(R.id.MENU_DELETE,
                                                   r.getInteger(R.integer.MENU_ORDER_DELETE),
                                                   getString(R.string.action_delete),
                                                   R.drawable.ic_delete));

        final String title = bookshelf.getName();
        MenuPickerDialogFragment.newInstance(title, null, menu, position)
                                .show(getChildFragmentManager(), MenuPickerDialogFragment.TAG);
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        final Bookshelf bookshelf = mModel.getBookshelf(position);

        switch (menuItem) {
            case R.id.MENU_EDIT:
                editItem(bookshelf);
                return true;

            case R.id.MENU_DELETE:
                if (bookshelf.getId() > Bookshelf.DEFAULT) {
                    mModel.deleteBookshelf(bookshelf);
                    mAdapter.notifyDataSetChanged();

                } else {
                    //TODO: why not ? as long as we make sure there is another one left..
                    // e.g. count > 2, then you can delete '1'
                    Snackbar.make(mVb.bookshelfList, R.string.warning_cannot_delete_1st_bs,
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
     * Holder pattern for {@link BookshelfAdapter}.
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
            final View view = mInflater.inflate(R.layout.row_edit_bookshelf, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final Bookshelf bookshelf = mModel.getBookshelf(position);

            holder.nameView.setText(bookshelf.getName());

            // if there was nothing selected (yet), select the initial row if we have one.
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
                mSelectedPosition = holder.getBindingAdapterPosition();
                mModel.setSelectedBookshelf(mSelectedPosition);
                notifyItemChanged(mSelectedPosition);
            });

            holder.nameView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
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
