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
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStyleGroupsBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditStyleGroupsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;

/**
 * Editor for the groups of a single style.
 * <p>
 * Note this is NOT extending BasePreferenceFragment.
 * We must handle the base functionality (e.g. StyleViewModel) ourselves.
 */
@Keep
public class StyleGroupsFragment
        extends BaseFragment {

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /** Style we are editing. */
    private StyleViewModel vm;

    /**
     * Either make the groups permanent, or inform the user they need to have at least one group.
     */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (vm.hasGroupsSelected()) {
                        vm.updateStyleGroups();
                        // just pop, we're always called from a fragment
                        getParentFragmentManager().popBackStack();

                    } else {
                        //noinspection DataFlowIssue
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.warning_24px)
                                .setTitle(R.string.pt_bob_groups)
                                .setMessage(R.string.warning_select_at_least_1_group)
                                .setNegativeButton(R.string.action_discard, (d, w) ->
                                        // just pop, we're always called from a fragment
                                        getParentFragmentManager().popBackStack())
                                .setPositiveButton(R.string.action_edit, (d, w) ->
                                        d.dismiss())
                                .create()
                                .show();
                    }
                }
            };

    /** View Binding. */
    private FragmentEditStyleGroupsBinding vb;

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

        vb = FragmentEditStyleGroupsBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Style style = vm.getStyle();

        final Toolbar toolbar = getToolbar();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }
        final Context context = getContext();
        //noinspection DataFlowIssue
        toolbar.setSubtitle(style.getLabel(context));

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vb.groupList.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
        vb.groupList.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        final GroupWrapperListAdapter listAdapter =
                new GroupWrapperListAdapter(context,
                                            vm.createWrappedGroupList(),
                                            vh -> itemTouchHelper.startDrag(vh));

        vb.groupList.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.groupList);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_booklist_style_groups,
                                             null);
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditStyleGroupsBinding vb;

        Holder(@NonNull final RowEditStyleGroupsBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final StyleViewModel.WrappedGroup wrappedGroup) {
            final Context context = itemView.getContext();
            vb.booklistGroupName.setText(wrappedGroup.getGroup().getLabel(context));
            setChecked(wrappedGroup.isPresent());
        }
    }

    private static class GroupWrapperListAdapter
            extends BaseDragDropRecyclerViewAdapter<StyleViewModel.WrappedGroup, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of groups (in WrappedGroup)
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        GroupWrapperListAdapter(@NonNull final Context context,
                                @NonNull final List<StyleViewModel.WrappedGroup> items,
                                @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowEditStyleGroupsBinding vb = RowEditStyleGroupsBinding
                    .inflate(getLayoutInflater(), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);

            holder.setOnItemCheckChangedListener(position -> {
                final StyleViewModel.WrappedGroup wrappedGroup = getItem(position);
                final boolean newStatus = !wrappedGroup.isPresent();
                wrappedGroup.setPresent(newStatus);
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
