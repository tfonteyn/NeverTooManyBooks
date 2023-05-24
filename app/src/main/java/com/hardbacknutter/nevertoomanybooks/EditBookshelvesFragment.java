/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.annotation.SuppressLint;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;

/**
 * {@link Bookshelf} maintenance.
 */
public class EditBookshelvesFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookshelvesFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;
    private EditBookshelvesViewModel vm;

    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    prepareMenu(getToolbar().getMenu(), vm.getSelectedPosition());
                }
            };

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Intent resultIntent = EditBookshelvesContract
                            .createResult(vm.getSelectedBookshelfId());
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    /** The adapter for the list. */
    private BookshelfAdapter adapter;

    /** Accept the result from the dialog. */
    private final EditInPlaceParcelableLauncher<Bookshelf> editLauncher =
            new EditInPlaceParcelableLauncher<>(RK_EDIT_BOOKSHELF,
                                                EditBookshelfDialogFragment::new,
                                                this::onModified);
    private final PositionHandler positionHandler = new PositionHandler() {
        @Override
        public int getSelectedPosition() {
            return vm.getSelectedPosition();
        }

        @Override
        public void setSelectedPosition(final int position) {
            vm.setSelectedPosition(position);
        }

        @Override
        public void showContextMenu(@NonNull final View anchor,
                                    final int position,
                                    final int listIndex) {
            final ExtPopupMenu popupMenu = new ExtPopupMenu(anchor.getContext())
                    .inflate(R.menu.editing_bookshelves);
            prepareMenu(popupMenu.getMenu(), position);
            popupMenu.showAsDropDown(anchor, menuItem -> onMenuItemSelected(menuItem, listIndex));
        }
    };

    /** View Binding. */
    private FragmentEditBookshelvesBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditBookshelvesViewModel.class);
        vm.init(getArguments());

        editLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditBookshelvesBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_bookshelves);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        // FAB button to add a new Bookshelf
        final ExtendedFloatingActionButton fab = getFab();
        fab.setIconResource(R.drawable.ic_baseline_add_24);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editNewBookshelf());

        final GridLayoutManager layoutManager = (GridLayoutManager) vb.list.getLayoutManager();
        //noinspection ConstantConditions
        adapter = new BookshelfAdapter(getContext(), layoutManager.getSpanCount(),
                                       vm.getList(), positionHandler);
        adapter.registerAdapterDataObserver(adapterDataObserver);

        final GridDividerItemDecoration decoration =
                new GridDividerItemDecoration(getContext(), false, true);
        vb.list.addItemDecoration(decoration);

        vb.list.setHasFixedSize(true);
        vb.list.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    private void editNewBookshelf() {
        //noinspection ConstantConditions
        final Style style = ServiceLocator.getInstance().getStyles().getDefault(getContext());
        editLauncher.launch(new Bookshelf("", style));
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu o prepare
     */
    private void prepareMenu(@NonNull final Menu menu,
                             final int position) {
        // only if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible(position != RecyclerView.NO_POSITION);
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param index    in the list
     *
     * @return {@code true} if handled.
     */
    @SuppressLint("NotifyDataSetChanged")
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int index) {
        final int itemId = menuItem.getItemId();

        final Bookshelf bookshelf = vm.getBookshelf(index);

        if (itemId == R.id.MENU_EDIT) {
            editLauncher.launch(bookshelf);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            if (bookshelf.getId() > Bookshelf.DEFAULT) {
                //noinspection ConstantConditions
                StandardDialogs.deleteBookshelf(getContext(), bookshelf, () -> {
                    vm.deleteBookshelf(bookshelf);
                    // due to transposing row and columns, we MUST refresh the whole set.
                    adapter.notifyDataSetChanged();
                });
            } else {
                //TODO: why not ? as long as we make sure there is another one left..
                // e.g. count > 2, then you can delete '1'
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setMessage(R.string.warning_cannot_delete_1st_bs)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();
            }
            return true;

        } else if (itemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection ConstantConditions
            StandardDialogs.purgeBLNS(context, R.string.lbl_bookshelf, bookshelf.getLabel(context),
                                      () -> vm.purgeBLNS(bookshelf.getId()));
            return true;
        }

        return false;
    }

    private void onModified(@NonNull final String requestKey,
                            @NonNull final Bookshelf modified) {
        // first update the previous, now unselected, row.
        adapter.notifyItemChanged(vm.getSelectedPosition());
        // store the newly selected row.
        vm.onBookshelfEdited(modified.getId());
        // update the newly selected row.
        adapter.notifyItemChanged(vm.getSelectedPosition());
    }

    /**
     * Proxy between adapter and ViewModel.
     */
    private interface PositionHandler {

        int getSelectedPosition();

        void setSelectedPosition(int position);

        void showContextMenu(@NonNull View anchor,
                             int position,
                             int listIndex);
    }

    public static class Holder
            extends RowViewHolder {

        @NonNull
        private final RowEditBookshelfBinding vb;

        Holder(@NonNull final RowEditBookshelfBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private static class BookshelfAdapter
            extends MultiColumnRecyclerViewAdapter<Holder> {

        private final List<Bookshelf> bookshelfList;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        BookshelfAdapter(@NonNull final Context context,
                         final int columnCount,
                         final List<Bookshelf> bookshelfList,
                         @NonNull final PositionHandler positionHandler) {
            super(context, columnCount);
            this.bookshelfList = bookshelfList;
            this.positionHandler = positionHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final Holder holder = new Holder(
                    RowEditBookshelfBinding.inflate(getInflater(), parent, false));

            holder.setOnRowClickListener((v, position) -> {
                // first update the previous, now unselected, row.
                final int oldListIndex = positionHandler.getSelectedPosition();
                final int oldPosition = revert(oldListIndex);
                notifyItemChanged(oldPosition);

                // store the newly selected row.
                final int listIndex = transpose(position);
                if (listIndex == RecyclerView.NO_POSITION) {
                    // Should never get here
                    throw new IllegalStateException("No ListIndex for position=" + position);
                }
                positionHandler.setSelectedPosition(listIndex);
                // update the newly selected row.
                notifyItemChanged(position);
            });

            // long-click -> context menu
            holder.setOnRowShowContextMenuListener(
                    ShowContextMenu.getPreferredMode(parent.getContext()), (v, position) -> {
                        final int listIndex = transpose(position);
                        if (listIndex == RecyclerView.NO_POSITION) {
                            // Should never get here
                            throw new IllegalStateException(
                                    "No ListIndex for position=" + position);
                        }
                        positionHandler.showContextMenu(v, position, listIndex);
                    });


            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            final int listIndex = transpose(position);
            if (listIndex == RecyclerView.NO_POSITION) {
                holder.vb.bookshelfName.setVisibility(View.INVISIBLE);
            } else {
                holder.vb.bookshelfName.setVisibility(View.VISIBLE);

                final Bookshelf bookshelf = bookshelfList.get(listIndex);
                holder.vb.bookshelfName.setText(bookshelf.getName());
                holder.itemView.setSelected(listIndex == positionHandler.getSelectedPosition());
            }
        }

        @Override
        protected int getRealItemCount() {
            return bookshelfList.size();
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.editing_bookshelves, menu);
            prepareMenu(menu, vm.getSelectedPosition());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            return EditBookshelvesFragment.this.onMenuItemSelected(menuItem,
                                                                   vm.getSelectedPosition());
        }
    }
}
