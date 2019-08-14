/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit the groups associated with a style (include/exclude + move up/down).
 * <p>
 * IMPORTANT: changes here are NOT saved automatically but only when this activity is quit.
 * See {@link #onBackPressed} and {@link #saveStyleSettings}
 */
public class StyleGroupsActivity
        extends BaseActivity {

    private static final String TAG = "StyleGroupsActivity";

    private static final String BKEY_LIST = TAG + ":list";

    /** the rows. */
    private ArrayList<GroupWrapper> mList;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /** Copy of the style we are editing. */
    private BooklistStyle mStyle;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_style_edit_group_list;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mStyle = Objects.requireNonNull(intent.getParcelableExtra(UniqueId.BKEY_STYLE));

        // Get the list
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mList = args.getParcelableArrayList(BKEY_LIST);
        }
        if (mList == null) {
            mList = getList();
        }

        // The View for the list.
        RecyclerView listView = findViewById(android.R.id.list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(this, layoutManager.getOrientation()));
        listView.setHasFixedSize(true);

        // setup the adapter
        // The adapter for the list.
        RecyclerViewAdapterBase listAdapter =
                new GroupWrapperListAdapter(getLayoutInflater(), mList,
                                            vh -> mItemTouchHelper.startDrag(vh));

        listView.setAdapter(listAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(listView);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_edit_style);
            actionBar.setSubtitle(
                    getString(R.string.name_colon_value,
                              getString(R.string.pg_groupings), mStyle.getLabel(this)));
        }

        if (savedInstanceState == null) {
            TipManager.display(getLayoutInflater(), R.string.tip_booklist_style_groups, null);
        }
    }

    /**
     * Saves the new order/settings of the groups to the style.
     * <p>
     * This is unconditional in line with how Android preferences/settings screens work.
     */
    @Override
    public void onBackPressed() {
        saveStyleSettings();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
            Logger.debug(this, "onBackPressed", mStyle);
        }

        // send the modified style back.
        Intent data = new Intent().putExtra(UniqueId.BKEY_STYLE, (Parcelable) mStyle);
        setResult(Activity.RESULT_OK, data);

        super.onBackPressed();
    }

    /**
     * Construct the list by wrapping each group individually.
     *
     * @return the list
     */
    @NonNull
    private ArrayList<GroupWrapper> getList() {
        // Build an array list with the groups from the style
        ArrayList<GroupWrapper> groupWrappers = new ArrayList<>(mStyle.groupCount());
        for (BooklistGroup group : mStyle.getGroups()) {
            groupWrappers.add(new GroupWrapper(group, true));
        }

        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        for (BooklistGroup group : BooklistGroup.getAllGroups(mStyle.getUuid(),
                                                              mStyle.isUserDefined())) {
            if (!mStyle.hasGroupKind(group.getKind())) {
                groupWrappers.add(new GroupWrapper(group, false));
            }
        }

        return groupWrappers;
    }

    private void saveStyleSettings() {
        Map<String, PPref> allPreferences = mStyle.getPreferences(true);

        // Loop through all groups
        for (StyleGroupsActivity.GroupWrapper wrapper : mList) {
            // Remove its kind from style
            mStyle.removeGroup(wrapper.group.getKind());
            // If required, add the group back; this also takes care of the order.
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }

        // Apply any saved properties.
        // For now we don't have any updated preferences other then the groups.
        mStyle.updatePreferences(allPreferences);
    }

    /**
     * Ensure that the list is saved.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_LIST, mList);
    }

    /**
     * We build a list of GroupWrappers which is passed to the underlying class for editing.
     * The wrapper includes extra details (the 'present' flag) needed by this activity.
     */
    public static class GroupWrapper
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<GroupWrapper> CREATOR =
                new Creator<GroupWrapper>() {
                    @Override
                    public GroupWrapper createFromParcel(@NonNull final Parcel source) {
                        return new GroupWrapper(source);
                    }

                    @Override
                    public GroupWrapper[] newArray(final int size) {
                        return new GroupWrapper[size];
                    }
                };

        private static final long serialVersionUID = 3108094089675884238L;

        /** The actual group. When parceling, we only parcel the kind. */
        @NonNull
        final BooklistGroup group;
        /* Needed to reconstruct after parceling. */
        @NonNull
        final String uuid;
        /* Needed to reconstruct after parceling. */
        final boolean isUserDefinedStyle;

        /** Whether this group is present in the style. */
        boolean present;

        /** Constructor. */
        GroupWrapper(@NonNull final BooklistGroup group,
                     final boolean present) {
            this.group = group;
            this.uuid = group.getUuid();
            this.isUserDefinedStyle = group.isUserDefinedStyle();
            this.present = present;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private GroupWrapper(@NonNull final Parcel in) {
            present = in.readInt() != 0;
            //noinspection ConstantConditions
            uuid = in.readString();
            isUserDefinedStyle = in.readInt() != 0;
            //noinspection ConstantConditions
            group = BooklistGroup.newInstance(in.readInt(), uuid, isUserDefinedStyle);
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(present ? 1 : 0);
            dest.writeString(uuid);
            dest.writeInt(isUserDefinedStyle ? 1 : 0);
            dest.writeInt(group.getKind());
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

    protected static class GroupWrapperListAdapter
            extends RecyclerViewAdapterBase<GroupWrapper, Holder> {

        /**
         * Constructor.
         *
         * @param inflater          LayoutInflater to use
         * @param items             List of groups (in GroupWrapper)
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        GroupWrapperListAdapter(@NonNull final LayoutInflater inflater,
                                @NonNull final ArrayList<GroupWrapper> items,
                                @NonNull final StartDragListener dragStartListener) {
            super(inflater, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                                .inflate(R.layout.row_edit_booklist_style, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            GroupWrapper groupWrapper = getItem(position);

            holder.nameView.setText(groupWrapper.group.getName(getContext()));

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(groupWrapper.present);

            holder.mCheckableButton.setOnClickListener(v -> {
                boolean newStatus = !groupWrapper.present;
                groupWrapper.present = newStatus;
                holder.mCheckableButton.setChecked(newStatus);
                notifyItemChanged(holder.getAdapterPosition());
            });
        }
    }
}
