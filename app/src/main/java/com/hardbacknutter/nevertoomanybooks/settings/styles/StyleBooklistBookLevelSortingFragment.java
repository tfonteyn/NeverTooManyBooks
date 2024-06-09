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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStyleBookLevelColumnsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditStyleBookLevelColumnBinding;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * Editor for the book-level field sorting of a single style.
 * <p>
 * Note this is NOT extending BasePreferenceFragment.
 * We must handle the base functionality (e.g. StyleViewModel) ourselves.
 */
@Keep
public class StyleBooklistBookLevelSortingFragment
        extends BaseFragment {

    private static final String TAG = "StyleBooklistBookLevelS";
    private static final String RK_MENU = TAG + ":menu";

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;
    private ExtMenuLauncher menuLauncher;

    /** Style we are editing. */
    private StyleViewModel vm;

    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    vm.updateBookLevelColumnList();
                    // just pop, we're always called from a fragment
                    getParentFragmentManager().popBackStack();
                }
            };

    /** View Binding. */
    private FragmentEditStyleBookLevelColumnsBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemClick);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    private BookLevelColumnWrapperListAdapter listAdapter;


    private boolean onMenuItemClick(final int positionOrId,
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

        vm.getWrappedBookLevelColumnList().get(positionOrId).setSort(nextValue);
        listAdapter.notifyItemChanged(positionOrId);
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

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final List<StyleViewModel.WrappedBookLevelColumn> groupSortingFields =
                vm.getStyle()
                  .getGroupList()
                  .stream()
                  .flatMap(booklistGroup -> booklistGroup.getGroupDomainExpressions().stream())
                  // only show the groups that do sorting
                  .filter(domainExpression -> domainExpression.getSort() != Sort.Unsorted)
                  // We can get duplicate names;
                  // e.g. "Year Read" and "Month Read" both use "Read"
                  // as the name. So use a LinkedHashMap to prevent duplicates
                  .collect(Collectors.toMap(
                          domainExpression -> domainExpression.getDomain().getName(),
                          DomainExpression::getSort,
                          (existingKey, replacement) -> existingKey,
                          LinkedHashMap::new))
                  .entrySet()
                  .stream()
                  // Now convert the map back a list
                  .map(entry -> new StyleViewModel.WrappedBookLevelColumn(
                          entry.getKey(),
                          entry.getValue()))
                  .collect(Collectors.toList());


        //noinspection DataFlowIssue
        vb.columnList.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
        vb.columnList.setHasFixedSize(true);

        // setup the adapters

        // The adapter for the fixed Group columns.
        final HeaderAdapter headerAdapter =
                new HeaderAdapter(context, groupSortingFields);

        // The adapter for the list.
        listAdapter = new BookLevelColumnWrapperListAdapter(context,
                                                      vm.getWrappedBookLevelColumnList(),
                                                      vh -> itemTouchHelper.startDrag(vh));

        listAdapter.setOnRowShowMenuListener(ExtMenuButton.Always, (anchor, position) -> {
            final Menu menu = MenuUtils.create(context, R.menu.sorting_options);
            final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
            if (menuMode.isPopup()) {
                new ExtMenuPopupWindow(anchor.getContext())
                        .setListener(this::onMenuItemClick)
                        .setPosition(position)
                        .setMenu(menu, true)
                        .show(anchor, menuMode);
            } else {
                final String label = vm.getWrappedBookLevelColumnList().get(position)
                                       .getLabel(anchor.getContext());
                menuLauncher.launch(getActivity(), position, label, null, menu, true);
            }
        });

        // Combine the adapters for the list header and the actual list
        final ConcatAdapter concatAdapter = new ConcatAdapter(
                headerAdapter, listAdapter);

        vb.columnList.setAdapter(concatAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.columnList);
    }

    /**
     * Holder for each row.
     */
    private static class HeaderRowHolder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditStyleBookLevelColumnBinding vb;

        HeaderRowHolder(@NonNull final RowEditStyleBookLevelColumnBinding vb) {
            super(vb.getRoot());
            this.vb = vb;

            vb.btnRowMenu.setEnabled(false);
            vb.ROWGRABBERICON.setVisibility(View.INVISIBLE);
        }

        void onBind(@NonNull final StyleViewModel.WrappedBookLevelColumn wrappedColumn) {
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

        void onBind(@NonNull final StyleViewModel.WrappedBookLevelColumn wrappedColumn) {
            final Context context = itemView.getContext();
            vb.columnName.setText(wrappedColumn.getLabel(context));

            setRowMenuButtonIconResource(StyleViewModel.getIconResId(wrappedColumn.getSort()));
        }
    }

    private static class HeaderAdapter
            extends BaseDragDropRecyclerViewAdapter<StyleViewModel.WrappedBookLevelColumn,
            HeaderRowHolder> {

        /**
         * Constructor.
         *
         * @param context Current context
         * @param items   List of columns (in WrappedBookLevelColumn)
         */
        HeaderAdapter(
                @NonNull final Context context,
                @NonNull final List<StyleViewModel.WrappedBookLevelColumn> items) {
            super(context, items, null);
        }

        @NonNull
        @Override
        public HeaderRowHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                  final int viewType) {

            final RowEditStyleBookLevelColumnBinding vb = RowEditStyleBookLevelColumnBinding
                    .inflate(getLayoutInflater(), parent, false);
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
            extends BaseDragDropRecyclerViewAdapter<StyleViewModel.WrappedBookLevelColumn, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of columns (in WrappedBookLevelColumn)
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        BookLevelColumnWrapperListAdapter(
                @NonNull final Context context,
                @NonNull final List<StyleViewModel.WrappedBookLevelColumn> items,
                @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowEditStyleBookLevelColumnBinding vb = RowEditStyleBookLevelColumnBinding
                    .inflate(getLayoutInflater(), parent, false);
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
