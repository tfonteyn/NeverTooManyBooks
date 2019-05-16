/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewAdapterBase;
import com.eleybourn.bookcatalogue.widgets.RecyclerViewViewHolderBase;
import com.eleybourn.bookcatalogue.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.eleybourn.bookcatalogue.widgets.ddsupport.StartDragListener;

/**
 * Activity to edit the groups associated with a style (include/exclude + move up/down).
 * <p>
 * IMPORTANT: changes here are NOT saved automatically but only when this activity is quit.
 * See {@link #onBackPressed} and {@link #saveStyleSettings}
 */
public class StyleGroupsActivity
        extends BaseActivity {

    private static final String BKEY_LIST = "list";

    /** the rows. */
    protected ArrayList<GroupWrapper> mList;

    /** The adapter for the list. */
    protected RecyclerViewAdapterBase mListAdapter;
    /** The View for the list. */
    protected RecyclerView mListView;
    protected LinearLayoutManager mLayoutManager;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /** Copy of the style we are editing. */
    private BooklistStyle mStyle;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_style_edit_group_list;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mStyle = intent.getParcelableExtra(UniqueId.BKEY_STYLE);
        Objects.requireNonNull(mStyle);

        // Get the list
        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (args != null) {
            mList = args.getParcelableArrayList(BKEY_LIST);
        }
        if (mList == null) {
            mList = getList();
        }

        mListView = findViewById(android.R.id.list);
        mLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(mLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, mLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        // setup the adapter
        mListAdapter = new GroupWrapperListAdapter(this, mList,
                                                   vh -> mItemTouchHelper.startDrag(vh));
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);

        ActionBar bar = getSupportActionBar();
        //noinspection ConstantConditions
        bar.setTitle(R.string.title_edit_style);
        bar.setSubtitle(getString(R.string.name_colon_value,
                                  getString(R.string.pg_groupings),
                                  mStyle.getLabel(getResources())));

        if (savedInstanceState == null) {
            HintManager.displayHint(getLayoutInflater(),
                                    R.string.hint_booklist_style_groups, null);
        }
    }

    /**
     * Construct the list by wrapping each group individually.
     */
    @NonNull
    protected ArrayList<GroupWrapper> getList() {
        // Build an array list with the groups from the style
        ArrayList<GroupWrapper> groupWrappers = new ArrayList<>(mStyle.groupCount());
        for (BooklistGroup group : mStyle.getGroups()) {
            groupWrappers.add(new GroupWrapper(group, mStyle.getUuid(), true));
        }

        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        for (BooklistGroup group : BooklistGroup.getAllGroups(mStyle)) {
            if (!mStyle.hasGroupKind(group.getKind())) {
                groupWrappers.add(new GroupWrapper(group, mStyle.getUuid(), false));
            }
        }

        return groupWrappers;
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

    public void saveStyleSettings() {
        Map<String, PPref> allPreferences = mStyle.getPreferences(true);

        // Loop through ALL groups
        for (StyleGroupsActivity.GroupWrapper wrapper : mList) {
            // Remove its kind from style
            mStyle.removeGroup(wrapper.group.getKind());
            // If required, add the group back; this also takes care of the order.
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }

        // Apply any saved properties.
        // Note: right now, we don't have any updated preferences other then the groups.
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
     * The wrapper includes extra details needed by this activity.
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

        /** The actual group. */
        @NonNull
        public final BooklistGroup group;
        @NonNull
        final String uuid;
        /** Whether this groups is present in the style. */
        public boolean present;

        /** Constructor. */
        public GroupWrapper(@NonNull final BooklistGroup group,
                            @NonNull final String uuid,
                            final boolean present) {
            this.group = group;
            this.uuid = uuid;
            this.present = present;
        }

        /** {@link Parcelable}. */
        GroupWrapper(@NonNull final Parcel in) {
            present = in.readByte() != 0;
            //noinspection ConstantConditions
            uuid = in.readString();
            //noinspection ConstantConditions
            group = BooklistGroup.newInstance(in.readInt(), uuid);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeByte((byte) (present ? 1 : 0));
            dest.writeString(uuid);
            dest.writeInt(group.getKind());
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
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

        GroupWrapperListAdapter(@NonNull final Context context,
                                @NonNull final ArrayList<GroupWrapper> items,
                                @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
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

            holder.nameView.setText(groupWrapper.group.getName(getContext().getResources()));

            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(groupWrapper.present);

            holder.mCheckableButton.setOnClickListener(v -> {
                boolean newStatus = !groupWrapper.present;
                groupWrapper.present = newStatus;
                holder.mCheckableButton.setChecked(newStatus);
                notifyItemChanged(position);
            });
        }
    }
}
