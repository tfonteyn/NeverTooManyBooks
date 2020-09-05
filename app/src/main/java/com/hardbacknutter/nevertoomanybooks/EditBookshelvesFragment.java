/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

/**
 * Lists all bookshelves and can add/delete/edit them.
 */
public class EditBookshelvesFragment
        extends Fragment {

    /** Log tag. */
    static final String TAG = "EditBookshelvesFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELF = EditBookshelfDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_MENU_PICKER = MenuPickerDialogFragment.TAG + ":rk";
    /** The adapter for the list. */
    private BookshelfAdapter mAdapter;
    private EditBookshelvesModel mModel;
    private final EditBookshelfDialogFragment.OnResultListener mOnEditBookshelfListener =
            bookshelfId -> mModel.reloadListAndSetSelectedPosition(bookshelfId);
    /** ViewModel. */
    private ResultDataModel mResultData;
    /** View Binding. */
    private FragmentEditBookshelvesBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        getChildFragmentManager()
                .setFragmentResultListener(RK_EDIT_BOOKSHELF, this, mOnEditBookshelfListener);
        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            getChildFragmentManager().setFragmentResultListener(
                    RK_MENU_PICKER, this,
                    (MenuPickerDialogFragment.OnResultListener) this::onContextItemSelected);
        }
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

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_bookshelves_long);

        mResultData = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mModel = new ViewModelProvider(this).get(EditBookshelvesModel.class);
        mModel.init(getArguments());
        mModel.onSelectedPositionChanged().observe(getViewLifecycleOwner(), positionPair -> {
            // old position
            mAdapter.notifyItemChanged(positionPair.first);
            // current/new position
            mAdapter.notifyItemChanged(positionPair.second);
            // update the activity result.
            mResultData.putResultData(DBDefinitions.KEY_PK_ID,
                                      mModel.getBookshelf(positionPair.second).getId());
        });

        // The FAB lives in the activity.
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_add);
        fab.setVisibility(View.VISIBLE);
        //noinspection ConstantConditions
        fab.setOnClickListener(v -> editItem(mModel.createNewBookshelf(getContext())));

        //noinspection ConstantConditions
        mAdapter = new BookshelfAdapter(getContext());
        mVb.list.addItemDecoration(new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.list.setHasFixedSize(true);
        mVb.list.setAdapter(mAdapter);
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
            .setEnabled(mModel.getSelectedPosition() != RecyclerView.NO_POSITION);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PURGE_BLNS: {
                final int position = mModel.getSelectedPosition();
                if (position != RecyclerView.NO_POSITION) {
                    //noinspection ConstantConditions
                    StandardDialogs.purgeBLNS(getContext(), R.string.lbl_bookshelf,
                                              mModel.getBookshelf(position),
                                              () -> mModel.purgeBLNS());
                }
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        final Resources res = getResources();
        final Bookshelf bookshelf = mModel.getBookshelf(position);
        final String title = bookshelf.getName();

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();
            menu.add(new MenuPickerDialogFragment.Pick(
                    R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                    getString(R.string.action_edit_ellipsis),
                    R.drawable.ic_edit));
            menu.add(new MenuPickerDialogFragment.Pick(
                    R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                    getString(R.string.action_delete),
                    R.drawable.ic_delete));

            MenuPickerDialogFragment.newInstance(RK_MENU_PICKER, title, menu, position)
                                    .show(getChildFragmentManager(), MenuPickerDialogFragment.TAG);
        } else {
            //noinspection ConstantConditions
            final Menu menu = MenuPicker.createMenu(getContext());
            menu.add(Menu.NONE, R.id.MENU_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.ic_edit);
            menu.add(Menu.NONE, R.id.MENU_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.ic_delete);

            new MenuPicker(getContext(), title, menu, position, this::onContextItemSelected)
                    .show();
        }
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
                    mModel.deleteBookshelf(position);
                    mAdapter.notifyItemRemoved(position);

                } else {
                    //TODO: why not ? as long as we make sure there is another one left..
                    // e.g. count > 2, then you can delete '1'
                    Snackbar.make(mVb.list, R.string.warning_cannot_delete_1st_bs,
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
                .newInstance(RK_EDIT_BOOKSHELF, bookshelf)
                .show(getChildFragmentManager(), EditBookshelfDialogFragment.TAG);
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
                    v -> mModel.setSelectedPosition(holder.getBindingAdapterPosition()));

            holder.nameView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
                return true;
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final Bookshelf bookshelf = mModel.getBookshelf(position);

            holder.nameView.setText(bookshelf.getName());
            holder.itemView.setSelected(position == mModel.getSelectedPosition());
        }

        @Override
        public int getItemCount() {
            return mModel.getBookshelves().size();
        }
    }
}
