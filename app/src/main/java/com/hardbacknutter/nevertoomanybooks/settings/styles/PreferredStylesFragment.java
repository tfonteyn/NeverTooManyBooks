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
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStylesBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditPreferredStylesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * Editor for the list of all styles.
 */
public class PreferredStylesFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "PreferredStylesFragment";
    private static final String RK_MENU = TAG + ":rk:menu";
    private PreferredStylesViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final String uuid = vm.getSelectedUuid();
                    final Intent resultIntent = EditPreferredStylesContract
                            .createResult(uuid, vm.isModified());

                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };

    private final PositionHandler positionHandler = new PositionHandler() {

        @Override
        public String getSelectedUuid() {
            return vm.getSelectedUuid();
        }

        @Override
        public int findPreferredAndSelect(@IntRange(from = 0) final int position) {
            return vm.findPreferredAndSelectIt(position);
        }
    };

    /** The adapter for the list. */
    private StylesAdapter adapter;
    /** React to changes in the adapter. */
    private final RecyclerView.AdapterDataObserver adapterObserver =
            new SimpleAdapterDataObserver() {
                /**
                 * Called when the {@code preferred} status is changed by the user.
                 * <p>
                 * Called when the {@code selected} status is changed by the user:
                 * once for the row deselected + once for the row selected.
                 */
                @Override
                public void onItemRangeChanged(@IntRange(from = 0) final int positionStart,
                                               final int itemCount) {
                    //noinspection DataFlowIssue
                    vm.updateStyle(getContext(), adapter.getItem(positionStart));
                    onChanged();
                }

                @Override
                public void onChanged() {
                    prepareMenu(getToolbar().getMenu(), vm.getSelectedPosition());
                    // We'll save the list order in onPause.
                    vm.setModified(true);
                }
            };

    @SuppressLint("NotifyDataSetChanged")
    private final ActivityResultLauncher<EditStyleContract.Input> editStyleContract =
            registerForActivityResult(new EditStyleContract(), o -> o.ifPresent(data -> {
                if (data.isModified()) {
                    //noinspection DataFlowIssue
                    data.getUuid().ifPresent(uuid -> vm.onStyleEdited(getContext(), uuid,
                                                                      data.getTemplateUuid()));

                    // always update ALL rows as the order might have changed
                    adapter.notifyDataSetChanged();
                }
            }));

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;
    /** View Binding. */
    private FragmentEditStylesBinding vb;
    private ExtMenuLauncher menuLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(PreferredStylesViewModel.class);
        vm.init(requireArguments());

        final FragmentManager fm = getChildFragmentManager();

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
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
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.list);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_styles_long);
        toolbar.setSubtitle(null);
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());

        //noinspection DataFlowIssue
        adapter = new StylesAdapter(getContext(), vm.getList(), positionHandler,
                                    vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowClickListener((v, position) -> {
            // click -> set the row as 'selected'.
            // Do NOT modify the 'preferred' state of the row here.

            // first update the previous, now unselected, row.
            adapter.notifyItemChanged(vm.getSelectedPosition());
            // store the newly selected row.
            vm.setSelectedPosition(position);
            // update the newly selected row.
            adapter.notifyItemChanged(position);
        });
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(getContext()), (anchor, position) -> {
                    final Context context = anchor.getContext();

                    final Menu menu = MenuUtils.create(context, R.menu.preferred_styles);
                    prepareMenu(menu, position);

                    final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(context)
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(menu, true)
                                .show(anchor, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), null, null, position, menu, true);
                    }
                });
        adapter.registerAdapterDataObserver(adapterObserver);

        final MaterialDividerItemDecoration decoration =
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL);
        vb.list.addItemDecoration(decoration);

        vb.list.setAdapter(adapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.list);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(getContext(), Tip.STYLES_EDITOR);
        }
    }

    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterObserver);
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        //noinspection DataFlowIssue
        vm.updateMenuOrder(getContext());
        super.onPause();
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param menu     to prepare
     * @param position in the list; or {@link RecyclerView#NO_POSITION}
     */
    private void prepareMenu(@NonNull final Menu menu,
                             @IntRange(from = RecyclerView.NO_POSITION) final int position) {

        @Nullable
        final Style style = position == RecyclerView.NO_POSITION ? null : vm.getStyle(position);

        // only user styles can be edited/deleted
        final boolean isUserStyle = style != null && style.getType() == Style.Type.User;
        menu.findItem(R.id.MENU_EDIT).setVisible(isUserStyle);
        menu.findItem(R.id.MENU_DELETE).setVisible(isUserStyle);

        // only if a style is selected
        menu.findItem(R.id.MENU_DUPLICATE).setVisible(style != null);
        menu.findItem(R.id.MENU_PURGE_BLNS).setVisible(style != null);
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    @SuppressLint("Range")
    private boolean onMenuItemSelected(@IntRange(from = RecyclerView.NO_POSITION) final int position,
                                       @IdRes final int menuItemId) {

        // should never be the case.... flw
        if (position == RecyclerView.NO_POSITION) {
            return false;
        }

        final Style style = vm.getStyle(position);

        if (menuItemId == R.id.MENU_EDIT) {
            editStyleContract.launch(EditStyleContract.edit(style));
            return true;

        } else if (menuItemId == R.id.MENU_DUPLICATE) {
            editStyleContract.launch(EditStyleContract.duplicate(style));
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            //noinspection DataFlowIssue
            StandardDialogs.deleteStyle(getContext(), style, () -> {
                // If the deleted row was the 'selected' row,
                // FIRST find a new row to select based on the CURRENT row
                if (style.getUuid().equals(vm.getSelectedUuid())) {
                    adapter.notifyItemChanged(vm.findPreferredAndSelectIt(position));
                }
                vm.deleteStyle(style);
                adapter.notifyItemRemoved(position);
            });
            return true;

        } else if (menuItemId == R.id.MENU_PURGE_BLNS) {
            final Context context = getContext();
            //noinspection DataFlowIssue
            StandardDialogs.purgeNodeStates(context, R.string.lbl_style, style.getLabel(context),
                                            () -> vm.purgeNodeStates(style));
            return true;
        }

        return false;
    }

    /**
     * Proxy between adapter and ViewModel.
     */
    private interface PositionHandler {

        /**
         * Get the currently selected {@link Style}.
         *
         * @return Style uuid
         */
        @Nullable
        String getSelectedUuid();

        /**
         * Find the best candidate position/style and make that one the 'selected',
         * starting from the currently selected row.
         *
         * @param position current position
         *
         * @return the new 'selected' position
         */
        int findPreferredAndSelect(@IntRange(from = 0) int position);
    }

    private static class Holder
            extends CheckableDragDropViewHolder
            implements BindableViewHolder<Style> {

        @NonNull
        private final RowEditPreferredStylesBinding vb;

        Holder(@NonNull final RowEditPreferredStylesBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @Override
        public void onBind(@NonNull final Style style) {
            final Context context = itemView.getContext();
            vb.styleName.setText(style.getLabel(context));
            vb.type.setText(style.getType().getLabel(context));
            vb.groups.setText(style.getGroupsSummaryText(context));

            // set the 'preferred' state of the current row
            setChecked(style.isPreferred());
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
         * @param positionHandler   Proxy between adapter and ViewModel
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

            final RowEditPreferredStylesBinding vb = RowEditPreferredStylesBinding.inflate(
                    getLayoutInflater(), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);

            holder.setOnItemCheckChangedListener(this::togglePreferredStatus);

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     @SuppressLint("RecyclerView") final int position) {
            super.onBindViewHolder(holder, position);

            final Style style = getItem(position);
            holder.onBind(style);

            // set the 'selected' state of the current row
            holder.itemView.setSelected(style.getUuid().equals(positionHandler.getSelectedUuid()));
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
        private boolean togglePreferredStatus(@IntRange(from = 0) final int position) {
            final Style style = getItem(position);
            final boolean checked = !style.isPreferred();
            style.setPreferred(checked);

            // update checked and selected status
            notifyItemChanged(position);

            // if the current position/style is no longer 'preferred' but was 'selected' ...
            if (!checked && style.getUuid().equals(positionHandler.getSelectedUuid())) {
                notifyItemChanged(positionHandler.findPreferredAndSelect(position));
            }
            return checked;
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.preferred_styles, menu);
            prepareMenu(menu, vm.getSelectedPosition());
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_EDIT_DEFAULT).setVisible(true);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_EDIT_DEFAULT) {
                getParentFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .addToBackStack(StyleDefaultsFragment.TAG)
                        .replace(R.id.main_fragment,
                                 StyleDefaultsFragment.create(),
                                 StyleDefaultsFragment.TAG)
                        .commit();
                return true;
            }

            final int position = vm.getSelectedPosition();
            if (BuildConfig.DEBUG /* */) {
                if (position == RecyclerView.NO_POSITION) {
                    throw new IllegalStateException("position=-1");
                }
            }
            return PreferredStylesFragment.this.onMenuItemSelected(position, menuItemId);
        }
    }
}
