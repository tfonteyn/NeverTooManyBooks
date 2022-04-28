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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;

/**
 * {@link Bookshelf} maintenance.
 */
@SuppressWarnings("MethodOnlyUsedFromInnerClass")
public class EditBookshelvesFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookshelvesFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;
    private EditBookshelvesViewModel mVm;

    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    prepareMenu(getToolbar().getMenu(), mVm.getSelectedPosition());
                }
            };

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Intent resultIntent = EditBookshelvesContract
                            .createResultIntent(mVm.getSelectedBookshelf());
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    /** The adapter for the list. */
    private BookshelfAdapter mListAdapter;

    /** Accept the result from the dialog. */
    private final EditBookshelfDialogFragment.Launcher mOnEditBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher(RK_EDIT_BOOKSHELF) {
                @Override
                public void onResult(final long bookshelfId) {
                    // first update the previous, now unselected, row.
                    mListAdapter.notifyItemChanged(mVm.getSelectedPosition());
                    // store the newly selected row.
                    mVm.onBookshelfEdited(bookshelfId);
                    // update the newly selected row.
                    mListAdapter.notifyItemChanged(mVm.getSelectedPosition());
                }
            };

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;
    /** View Binding. */
    private FragmentEditBookshelvesBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(EditBookshelvesViewModel.class);
        mVm.init(getArguments());

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

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_bookshelves);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        // FAB button to add a new Bookshelf
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_add_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editNewBookshelf());

        //noinspection ConstantConditions
        mListAdapter = new BookshelfAdapter(getContext());
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.list.addItemDecoration(new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.list.setHasFixedSize(true);
        mVb.list.setAdapter(mListAdapter);
    }

    private void editNewBookshelf() {
        //noinspection ConstantConditions
        final ListStyle style = ServiceLocator.getInstance().getStyles().getDefault(getContext());
        mOnEditBookshelfLauncher.launch(new Bookshelf("", style));
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu o prepare
     */
    private void prepareMenu(@NonNull final Menu menu,
                             final int position) {
        // only if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible((position != RecyclerView.NO_POSITION));
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        final Bookshelf bookshelf = mVm.getBookshelf(position);

        if (itemId == R.id.MENU_EDIT) {
            mOnEditBookshelfLauncher.launch(bookshelf);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            final Context context = getContext();
            if (bookshelf.getId() > Bookshelf.DEFAULT) {
                //noinspection ConstantConditions
                StandardDialogs.deleteBookshelf(context, bookshelf, () -> {
                    mVm.deleteBookshelf(bookshelf);
                    mListAdapter.notifyItemRemoved(position);
                    mListAdapter.notifyItemChanged(mVm.findAndSelect(position));
                });
            } else {
                //TODO: why not ? as long as we make sure there is another one left..
                // e.g. count > 2, then you can delete '1'
                //noinspection ConstantConditions
                StandardDialogs.showError(context, R.string.warning_cannot_delete_1st_bs);
            }
            return true;

        } else if (itemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection ConstantConditions
            StandardDialogs.purgeBLNS(context, R.string.lbl_bookshelf, bookshelf.getLabel(context),
                                      () -> mVm.purgeBLNS(bookshelf.getId()));
            return true;
        }

        return false;
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowEditBookshelfBinding vb;

        Holder(@NonNull final RowEditBookshelfBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.editing_bookshelves, menu);
            prepareMenu(menu, mVm.getSelectedPosition());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            return EditBookshelvesFragment.this.onMenuItemSelected(menuItem,
                                                                   mVm.getSelectedPosition());
        }
    }

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
            final RowEditBookshelfBinding vb = RowEditBookshelfBinding
                    .inflate(mInflater, parent, false);
            final Holder holder = new Holder(vb);

            // click -> set the row as 'selected'.
            holder.vb.name.setOnClickListener(v -> {
                // first update the previous, now unselected, row.
                notifyItemChanged(mVm.getSelectedPosition());
                // store the newly selected row.
                mVm.setSelectedPosition(holder.getBindingAdapterPosition());
                // update the newly selected row.
                notifyItemChanged(mVm.getSelectedPosition());
            });

            // long-click -> context menu
            holder.vb.name.setOnLongClickListener(v -> {
                //noinspection ConstantConditions
                final ExtPopupMenu popupMenu = new ExtPopupMenu(getContext())
                        .inflate(R.menu.editing_bookshelves);
                prepareMenu(popupMenu.getMenu(), holder.getBindingAdapterPosition());
                popupMenu.showAsDropDown(v, menuItem ->
                        onMenuItemSelected(menuItem, holder.getBindingAdapterPosition()));
                return true;
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final Bookshelf bookshelf = mVm.getBookshelf(position);

            holder.vb.name.setText(bookshelf.getName());
            holder.itemView.setSelected(position == mVm.getSelectedPosition());
        }

        @Override
        public int getItemCount() {
            return mVm.getList().size();
        }
    }
}
