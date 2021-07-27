/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;

/**
 * Lists all bookshelves and can add/delete/edit them.
 */
public class EditBookshelvesFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "EditBookshelvesFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;
    /** The adapter for the list. */
    private BookshelfAdapter mAdapter;
    private EditBookshelvesViewModel mVm;

    /** Accept the result from the dialog. */
    private final EditBookshelfDialogFragment.Launcher mOnEditBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher(RK_EDIT_BOOKSHELF) {
                @Override
                public void onResult(final long bookshelfId) {
                    mVm.reloadListAndSetSelectedPosition(bookshelfId);
                }
            };

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    EditBookshelvesContract
                            .setResultAndFinish(getActivity(), mVm.getSelectedBookshelfId());
                }
            };

    /** View Binding. */
    private FragmentEditBookshelvesBinding mVb;
    private ExtPopupMenu mContextMenu;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mOnEditBookshelfLauncher.registerForFragmentResult(getChildFragmentManager(), this);
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
        setTitle(R.string.lbl_bookshelves);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(this).get(EditBookshelvesViewModel.class);
        mVm.init(getArguments());
        mVm.onSelectedPositionChanged().observe(getViewLifecycleOwner(),
                                                aVoid -> mAdapter.notifyDataSetChanged());

        // The FAB lives in the activity.
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_baseline_add_24);
        fab.setVisibility(View.VISIBLE);
        //noinspection ConstantConditions
        fab.setOnClickListener(v -> editItem(mVm.createNewBookshelf(getContext())));

        //noinspection ConstantConditions
        mAdapter = new BookshelfAdapter(getContext());
        mVb.list.addItemDecoration(new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.list.setHasFixedSize(true);
        mVb.list.setAdapter(mAdapter);

        final Resources res = getResources();
        final Menu menu = ExtPopupMenu.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.action_edit_ellipsis)
            .setIcon(R.drawable.ic_baseline_edit_24);
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);
        mContextMenu = new ExtPopupMenu(getContext(), menu, this::onContextItemSelected);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PURGE_BLNS, 0, R.string.lbl_purge_blns)
            .setIcon(R.drawable.ic_baseline_delete_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        // only enable if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS)
            .setEnabled(mVm.getSelectedPosition() != RecyclerView.NO_POSITION);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_PURGE_BLNS) {
            final int position = mVm.getSelectedPosition();
            if (position != RecyclerView.NO_POSITION) {
                //noinspection ConstantConditions
                StandardDialogs.purgeBLNS(getContext(), R.string.lbl_bookshelf,
                                          mVm.getBookshelf(position).getLabel(getContext()),
                                          () -> mVm.purgeBLNS());
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          final int position) {
        final int itemId = menuItem.getItemId();

        final Bookshelf bookshelf = mVm.getBookshelf(position);

        if (itemId == R.id.MENU_EDIT) {
            editItem(bookshelf);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            if (bookshelf.getId() > Bookshelf.DEFAULT) {
                mVm.deleteBookshelf(position);
                mAdapter.notifyItemRemoved(position);

            } else {
                //TODO: why not ? as long as we make sure there is another one left..
                // e.g. count > 2, then you can delete '1'
                Snackbar.make(mVb.list, R.string.warning_cannot_delete_1st_bs,
                              Snackbar.LENGTH_LONG).show();
            }
            return true;
        }

        return false;
    }

    /**
     * Start the fragment dialog to edit a Bookshelf.
     *
     * @param bookshelf to edit
     */
    private void editItem(@NonNull final Bookshelf bookshelf) {
        mOnEditBookshelfLauncher.launch(bookshelf);
    }

    /**
     * Row ViewHolder for {@link BookshelfAdapter}.
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

        /** Cached inflater. */
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
            final View view = mInflater.inflate(R.layout.row_edit_bookshelf, parent, false);
            final Holder holder = new Holder(view);

            // click -> set the row as 'selected'.
            holder.nameView.setOnClickListener(
                    v -> mVm.setSelectedPosition(holder.getBindingAdapterPosition()));

            holder.nameView.setOnLongClickListener(v -> {
                mContextMenu.showAsDropDown(v, holder.getBindingAdapterPosition());
                return true;
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final Bookshelf bookshelf = mVm.getBookshelf(position);

            holder.nameView.setText(bookshelf.getName());
            holder.itemView.setSelected(position == mVm.getSelectedPosition());
        }

        @Override
        public int getItemCount() {
            return mVm.getBookshelves().size();
        }
    }
}
