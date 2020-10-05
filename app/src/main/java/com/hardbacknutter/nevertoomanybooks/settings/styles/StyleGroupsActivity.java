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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit the groups associated with a style (include/exclude + move up/down).
 * <p>
 * <strong>Note:</strong> changes are saved when this activity is quit, see {@link #onBackPressed}.
 */
public class StyleGroupsActivity
        extends BaseActivity {

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    private StyleGroupsModel mModel;

    @Override
    protected void onSetContentView() {
        setContentView(R.layout.activity_edit_style_groups);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(StyleGroupsModel.class);
        Objects.requireNonNull(getIntent().getExtras(), ErrorMsg.NULL_EXTRAS);
        mModel.init(this, getIntent().getExtras());

        // The View for the list.
        final RecyclerView listView = findViewById(R.id.groupList);
        listView.addItemDecoration(new DividerItemDecoration(this, RecyclerView.VERTICAL));
        listView.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        final GroupWrapperListAdapter listAdapter =
                new GroupWrapperListAdapter(this, mModel.getList(),
                                            vh -> mItemTouchHelper.startDrag(vh));

        listView.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(listView);

        //noinspection ConstantConditions
        getSupportActionBar().setSubtitle(getString(R.string.name_colon_value,
                                                    getString(R.string.pg_style_groups),
                                                    mModel.getStyle().getLabel(this)));

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_booklist_style_groups, null);
        }
    }

    /**
     * Saves the new order/settings of the groups to the style.
     */
    @Override
    public void onBackPressed() {
        mModel.updateStyle(this);

        if (mModel.getStyle().getGroups().size() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.pg_style_groups)
                    .setMessage(R.string.warning_select_at_least_1_group)
                    // cancel button, or cancel dialog
                    .setNegativeButton(R.string.action_edit, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_discard, (d, w) -> finish())
                    .create()
                    .show();
        } else {
            final Intent resultData = new Intent()
                    .putExtra(BooklistStyle.BKEY_STYLE, mModel.getStyle());
            setResult(Activity.RESULT_OK, resultData);
            super.onBackPressed();
        }
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
            extends RecyclerViewAdapterBase<StyleGroupsModel.GroupWrapper, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of groups (in GroupWrapper)
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        GroupWrapperListAdapter(@NonNull final Context context,
                                @NonNull final List<StyleGroupsModel.GroupWrapper> items,
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
            final StyleGroupsModel.GroupWrapper groupWrapper = getItem(position);
            boolean newStatus = !groupWrapper.present;
            groupWrapper.present = newStatus;
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(newStatus);
            notifyItemChanged(position);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final StyleGroupsModel.GroupWrapper groupWrapper = getItem(position);

            holder.nameView.setText(groupWrapper.group.getLabel(getContext()));
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(groupWrapper.present);
        }
    }
}
