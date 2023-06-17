/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

public class StyleViewModel
        extends ViewModel {

    private final MutableLiveData<Void> onModified = new MutableLiveData<>();
    private String templateUuid;
    /** The style we're editing. */
    private UserStyle style;
    /** The list of groups with a boolean flag for when the user is editing the groups. */
    @Nullable
    private List<WrappedGroup> wrappedGroupList;
    @Nullable
    private StyleDataStore styleDataStore;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (style == null) {
            final String uuid = SanityCheck.requireValue(args.getString(Style.BKEY_UUID),
                                                         Style.BKEY_UUID);
            // ALWAYS pass the original style uuid back.
            templateUuid = uuid;

            final Style dbStyle = ServiceLocator.getInstance().getStyles()
                                                .getStyle(uuid)
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                        "uuid not found: " + uuid));

            @EditStyleContract.EditAction
            final int action = args.getInt(EditStyleContract.BKEY_ACTION,
                                           EditStyleContract.ACTION_EDIT);

            if (action == EditStyleContract.ACTION_CLONE || !dbStyle.isUserDefined()) {
                style = dbStyle.clone(context);
            } else {
                style = (UserStyle) dbStyle;
            }

            // Only set if true, don't overwrite
            if (args.getBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED)) {
                style.setPreferred(true);
            }

            styleDataStore = new StyleDataStore(context, style, onModified);
        }
    }

    @NonNull
    MutableLiveData<Void> onModified() {
        return onModified;
    }

    @NonNull
    UserStyle getStyle() {
        return style;
    }

    @Nullable
    StyleDataStore getStyleDataStore() {
        return styleDataStore;
    }

    @NonNull
    String getTemplateUuid() {
        return Objects.requireNonNull(templateUuid, "templateUuid");
    }

    /**
     * Called when the user leaves the fragment. Save any updates needed.
     *
     * @return {@code true} if the style was modified
     */
    boolean updateOrInsertStyle() {
        //noinspection DataFlowIssue
        if (styleDataStore.isModified()) {
            ServiceLocator.getInstance().getStyles().updateOrInsert(style);
            return true;
        }
        return false;
    }

    @NonNull
    List<WrappedGroup> createWrappedGroupList() {
        // Build an array list with the groups already present in the style
        wrappedGroupList = style
                .getGroupList()
                .stream()
                .map(group -> new WrappedGroup(group, true))
                .collect(Collectors.toList());

        // Get all other groups and add any missing ones to the list so the user can
        // add them if wanted.
        BooklistGroup.getAllGroups(style)
                     .stream()
                     .filter(group -> !style.hasGroup(group.getId()))
                     .forEach(group -> wrappedGroupList.add(new WrappedGroup(group, false)));

        return wrappedGroupList;
    }

    boolean hasGroupsSelected() {
        //noinspection DataFlowIssue
        return wrappedGroupList.stream().anyMatch(WrappedGroup::isPresent);
    }

    /**
     * Collect the user selected groups, and update the style.
     */
    void updateStyleGroups() {
        //noinspection DataFlowIssue
        style.setGroupList(wrappedGroupList.stream()
                                           .filter(WrappedGroup::isPresent)
                                           .map(WrappedGroup::getGroup)
                                           .collect(Collectors.toList()));
        //noinspection DataFlowIssue
        styleDataStore.setModified();
    }

    /**
     * Wraps a {@link BooklistGroup} and a 'present' flag.
     */
    static class WrappedGroup {

        /** The actual group. */
        @NonNull
        private final BooklistGroup booklistGroup;

        /** Whether this group is present in the style. */
        private boolean present;

        /**
         * Constructor.
         *
         * @param group   to wrap
         * @param present flag
         */
        WrappedGroup(@NonNull final BooklistGroup group,
                     final boolean present) {
            booklistGroup = group;
            this.present = present;
        }

        @NonNull
        public BooklistGroup getGroup() {
            return booklistGroup;
        }

        public boolean isPresent() {
            return present;
        }

        public void setPresent(final boolean present) {
            this.present = present;
        }
    }
}
