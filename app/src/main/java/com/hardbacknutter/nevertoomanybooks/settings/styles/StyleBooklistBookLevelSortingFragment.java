/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStyleBookLevelColumnsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditStyleBookLevelColumnBinding;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * Editor for the book-level field sorting of a single style.
 * <p>
 * Note this is NOT extending
 * {@link com.hardbacknutter.nevertoomanybooks.settings.BaseSettingsFragment}.
 * We must handle the base functionality (e.g. StyleViewModel) ourselves.
 */
@Keep
public class StyleBooklistBookLevelSortingFragment
        extends BaseFragment {

    private static final String TAG = "StyleBooklistBookLevelS";
    private static final String RK_MENU = TAG + ":rk:menu";

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;
    private ExtMenuLauncher menuLauncher;

    /** Style we are editing. */
    private StyleViewModel vm;

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    vm.updateBookLevelFieldsSorting();
                    // just pop, we're always called from a fragment
                    getParentFragmentManager().popBackStack();
                }
            };

    /** View Binding. */
    private FragmentEditStyleBookLevelColumnsBinding vb;

    private BookLevelColumnWrapperListAdapter adapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemClick);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    private boolean onMenuItemClick(final int menuOwner,
                                    final int menuItemId) {

        final Sort nextValue;
        if (menuItemId == R.id.MENU_SORT_UNSORTED) {
            nextValue = Sort.Unsorted;
        } else if (menuItemId == R.id.MENU_SORT_ASC) {
            nextValue = Sort.Asc;
        } else if (menuItemId == R.id.MENU_SORT_DESC) {
            nextValue = Sort.Desc;
        } else {
            // Should never get here... flw
            return false;
        }

        vm.getBookLevelFieldsSorting().get(menuOwner).setSort(nextValue);
        adapter.notifyItemChanged(menuOwner);
        return true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        vb = FragmentEditStyleBookLevelColumnsBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.columnList);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Context context = getContext();
        final Style style = vm.getStyle();

        final Toolbar toolbar = getToolbar();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }
        //noinspection DataFlowIssue
        toolbar.setSubtitle(style.getLabel(context));

        vb.columnList.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
        vb.columnList.setHasFixedSize(true);

        // set up the adapters

        // The adapter for the fixed Group columns.
        final HeaderAdapter headerAdapter = new HeaderAdapter(vm.getGroupSortingFields());

        // The adapter for the list.
        adapter = new BookLevelColumnWrapperListAdapter(
                vm.getBookLevelFieldsSorting(),
                vh -> itemTouchHelper.startDrag(vh));

        adapter.setOnRowShowMenuListener(
                ExtMenuButton.Always,
                (v, position) -> {
                    if (position == RecyclerView.NO_POSITION) {
                        return;
                    }
                    final Menu menu = MenuUtils.create(v.getContext(), R.menu.sorting_options);
                    final String label = vm.getBookLevelFieldsSorting().get(position)
                                           .getLabel(v.getContext());
                    menuLauncher.launch(v, label, null, position, menu);
        });

        // Combine the adapters for the list header and the actual list
        final ConcatAdapter concatAdapter = new ConcatAdapter(
                headerAdapter, adapter);

        vb.columnList.setAdapter(concatAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.columnList);
    }

    private static class HeaderRowHolder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditStyleBookLevelColumnBinding vb;

        HeaderRowHolder(@NonNull final RowEditStyleBookLevelColumnBinding vb) {
            super(vb.getRoot());
            this.vb = vb;

            vb.ROWMENUBTN.setEnabled(false);
            showDragHandle(false);
        }

        void onBind(@NonNull final StyleViewModel.WrappedBookLevelField wrappedColumn) {
            final Context context = itemView.getContext();
            final String text = context.getString(R.string.a_bracket_b_bracket,
                                                  wrappedColumn.getLabel(context),
                                                  context.getString(R.string.lbl_group));
            vb.columnName.setText(text);

            setRowMenuButtonIconResource(StyleViewModel.getIconResId(wrappedColumn.getSort()));
        }
    }

    private static class Holder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditStyleBookLevelColumnBinding vb;

        Holder(@NonNull final RowEditStyleBookLevelColumnBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final StyleViewModel.WrappedBookLevelField wrappedColumn) {
            final Context context = itemView.getContext();
            vb.columnName.setText(wrappedColumn.getLabel(context));

            setRowMenuButtonIconResource(StyleViewModel.getIconResId(wrappedColumn.getSort()));
        }
    }

    private static class HeaderAdapter
            extends BaseDragDropRecyclerViewAdapter<StyleViewModel.WrappedBookLevelField,
            HeaderRowHolder> {

        /**
         * Constructor.
         *
         * @param items List of columns
         */
        HeaderAdapter(@NonNull final List<StyleViewModel.WrappedBookLevelField> items) {
            super(items, null);
        }

        @NonNull
        @Override
        public HeaderRowHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                  final int viewType) {

            final RowEditStyleBookLevelColumnBinding vb = RowEditStyleBookLevelColumnBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false);
            final HeaderRowHolder holder = new HeaderRowHolder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final HeaderRowHolder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }

    private static class BookLevelColumnWrapperListAdapter
            extends BaseDragDropRecyclerViewAdapter<StyleViewModel.WrappedBookLevelField, Holder> {

        /**
         * Constructor.
         *
         * @param items             List of columns
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        BookLevelColumnWrapperListAdapter(
                @NonNull final List<StyleViewModel.WrappedBookLevelField> items,
                @NonNull final StartDragListener dragStartListener) {
            super(items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowEditStyleBookLevelColumnBinding vb = RowEditStyleBookLevelColumnBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }
}
