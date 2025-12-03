/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.bookshelves;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.SuperscriptSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.GridDividerItemDecoration;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookshelvesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf.EditBookshelfBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.bookshelf.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * {@link Bookshelf} maintenance.
 */
public class EditBookshelvesFragment
        extends BaseFragment {

    private static final String TAG = "EditBookshelvesFragment";
    private static final String RK_MENU = TAG + ":rk:menu";

    private EditBookshelvesViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // can be 0; but the contract requires
                    // us to ALWAYS return a valid shelf.
                    long id = vm.getSelectedBookshelfId();
                    if (id == 0) {
                        id = vm.getDefaultBookshelf().getId();
                    }
                    final Intent resultIntent = EditBookshelvesContract.createResult(id);
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
        @NonNull
        public Bookshelf getDefaultBookshelf() {
            return vm.getDefaultBookshelf();
        }

        @Override
        public int getSelectedPosition() {
            return vm.getSelectedPosition();
        }

        @Override
        public void setSelectedPosition(final int position) {
            vm.setSelectedPosition(position);
        }

        @Override
        public void onShowContextMenu(@NonNull final View anchor,
                                      final int position) {
            showContextMenu(anchor, position);
        }
    };
    /** View Binding. */
    private FragmentEditBookshelvesBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditBookshelvesViewModel.class);
        vm.init(requireArguments());

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditParcelableLauncher<>(DBKey.FK_BOOKSHELF,
                                                    EditBookshelfDialogFragment::new,
                                                    EditBookshelfBottomSheet::new);
        editLauncher.setOnEditInPlaceListener(this::onModified);
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

        // FAB button to add a new Bookshelf
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> createNewBookshelf());

        final GridLayoutManager layoutManager = (GridLayoutManager) vb.list.getLayoutManager();
        final Context context = getContext();
        //noinspection DataFlowIssue
        adapter = new BookshelfAdapter(context, layoutManager.getSpanCount(),
                                       vm.getList(), positionHandler);

        final GridDividerItemDecoration decoration =
                new GridDividerItemDecoration(context, false, true);
        vb.list.addItemDecoration(decoration);

        vb.list.setAdapter(adapter);

        if (savedInstanceState == null) {
            TipManager.getInstance().show(context, Tip.BOOKSHELF_MANAGEMENT);
        }
    }

    private void createNewBookshelf() {
        final Style style = ServiceLocator.getInstance().getStyles().getDefault();
        // Do not use {@code EditParcelableLauncher#add} as we DO want this
        // new shelf stored in the database when edited.
        //noinspection DataFlowIssue
        editLauncher.editInPlace(getActivity(), new Bookshelf("", style));
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void showContextMenu(@NonNull final View anchor,
                                 final int position) {
        final Context context = anchor.getContext();
        final Menu menu = MenuUtils.create(context, R.menu.edit_bookshelves);

        final boolean isDefaultBookshelf =
                vm.getBookshelf(position).getId() == vm.getDefaultBookshelf().getId();
        // enable/disable as needed
        menu.findItem(R.id.MENU_SET_DEFAULT).setEnabled(!isDefaultBookshelf);
        // - deleting the default is not allowed
        // - prevents deleting the last/only shelf, as that would also be the default.
        menu.findItem(R.id.MENU_DELETE).setEnabled(!isDefaultBookshelf);

        //noinspection DataFlowIssue
        final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
        if (menuMode.isPopup()) {
            new ExtMenuPopupWindow(context)
                    .setListener(EditBookshelvesFragment.this::onMenuItemSelected)
                    .setMenuOwner(position)
                    .setMenu(menu, true)
                    .show(anchor, menuMode);
        } else {
            menuLauncher.launch(getActivity(), null, null, position, menu, true);
        }
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
        if (listIndex < 0) {
            return true;
        }

        final Bookshelf bookshelf = vm.getBookshelf(listIndex);

        if (menuItemId == R.id.MENU_EDIT) {
            //noinspection DataFlowIssue
            editLauncher.editInPlace(getActivity(), bookshelf);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            // We have prevented deletion of the default/only bookshelf already
            //noinspection DataFlowIssue
            StandardDialogs.deleteBookshelf(getContext(), bookshelf, () -> {
                vm.deleteBookshelf(getContext(), bookshelf);
                // - we're transposing row and columns
                // - and potentially changing the default/selected shelf
                // => we always MUST refresh the whole set.
                adapter.notifyDataSetChanged();
            });
            return true;

        } else if (menuItemId == R.id.MENU_SET_DEFAULT) {
            vm.setDefaultBookshelf(bookshelf);
            // see above
            adapter.notifyDataSetChanged();
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

    @SuppressLint("NotifyDataSetChanged")
    private void onModified(@NonNull final Bookshelf bookshelf) {
        // store the newly selected row.
        //noinspection DataFlowIssue
        vm.onBookshelfEdited(getContext(), bookshelf);
        // due to transposing row and columns, we MUST refresh the whole set.
        adapter.notifyDataSetChanged();
    }

    /**
     * Proxy between adapter and Fragment/ViewModel.
     */
    private interface PositionHandler {

        @NonNull
        Bookshelf getDefaultBookshelf();

        int getSelectedPosition();

        void setSelectedPosition(int position);

        /**
         * Show the menu.
         *
         * @param anchor   view
         * @param position the position (index) in the list of items.
         */
        void onShowContextMenu(@NonNull View anchor,
                               int position);
    }

    public static class Holder
            extends RowViewHolder {

        private static final SpannableString ASTERISK_SUFFIX;

        static {
            ASTERISK_SUFFIX = new SpannableString(" *");
            ASTERISK_SUFFIX.setSpan(new SuperscriptSpan(), 0, 1,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        @NonNull
        private final RowEditBookshelfBinding vb;

        Holder(@NonNull final RowEditBookshelfBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        public void onBind(@Nullable final Bookshelf bookshelf,
                           final boolean isDefault) {
            if (bookshelf == null) {
                vb.getRoot().setVisibility(View.INVISIBLE);
            } else {
                vb.getRoot().setVisibility(View.VISIBLE);

                final Context context = itemView.getContext();
                final String name;
                if (isDefault) {
                    name = context.getString(R.string.a_b, bookshelf.getName(), ASTERISK_SUFFIX);
                } else {
                    name = bookshelf.getName();
                }
                vb.bookshelfName.setText(name);
            }
        }
    }

    private static class BookshelfAdapter
            extends MultiColumnRecyclerViewAdapter<Holder> {

        private final List<Bookshelf> items;
        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context         Current context
         * @param columnCount     from the grid layout
         * @param items           to display
         * @param positionHandler Proxy between adapter and ViewModel.
         */
        BookshelfAdapter(@NonNull final Context context,
                         @IntRange(from = 1) final int columnCount,
                         @NonNull final List<Bookshelf> items,
                         @NonNull final PositionHandler positionHandler) {
            super(context, columnCount);
            this.items = items;
            this.positionHandler = positionHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditBookshelfBinding vb = RowEditBookshelfBinding
                    .inflate(getInflater(), parent, false);
            adjustColumns(vb.getRoot());
            final Holder holder = new Holder(vb);

            // click -> select the row
            holder.setOnRowClickListener((v, gridPosition) -> {
                // first update the previous, now unselected, row.
                notifyItemChanged(listToGridPosition(positionHandler.getSelectedPosition()));

                // store the newly selected row.
                final int listIndex = gridToListPosition(gridPosition);
                requireValidOrThrow(listIndex, gridPosition);
                positionHandler.setSelectedPosition(listIndex);

                // update the newly selected row.
                notifyItemChanged(gridPosition);
            });

            // long-click -> context menu
            holder.setOnRowLongClickListener(
                    ExtMenuButton.getPreferredMode(parent.getContext()), (v, gridPosition) -> {
                        final int listIndex = gridToListPosition(gridPosition);
                        requireValidOrThrow(listIndex, gridPosition);
                        positionHandler.onShowContextMenu(v, listIndex);
                    });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final int listIndex = gridToListPosition(position);
            if (listIndex == RecyclerView.NO_POSITION) {
                holder.onBind(null, false);
            } else {
                final Bookshelf bookshelf = items.get(listIndex);
                holder.onBind(bookshelf, bookshelf.getId()
                                         == positionHandler.getDefaultBookshelf().getId());
                holder.itemView.setSelected(listIndex == positionHandler.getSelectedPosition());
            }
        }

        @Override
        protected int getListSize() {
            return items.size();
        }
    }
}
