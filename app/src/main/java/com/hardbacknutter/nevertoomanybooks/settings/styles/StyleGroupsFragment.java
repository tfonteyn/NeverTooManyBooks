/*
 * @Copyright 2020 HardBackNutter
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
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditStyleGroupsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Note this is NOT extending BasePreferenceFragment, so we must handle the base
 * functionality (e.g. StyleViewModel) ourselves.
 */
@Keep
public class StyleGroupsFragment
        extends Fragment {

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /** Style we are editing. */
    private StyleViewModel mStyleViewModel;

    /**
     * Either make the groups permanent, or inform the user they need to have at least one group.
     */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (mStyleViewModel.hasGroupsSelected()) {
                        mStyleViewModel.updateStyleGroups();
                        getParentFragmentManager().popBackStack();

                    } else {
                        //noinspection ConstantConditions
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.ic_warning)
                                .setTitle(R.string.pg_style_groups)
                                .setMessage(R.string.warning_select_at_least_1_group)
                                .setNegativeButton(R.string.action_discard, (d, w) ->
                                        getParentFragmentManager().popBackStack())
                                .setPositiveButton(R.string.action_edit, (d, w) ->
                                        d.dismiss())
                                .create()
                                .show();
                    }
                }
            };

    /** View Binding. */
    private FragmentEditStyleGroupsBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mStyleViewModel = new ViewModelProvider(getActivity()).get(StyleViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        mVb = FragmentEditStyleGroupsBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        //noinspection ConstantConditions
        mVb.groupList.addItemDecoration(
                new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.groupList.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        final GroupWrapperListAdapter listAdapter =
                new GroupWrapperListAdapter(getContext(),
                                            mStyleViewModel.createWrappedGroupList(),
                                            vh -> mItemTouchHelper.startDrag(vh));

        mVb.groupList.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.groupList);

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(), R.string.tip_booklist_style_groups,
                                             null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setSubtitle(getString(R.string.name_colon_value,
                                        getString(R.string.pg_style_groups),
                                        mStyleViewModel.getStyle().getLabel(getContext())));
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView nameView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
        }
    }

    static class GroupWrapperListAdapter
            extends RecyclerViewAdapterBase<StyleViewModel.WrappedGroup, Holder> {

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

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_style_groups, parent, false);
            final Holder holder = new Holder(view);
            //noinspection ConstantConditions
            holder.mCheckableButton.setOnClickListener(v -> onItemCheckChanged(holder));
            return holder;
        }

        void onItemCheckChanged(@NonNull final Holder holder) {
            final int position = holder.getBindingAdapterPosition();
            final StyleViewModel.WrappedGroup wrappedGroup = getItem(position);
            final boolean newStatus = !wrappedGroup.isPresent();
            wrappedGroup.setPresent(newStatus);
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(newStatus);
            notifyItemChanged(position);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final StyleViewModel.WrappedGroup wrappedGroup = getItem(position);

            holder.nameView.setText(wrappedGroup.getGroup().getLabel(getContext()));
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(wrappedGroup.isPresent());
        }
    }

}
