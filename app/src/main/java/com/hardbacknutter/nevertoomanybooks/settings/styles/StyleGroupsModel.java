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
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Groups;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public class StyleGroupsModel
        extends ViewModel {

    /** the rows. */
    private ArrayList<GroupWrapper> mList;
    /** Copy of the style we are editing. */
    private BooklistStyle mStyle;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        if (mStyle == null) {
            mStyle = args.getParcelable(BooklistStyle.BKEY_STYLE);
            Objects.requireNonNull(mStyle, ErrorMsg.NULL_STYLE);

            final Groups styleGroups = mStyle.getGroups();

            // Build an array list with the groups from the style
            mList = new ArrayList<>(styleGroups.size());
            for (BooklistGroup group : styleGroups.getGroupList()) {
                mList.add(new GroupWrapper(group, true));
            }
            // Get all other groups and add any missing ones to the list so the user can
            // add them if wanted.
            for (BooklistGroup group : BooklistGroup.getAllGroups(context, mStyle)) {
                if (!styleGroups.contains(group.getId())) {
                    mList.add(new GroupWrapper(group, false));
                }
            }
        }
    }

    @NonNull
    public BooklistStyle getStyle() {
        return Objects.requireNonNull(mStyle);
    }

    @NonNull
    public ArrayList<GroupWrapper> getList() {
        return Objects.requireNonNull(mList);
    }

    /**
     * Collect the data from the list, and update the style.
     *
     * @param context Current context
     */
    void updateStyle(@NonNull final Context context) {
        final Map<String, PPref> allPreferences = mStyle.getPreferences(true);

        final Groups styleGroups = mStyle.getGroups();

        // Loop through all groups
        for (GroupWrapper wrapper : mList) {
            // Remove it from the style
            styleGroups.remove(wrapper.group.getId());
            // If required, add the group back; this also takes care of the order.
            if (wrapper.present) {
                styleGroups.add(wrapper.group);
            }
        }

        // Apply any saved properties.
        // For now we don't have any updated preferences other than the groups.
        mStyle.updatePreferences(context, allPreferences);
    }


    /**
     * Wraps a {@link BooklistGroup} and a 'present' flag.
     */
    static class GroupWrapper {

        /** The actual group. */
        @NonNull
        final BooklistGroup group;

        /** Whether this group is present in the style. */
        boolean present;

        /** Constructor. */
        GroupWrapper(@NonNull final BooklistGroup group,
                     final boolean present) {
            this.group = group;
            this.present = present;
        }
    }
}
