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

package com.eleybourn.bookcatalogue.booklist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.EditBooklistStyleGroupsActivity.GroupWrapper;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * Activity to edit the groups associated with a style (include/exclude and/or move up/down).
 *
 * @author Philip Warner
 */
public class EditBooklistStyleGroupsActivity
        extends EditObjectListActivity<GroupWrapper> {

    /** Preferences setup. */
    public static final String REQUEST_BKEY_STYLE = "Style";
    public static final String REQUEST_BKEY_SAVE_TO_DATABASE = "SaveToDb";

    /** Copy of the style we are editing. */
    private BooklistStyle mStyle;
    /** Copy of flag passed by calling activity to indicate changes made here should be
     *  saved on exit. */
    private boolean mSaveToDb = true;

    /**
     * Constructor.
     */
    public EditBooklistStyleGroupsActivity() {
        super(R.layout.booklist_style_edit_group_list, R.layout.row_edit_booklist_style, null);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        // Get the intent and get the style and other settings
        Intent intent = this.getIntent();

        if (intent.hasExtra(REQUEST_BKEY_SAVE_TO_DATABASE)) {
            mSaveToDb = intent.getBooleanExtra(REQUEST_BKEY_SAVE_TO_DATABASE, true);
        }

        mStyle = intent.getParcelableExtra(REQUEST_BKEY_STYLE);
        Objects.requireNonNull(mStyle);

        // Init the subclass now that we have the style
        super.onCreate(savedInstanceState);

        this.setTitle(getString(R.string.name_colon_value,
                                getString(R.string.pg_groupings), mStyle.getDisplayName()));

        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(),
                                    R.string.hint_booklist_style_groups, null);
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Nullable
    @Override
    protected ArrayList<GroupWrapper> getList() {
        // Build an array list with the groups from the style
        ArrayList<GroupWrapper> groupWrappers = new ArrayList<>();
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
     * Called when user clicks the 'Save' button.
     *
     * Adds the style to the resulting data
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt>if activity should exit, <tt>false</tt> to abort exit.
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        //Note: right now, we don't have any updated preferences (other then the groups)
        // Save the properties of this style
        Map<String, PPref> allPreferences = mStyle.getPreferences(true);

        // Loop through ALL groups
        for (GroupWrapper wrapper : mList) {
            // Remove its kind from style
            mStyle.removeGroup(wrapper.group.getKind());
            // If required, add the group back; this also takes care of the order.
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }

        // Apply any saved properties.
        mStyle.updatePreferences(allPreferences);

        // Save to DB if necessary
        if (mSaveToDb) {
            mStyle.save(mDb);
        }

        if (DEBUG_SWITCHES.DUMP_STYLE && BuildConfig.DEBUG) {
            Logger.info(this, "onSave|" + mStyle.toString());
        }
        data.putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle);
        setResult(Activity.RESULT_OK, data);
        return true;
    }

    protected SimpleListAdapter<GroupWrapper> createListAdapter(@LayoutRes final int rowViewId,
                                                                @NonNull final ArrayList<GroupWrapper> list) {
        return new GroupWrapperListAdapter(this, rowViewId, list);
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
        final BooklistGroup group;
        /** Whether this groups is present in the style. */
        boolean present;
        String uuid;

        /** Constructor. */
        GroupWrapper(@NonNull final BooklistGroup group,
                     @NonNull final String uuid,
                     final boolean present) {
            this.group = group;
            this.uuid = uuid;
            this.present = present;
        }

        GroupWrapper(@NonNull final Parcel in) {
            present = in.readByte() != 0;
            uuid = in.readString();
            group = BooklistGroup.newInstance(in.readInt(), uuid);
        }

        /** {@link Parcelable}. */
        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeByte((byte) (present ? 1 : 0));
            dest.writeString(uuid);
            dest.writeInt(group.getKind());
        }

        /** {@link Parcelable}. */
        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        GroupWrapper groupWrapper;
        CheckedTextView checkable;
        TextView name;
    }

    protected class GroupWrapperListAdapter
            extends SimpleListAdapter<GroupWrapper> {

        GroupWrapperListAdapter(@NonNull final Context context,
                                @LayoutRes final int rowViewId,
                                @NonNull final ArrayList<GroupWrapper> items) {
            super(context, rowViewId, items);
        }

        @Override
        public void onGetView(@NonNull final View convertView,
                              @NonNull final GroupWrapper item) {
            Holder holder = ViewTagger.getTag(convertView);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.name = convertView.findViewById(R.id.name);
                holder.checkable = convertView.findViewById(R.id.row_check);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
                ViewTagger.setTag(holder.checkable, holder);

                // Handle a click on the CheckedTextView
                holder.checkable.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(@NonNull View v) {
                        Holder h = ViewTagger.getTagOrThrow(v);
                        boolean newStatus = !h.groupWrapper.present;
                        h.groupWrapper.present = newStatus;
                        h.checkable.setChecked(newStatus);
                        // no need to update the list, item itself is updated
                        //onListChanged();
                    }
                });
            }
            // Setup the variant fields in the holder
            holder.groupWrapper = item;
            holder.name.setText(item.group.getName());
            holder.checkable.setChecked(holder.groupWrapper.present);
        }

        /**
         * delegate to ListView host
         */
        @Override
        public void onListChanged() {
            super.onListChanged();
            EditBooklistStyleGroupsActivity.this.onListChanged();
        }
    }
}
