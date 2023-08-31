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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStyleBookLevelColumnsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditStyleBookLevelColumnBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;

/**
 * Note this is NOT extending BasePreferenceFragment, so we must handle the base
 * functionality (e.g. StyleViewModel) ourselves.
 */
@Keep
public class StyleBookLevelColumnsFragment
        extends BaseFragment {

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

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

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        //noinspection DataFlowIssue
        vb.columnList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.columnList.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        final BookLevelColumnWrapperListAdapter listAdapter =
                new BookLevelColumnWrapperListAdapter(getContext(),
                                                      vm.createWrappedBookLevelColumnList(),
                                                      vh -> itemTouchHelper.startDrag(vh));

        vb.columnList.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.columnList);
    }

    /**
     * Holder for each row.
     */
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
            setChecked(wrappedColumn.isVisible());

            final Sort current = wrappedColumn.getSort();
            if (current != null) {
                enableDrag(true);
                vb.btnOrderBy.setVisibility(View.VISIBLE);
                setIcon(current);

                vb.btnOrderBy.setOnClickListener(v -> {
                    final Sort nextValue = wrappedColumn.getSort().next();
                    wrappedColumn.setSort(nextValue);
                    setIcon(nextValue);
                });
            } else {
                enableDrag(false);
                vb.btnOrderBy.setVisibility(View.INVISIBLE);
            }
        }

        private void setIcon(@NonNull final Sort sort) {
            switch (sort) {
                case Unsorted:
                    vb.btnOrderBy.setIconResource(R.drawable.ic_baseline_clear_24);
                    break;
                case Asc:
                    vb.btnOrderBy.setIconResource(R.drawable.ic_baseline_sort_by_alpha_24);
                    break;
                case Desc:
                    vb.btnOrderBy.setIconResource(R.drawable.ic_baseline_sort_by_alpha_reversed_24);

                    break;
            }
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
            holder.setOnItemCheckChangedListener(position -> {
                final StyleViewModel.WrappedBookLevelColumn wrappedColumn = getItem(position);
                final boolean newStatus = !wrappedColumn.isVisible();
                wrappedColumn.setVisible(newStatus);
                notifyItemChanged(position);
                return newStatus;
            });
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
