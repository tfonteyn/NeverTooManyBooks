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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckedTextView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyleGroupsListActivity.GroupWrapper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Activity to edit the groups associated with a style (include/exclude and/or move up/down)
 *
 * @author Philip Warner
 */
public class BooklistStyleGroupsListActivity extends EditObjectListActivity<GroupWrapper> {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS;

    private static final String TAG = "StyleEditor";
    /** Preferences setup */
    public static final String REQUEST_KEY_STYLE = TAG + ".Style";
    public static final String REQUEST_KEY_SAVE_TO_DATABASE = TAG + ".SaveToDb";
    private static final String BKEY_GROUPS = TAG + ".Groups";

    /** Copy of the style we are editing */
    private BooklistStyle mStyle;
    /** Copy of flag passed by calling activity to indicate changes made here should be saved on exit */
    private boolean mSaveToDb = true;

    /**
     * Constructor
     */
    public BooklistStyleGroupsListActivity() {
        super(BKEY_GROUPS, R.layout.booklist_style_edit_group_list, R.layout.booklist_style_edit_row);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            // Get the intent and get the style and other settings
            Intent intent = this.getIntent();
            mStyle = (BooklistStyle) intent.getSerializableExtra(REQUEST_KEY_STYLE);

            if (intent.hasExtra(REQUEST_KEY_SAVE_TO_DATABASE)) {
                mSaveToDb = intent.getBooleanExtra(REQUEST_KEY_SAVE_TO_DATABASE, true);
            }

            /* Indicated this activity was called without an existing style */
            if (mStyle == null) {
                mStyle = new BooklistStyle("");
            }

            // Build an array list with the groups from the style, and record that they are present in mGroups.
            ArrayList<GroupWrapper> groups = new ArrayList<>();
            for (BooklistGroup g : mStyle) {
                groups.add(new GroupWrapper(g, true));
            }

            // Get all other groups and add any missing ones to the list
            for (BooklistGroup g : BooklistGroup.getAllGroups()) {
                if (!mStyle.hasKind(g.kind)) {
                    groups.add(new GroupWrapper(g, false));
                }
            }

            // Store the full list in the intent
            intent.putExtra(BKEY_GROUPS, groups);

            // Init the subclass now it has the array it expects
            super.onCreate(savedInstanceState);
            this.setTitle(getString(R.string.groupings) + ": " + mStyle.getDisplayName());

            if (savedInstanceState == null) {
                HintManager.displayHint(this, R.string.hint_booklist_style_groups, null);
            }

        } catch (Exception e) {
            Logger.error(e);
        }
    }

    @Override
    protected void onAdd(@NonNull final View view) {
        throw new UnsupportedOperationException("Unexpected call to 'onAdd'");
    }

    /**
     * Set up the view for a passed wrapper.
     */
    @Override
    protected void onSetupView(@NonNull final View target, @NonNull final GroupWrapper wrapper) {
        Holder holder = ViewTagger.getTag(target, R.id.TAG_HOLDER);
        if (holder == null) {
            // New view, so build the Holder
            holder = new Holder();
            holder.name = target.findViewById(R.id.name);
            holder.present = target.findViewById(R.id.present);
            // Tag the parts that need it
            ViewTagger.setTag(target, R.id.TAG_HOLDER, holder);
            ViewTagger.setTag(holder.present, R.id.TAG_HOLDER, holder);

            // Handle a click on the CheckedTextView
            holder.present.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(@NonNull View v) {
                    Holder h = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);
                    boolean newStatus = !h.wrapper.present;
                    h.wrapper.present = newStatus;
                    h.present.setChecked(newStatus);
                }
            });
        }
        // Setup the variant fields in the holder
        holder.wrapper = wrapper;
        holder.name.setText(wrapper.group.getName());

        holder.present.setChecked(holder.wrapper.present);
    }

    /**
     * Save the style in the resulting Intent
     */
    @Override
    protected boolean onSave(@NonNull final Intent intent) {
        // Save the properties of this style
        Properties props = mStyle.getProperties();
        // Loop through ALL groups
        for (GroupWrapper wrapper : mList) {
            // Remove it from style
            mStyle.removeGroup(wrapper.group.kind);
            // Add it back, if required.
            // Add then move ensures order will also match
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }
        // Apply any saved properties.
        mStyle.setProperties(props);

        // Store in resulting Intent
        intent.putExtra(REQUEST_KEY_STYLE, mStyle);

        // Save to DB if necessary
        if (mSaveToDb) {
            mDb.insertOrUpdateBooklistStyle(mStyle);
        }

        return true;
    }

    /**
     * We build a list of GroupWrappers which is passed to the underlying class for editing.
     * The wrapper includes extra details needed by this activity.
     *
     * @author Philip Warner
     */
    public static class GroupWrapper implements Serializable {
        private static final long serialVersionUID = 3108094089675884238L;
        /** The actual group */
        @NonNull
        final BooklistGroup group;
        /** Whether this groups is present in the style */
        boolean present;

        /** Constructor */
        GroupWrapper(@NonNull final BooklistGroup group, final boolean present) {
            this.group = group;
            this.present = present;
        }
    }

    /**
     * Holder pattern for each row.
     *
     * @author Philip Warner
     */
    private class Holder {
        GroupWrapper wrapper;
        TextView name;
        CheckedTextView present;
    }
}
