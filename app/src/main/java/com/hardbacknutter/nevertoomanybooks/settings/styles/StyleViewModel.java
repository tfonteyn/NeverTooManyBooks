/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;

public class StyleViewModel
        extends ViewModel
        implements ResultIntentOwner {

    public static final int BKEY_ACTION_CLONE = 0;
    public static final int BKEY_ACTION_EDIT = 1;

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();
    /** The style we're editing. */
    private UserStyle mStyle;
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

            final String uuid = Objects.requireNonNull(args.getString(ListStyle.BKEY_STYLE_UUID));

            if (uuid.isEmpty()) {
                // we're doing the global preferences, create a placeholder style with an empty uuid
                // and let it use the standard SharedPreferences
                mStyle = UserStyle.createGlobal(context);

            } else {
                // ALWAYS pass the original style uuid back.
                mResultIntent.putExtra(EditStyleContract.BKEY_TEMPLATE_UUID, uuid);

                final ListStyle style = ServiceLocator.getInstance().getStyles()
                                                      .getStyle(context, uuid);
                Objects.requireNonNull(style, "uuid not found: " + uuid);

                @EditAction
                final int action = args.getInt(EditStyleContract.BKEY_ACTION, BKEY_ACTION_EDIT);

                if (action == BKEY_ACTION_CLONE || style instanceof BuiltinStyle) {
                    mStyle = style.clone(context);
                } else {
                    mStyle = (UserStyle) style;
                }

                if (args.getBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED)) {
                    mStyle.setPreferred(true);
                }

                // always pass a non-global style uuid back; whether existing or new.
                // so even if the user makes no changes, we still send it back!
                mResultIntent.putExtra(ListStyle.BKEY_STYLE_UUID, mStyle.getUuid());

            }
        }
    }

    @NonNull
    UserStyle getStyle() {
        return mStyle;
    }

    void setModified() {
        mResultIntent.putExtra(EditStyleContract.BKEY_STYLE_MODIFIED, true);
    }

    @NonNull
    @Override
    public Intent getResultIntent() {
        return mResultIntent;
    }

    @NonNull
    ArrayList<WrappedGroup> createWrappedGroupList() {
        final Groups styleGroups = mStyle.getGroups();

        // Build an array list with the groups already present in the style
        mWrappedGroupList = new ArrayList<>(styleGroups.size());
        for (final BooklistGroup group : styleGroups.getGroupList()) {
            mWrappedGroupList.add(new WrappedGroup(group, true));
        }
        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        for (final BooklistGroup group : BooklistGroup.getAllGroups(mStyle)) {
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
     * Called when leaving the fragment. Save any updates needed.
     */
    void updateOrInsertStyle() {
        if (mResultIntent.getBooleanExtra(EditStyleContract.BKEY_STYLE_MODIFIED, false)) {
            ServiceLocator.getInstance().getStyles().updateOrInsert(mStyle);
        }
    }

    @IntDef({BKEY_ACTION_CLONE, BKEY_ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EditAction {

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
