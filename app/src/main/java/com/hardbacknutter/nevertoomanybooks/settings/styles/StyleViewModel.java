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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Groups;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultDataModel;

public class StyleViewModel
        extends ViewModel
        implements ActivityResultDataModel {

    /** Log tag. */
    private static final String TAG = "StyleViewModel";
    /** The template style (id) the style we're editing is based on. */
    public static final String BKEY_TEMPLATE_ID = TAG + ":templateId";
    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultData = new Intent();
    /** The style we're editing. */
    private BooklistStyle mStyle;
    /** The list of groups with a boolean flag for when the user is editing the groups. */
    @Nullable
    private ArrayList<WrappedGroup> mWrappedGroupList;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (mStyle == null) {

            // The templateId is not actually used here, but it MUST always be passed back.
            final long templateId = args.getLong(BKEY_TEMPLATE_ID);
            if (templateId != 0) {
                mResultData.putExtra(BKEY_TEMPLATE_ID, templateId);
            }

            final BooklistStyle style = args.getParcelable(BooklistStyle.BKEY_STYLE);
            if (style != null) {
                mStyle = style;
                // always pass a non-global style back; whether existing or new.
                // so even if the user makes no changes, we still send it back!
                // If the user does make changes, we'll overwrite it in onSharedPreferenceChanged
                mResultData.putExtra(BooklistStyle.BKEY_STYLE, mStyle);

            } else {
                // we're doing the global preferences, create a placeholder style with an empty uuid
                // and let it use the standard SharedPreferences
                mStyle = new BooklistStyle(context);
            }
        }
    }

    @NonNull
    BooklistStyle getStyle() {
        return mStyle;
    }

    void setModified() {
        mResultData.putExtra(BooklistStyle.BKEY_STYLE_MODIFIED, true);
    }

    @NonNull
    @Override
    public Intent getResultIntent() {
        return mResultData;
    }

    @NonNull
    ArrayList<WrappedGroup> createWrappedGroupList(@NonNull final Context context) {
        final Groups styleGroups = mStyle.getGroups();

        // Build an array list with the groups already present in the style
        mWrappedGroupList = new ArrayList<>(styleGroups.size());
        for (final BooklistGroup group : styleGroups.getGroupList()) {
            mWrappedGroupList.add(new WrappedGroup(group, true));
        }
        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        for (final BooklistGroup group : BooklistGroup.getAllGroups(context, mStyle)) {
            if (!styleGroups.contains(group.getId())) {
                mWrappedGroupList.add(new WrappedGroup(group, false));
            }
        }

        return mWrappedGroupList;
    }

    boolean hasGroupsSelected() {
        Objects.requireNonNull(mWrappedGroupList);

        return mWrappedGroupList.stream().anyMatch(WrappedGroup::isPresent);
    }

    /**
     * Collect the user selected groups, and update the style.
     */
    void updateStyleGroups() {
        Objects.requireNonNull(mWrappedGroupList);

        final Groups styleGroups = mStyle.getGroups();
        styleGroups.clear();
        mWrappedGroupList.stream()
                         .filter(WrappedGroup::isPresent)
                         .map(WrappedGroup::getGroup)
                         .forEach(styleGroups::add);
    }

    /**
     * Wraps a {@link BooklistGroup} and a 'present' flag.
     */
    static class WrappedGroup {

        /** The actual group. */
        @NonNull
        private final BooklistGroup mBooklistGroup;

        /** Whether this group is present in the style. */
        private boolean mIsPresent;

        /**
         * Constructor.
         *
         * @param group     to wrap
         * @param isPresent flag
         */
        WrappedGroup(@NonNull final BooklistGroup group,
                     final boolean isPresent) {
            mBooklistGroup = group;
            mIsPresent = isPresent;
        }

        @NonNull
        public BooklistGroup getGroup() {
            return mBooklistGroup;
        }

        public boolean isPresent() {
            return mIsPresent;
        }

        public void setPresent(final boolean present) {
            mIsPresent = present;
        }
    }
}
