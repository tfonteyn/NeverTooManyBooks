/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * {@link Bookshelf} maintenance.
 */
public class EditBookshelvesFragment
        extends BaseFragment {

    private static final String TAG = "EditBookshelvesFragment";
    private static final String RK_MENU = TAG + ":rk:menu";

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
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    /** The adapter for the list. */
    private BookshelfAdapter adapter;

    /** Accept the result from the dialog. */
    private EditParcelableLauncher<Bookshelf> editLauncher;
    private ExtMenuLauncher menuLauncher;

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
                                    final int gridPosition,
                                    final int listIndex) {
            final Context context = anchor.getContext();
            final Menu menu = MenuUtils.create(context, R.menu.edit_bookshelves);
            prepareMenu(menu, gridPosition);

            //noinspection DataFlowIssue
            final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
            if (menuMode.isPopup()) {
                new ExtMenuPopupWindow(context)
                        .setListener(EditBookshelvesFragment.this::onMenuItemSelected)
                        .setMenuOwner(listIndex)
                        .setMenu(menu, true)
                        .show(anchor, menuMode);
            } else {
                menuLauncher.launch(getActivity(), null, null, listIndex, menu, true);
            }
        }
    };

    /** View Binding. */
    private FragmentEditBookshelvesBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditBookshelvesViewModel.class);
        vm.init(getArguments());

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditParcelableLauncher<>(DBKey.FK_BOOKSHELF,
                                                    EditBookshelfDialogFragment::new,
                                                    EditBookshelfBottomSheet::new,
                                                    this::onModified);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
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
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.list);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_bookshelves);
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        // FAB button to add a new Bookshelf
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> editNewBookshelf());

        final GridLayoutManager layoutManager = (GridLayoutManager) vb.list.getLayoutManager();
        //noinspection DataFlowIssue
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
        final Style style = ServiceLocator.getInstance().getStyles().getDefault();
        // Not as 'add' as we DO want this new shelf stored in the database when edited.
        //noinspection DataFlowIssue
        editLauncher.editInPlace(getActivity(), new Bookshelf("", style));
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu     to prepare
     * @param position as selected in the adapter
     */
    private void prepareMenu(@NonNull final Menu menu,
                             final int position) {
        // only if a shelf is selected
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible(position != RecyclerView.NO_POSITION);
    }

    /**
     * Menu selection listener.
     *
     * @param listIndex  in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    @SuppressLint("NotifyDataSetChanged")
    private boolean onMenuItemSelected(final int listIndex,
                                       @IdRes final int menuItemId) {

        // If there is no Bookshelf selected, just quit here.
        // 2024-07-19: seen this happen, but not sure why - cannot reproduce.
        if (listIndex < 0) {
            return true;
        }

        final Bookshelf bookshelf = vm.getBookshelf(listIndex);

        if (menuItemId == R.id.MENU_EDIT) {
            //noinspection DataFlowIssue
            editLauncher.editInPlace(getActivity(), bookshelf);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            if (bookshelf.getId() > Bookshelf.HARD_DEFAULT) {
                //noinspection DataFlowIssue
                StandardDialogs.deleteBookshelf(getContext(), bookshelf, () -> {
                    vm.deleteBookshelf(getContext(), bookshelf);
                    // due to transposing row and columns, we MUST refresh the whole set.
                    adapter.notifyDataSetChanged();
                });
            } else {
                //TODO: why not ? as long as we make sure there is another one left..
                // e.g. count > 2, then you can delete '1'
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.warning_24px)
                        .setMessage(R.string.warning_cannot_delete_1st_bs)
                        .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();
            }
            return true;

        } else if (menuItemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection DataFlowIssue
            StandardDialogs.purgeNodeStates(context, R.string.lbl_bookshelf,
                                            bookshelf.getLabel(context),
                                            () -> vm.purgeNodeStates(bookshelf));
            return true;
        }

        return false;
    }

    private void onModified(@NonNull final Bookshelf bookshelf) {
        // first update the previous, now unselected, row.
        adapter.notifyItemChanged(vm.getSelectedPosition());
        // store the newly selected row.
        vm.onBookshelfEdited(bookshelf.getId());
        // update the newly selected row.
        adapter.notifyItemChanged(vm.getSelectedPosition());
    }

    /**
     * Proxy between adapter and ViewModel.
     */
    private interface PositionHandler {

        int getSelectedPosition();

        void setSelectedPosition(int position);

        /**
         * Show the menu.
         *
         * @param anchor       view
         * @param gridPosition the position in the adapter, this can/will be different
         *                     from the listIndex as we're using a
         *                     {@link GridLayoutManager}.
         * @param listIndex    the actual index/position in the list of items.
         */
        void showContextMenu(@NonNull View anchor,
                             int gridPosition,
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

        private static final String ERROR_NO_LIST_INDEX_FOR_POSITION = "No ListIndex for position=";
        private final List<Bookshelf> bookshelfList;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param columnCount     from the grid layout
         * @param bookshelfList   to display
         * @param positionHandler Proxy between adapter and ViewModel.
         */
        BookshelfAdapter(@NonNull final Context context,
                         final int columnCount,
                         @NonNull final List<Bookshelf> bookshelfList,
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
                    throw new IllegalStateException(ERROR_NO_LIST_INDEX_FOR_POSITION + position);
                }
                positionHandler.setSelectedPosition(listIndex);
                // update the newly selected row.
                notifyItemChanged(position);
            });

            // long-click -> context menu
            holder.setOnRowLongClickListener(
                    ExtMenuButton.getPreferredMode(parent.getContext()), (v, gridPosition) -> {
                        final int listIndex = transpose(gridPosition);
                        if (listIndex == RecyclerView.NO_POSITION) {
                            // Should never get here
                            throw new IllegalStateException(
                                    ERROR_NO_LIST_INDEX_FOR_POSITION + gridPosition);
                        }
                        positionHandler.showContextMenu(v, gridPosition, listIndex);
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

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.edit_bookshelves, menu);
            prepareMenu(menu, vm.getSelectedPosition());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            return EditBookshelvesFragment.this.onMenuItemSelected(vm.getSelectedPosition(),
                                                                   menuItem.getItemId()
            );
        }
    }
}
