/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
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
        mModel.init(Objects.requireNonNull(getIntent().getExtras(), ErrorMsg.ARGS_MISSING_EXTRAS));

        // The View for the list.
        RecyclerView listView = findViewById(R.id.groupList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(this, layoutManager.getOrientation()));
        listView.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        RecyclerViewAdapterBase listAdapter =
                new GroupWrapperListAdapter(this, mModel.getList(),
                                            vh -> mItemTouchHelper.startDrag(vh));

        listView.setAdapter(listAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(listView);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.lbl_edit_style);
            actionBar.setSubtitle(getString(R.string.name_colon_value,
                                            getString(R.string.pg_style_groups),
                                            mModel.getStyle().getLabel(this)));
        }

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

        if (mModel.getStyle().getGroupCount() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.pg_style_groups)
                    .setMessage(R.string.warning_select_at_least_1_group)
                    // cancel button, or cancel dialog
                    .setNegativeButton(R.string.btn_continue_edit, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_exit, (d, w) -> finish())
                    .create()
                    .show();
        } else {
            Intent resultData = new Intent()
                    .putExtra(BooklistStyle.BKEY_STYLE, mModel.getStyle());
            setResult(Activity.RESULT_OK, resultData);
            super.onBackPressed();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

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

            View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_style_groups, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            StyleGroupsModel.GroupWrapper groupWrapper = getItem(position);

            holder.nameView.setText(groupWrapper.group.getLabel(getContext()));
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(groupWrapper.present);
            holder.mCheckableButton.setOnClickListener(v -> {
                boolean newStatus = !groupWrapper.present;
                groupWrapper.present = newStatus;
                holder.mCheckableButton.setChecked(newStatus);
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        }
    }
}
