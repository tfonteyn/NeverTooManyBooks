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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

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
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStylesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditPreferredStylesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;

/**
 * Editor for the list of all styles.
 */
public class PreferredStylesFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "PreferredStylesFragment";
    private PreferredStylesViewModel vm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final Style selectedStyle = vm.getSelectedStyle();
                    final String uuid = selectedStyle != null ? selectedStyle.getUuid() : null;
                    final Intent resultIntent = PreferredStylesContract
                            .createResult(uuid, vm.isDirty());

                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    private final PositionHandler positionHandler = new PositionHandler() {

        @Override
        public int getSelectedPosition() {
            return vm.getSelectedPosition();
        }

        @Override
        public int findPreferredAndSelect(final int position) {
            return vm.findPreferredAndSelect(position);
        }

        @Override
        public void swapItems(final int fromPosition,
                              final int toPosition) {
            vm.onItemMove(fromPosition, toPosition);
        }
    };
    /** The adapter for the list. */
    private StylesAdapter listAdapter;
    /** React to changes in the adapter. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {

                /** called if the user flipped the 'isPreferred' status. */
                @Override
                public void onItemRangeChanged(final int positionStart,
                                               final int itemCount) {
                    // The style settings in its SharedPreference file are already stored.
                    // But (some of) the database settings also need to be stored.
                    vm.updateStyle(listAdapter.getItem(positionStart));
                    onChanged();
                }

                @Override
                public void onChanged() {
                    prepareMenu(getToolbar().getMenu(), vm.getSelectedPosition());
                    // We'll save the list order in onPause.
                    vm.setDirty(true);
                }
            };
    @SuppressLint("NotifyDataSetChanged")
    private final ActivityResultLauncher<EditStyleContract.Input> editStyleContract =
            registerForActivityResult(new EditStyleContract(), o -> o.ifPresent(data -> {
                if (data.isModified()) {
                    if (data.getUuid().isPresent()) {
                        vm.getStyle(data.getUuid().get())
                          .ifPresent(style -> vm.onStyleEdited(style, data.getTemplateUuid()));
                    }
                    // always update ALL rows as the order might have changed
                    listAdapter.notifyDataSetChanged();
                }
            }));
    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;
    /** View Binding. */
    private FragmentEditStylesBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        vm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditStylesBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        toolbar.setTitle(R.string.lbl_styles_long);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        //noinspection DataFlowIssue
        listAdapter = new StylesAdapter(getContext(), vm.getStyleList(), positionHandler,
                                        vh -> itemTouchHelper.startDrag(vh));
        listAdapter.setOnRowClickListener((v, position) -> {
            // click -> set the row as 'selected'.
            // Do NOT modify the 'preferred' state of the row here.

            // first update the previous, now unselected, row.
            listAdapter.notifyItemChanged(vm.getSelectedPosition());
            // store the newly selected row.
            vm.setSelectedPosition(position);
            // update the newly selected row.
            listAdapter.notifyItemChanged(position);
        });
        listAdapter.setOnRowShowMenuListener(
                ShowContextMenu.getPreferredMode(getContext()),
                (anchor, position) -> {
                    final ExtPopupMenu popupMenu = new ExtPopupMenu(anchor.getContext())
                            .inflate(R.menu.editing_styles)
                            .setGroupDividerEnabled();
                    prepareMenu(popupMenu.getMenu(), position);

                    popupMenu.showAsDropDown(anchor, menuItem ->
                            onMenuItemSelected(menuItem, position));
                });
        listAdapter.registerAdapterDataObserver(adapterDataObserver);

        vb.list.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.list.setHasFixedSize(true);
        vb.list.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.list);

        if (savedInstanceState == null) {
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_styles_editor, null);
        }
    }

    @Override
    public void onDestroyView() {
        listAdapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        vm.updateMenuOrder();
        super.onPause();
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu     to prepare
     * @param position in the list
     */
    private void prepareMenu(@NonNull final Menu menu,
                             final int position) {
        final Style style = position == RecyclerView.NO_POSITION
                            ? null : vm.getStyle(position);

        // only user styles can be edited/deleted
        final boolean isUserStyle = style != null && style.getType() == StyleType.User;
        menu.findItem(R.id.MENU_EDIT).setVisible(isUserStyle);
        menu.findItem(R.id.MENU_DELETE).setVisible(isUserStyle);

        // only if a style is selected
        menu.findItem(R.id.MENU_DUPLICATE).setVisible(style != null);
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible(style != null);
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        final Style style = vm.getStyle(position);

        if (itemId == R.id.MENU_EDIT) {
            editStyleContract.launch(EditStyleContract.edit(style));
            return true;

        } else if (itemId == R.id.MENU_DUPLICATE) {
            editStyleContract.launch(EditStyleContract.duplicate(style));
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            //noinspection DataFlowIssue
            StandardDialogs.deleteStyle(getContext(), style, () -> {
                vm.deleteStyle(style);
                listAdapter.notifyItemRemoved(position);
                listAdapter.notifyItemChanged(vm.findPreferredAndSelect(position));
            });
            return true;

        } else if (itemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection DataFlowIssue
            StandardDialogs.purgeBLNS(context, R.string.lbl_style, style.getLabel(context),
                                      () -> vm.purgeBLNS(style.getId()));
            return true;
        }

        return false;
    }

    /**
     * Proxy between adapter and ViewModel.
     */
    private interface PositionHandler {

        int getSelectedPosition();

        /**
         * Find the best candidate position/style and make that one the 'selected'.
         *
         * @param position current position
         *
         * @return the new 'selected' position
         */
        int findPreferredAndSelect(int position);

        void swapItems(int fromPosition,
                       int toPosition);
    }

    private static class Holder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditPreferredStylesBinding vb;

        Holder(@NonNull final RowEditPreferredStylesBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private static class StylesAdapter
            extends BaseDragDropRecyclerViewAdapter<Style, Holder> {

        @NonNull
        private final PositionHandler positionHandler;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of styles
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        StylesAdapter(@NonNull final Context context,
                      @NonNull final List<Style> items,
                      @NonNull final PositionHandler positionHandler,
                      @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            this.positionHandler = positionHandler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final Holder holder = new Holder(
                    RowEditPreferredStylesBinding.inflate(getLayoutInflater(), parent, false));

            holder.setOnItemCheckChangedListener(this::togglePreferredStatus);

            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     @SuppressLint("RecyclerView") final int position) {
            super.onBindViewHolder(holder, position);
            final Context context = holder.itemView.getContext();

            final Style style = getItem(position);

            holder.vb.styleName.setText(style.getLabel(context));
            holder.vb.type.setText(style.getType().getTypeResId());
            holder.vb.groups.setText(style.getGroupsSummaryText(context));

            // set the 'preferred' state of the current row
            holder.setChecked(style.isPreferred());

            // set the 'selected' state of the current row
            holder.itemView.setSelected(position == positionHandler.getSelectedPosition());
        }

        /**
         * The user clicked the checkable button of the row; i.e. changed the 'preferred' status.
         *
         * <ol>User checked the row:
         * <li>set the row/style 'preferred'</li>
         * <li>set the row 'selected'</li>
         * </ol>
         * <ol>User unchecked the row:
         * <li>set the row to 'not preferred'</li>
         * <li>look up and down in the list to find a 'preferred' row, and set it 'selected'</li>
         * </ol>
         *
         * @param position to toggle
         *
         * @return the new status
         */
        private boolean togglePreferredStatus(final int position) {
            final Style style = getItem(position);
            final boolean checked = !style.isPreferred();
            style.setPreferred(checked);

            // if the current position/style is no longer 'preferred' but was 'selected' ...
            if (!checked && positionHandler.getSelectedPosition() == position) {
                notifyItemChanged(positionHandler.findPreferredAndSelect(position));
            } else {
                notifyItemChanged(position);
            }
            return checked;
        }

        @Override
        public boolean onItemMove(final int fromPosition,
                                  final int toPosition) {
            // changes the 'preferred' position as needed
            positionHandler.swapItems(fromPosition, toPosition);

            return super.onItemMove(fromPosition, toPosition);
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.editing_styles, menu);
            prepareMenu(menu, vm.getSelectedPosition());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            return PreferredStylesFragment.this.onMenuItemSelected(menuItem,
                                                                   vm.getSelectedPosition());
        }
    }
}
