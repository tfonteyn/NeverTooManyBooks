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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

public class StyleViewModel
        extends ViewModel {

    private static final String TAG = "StyleViewModel";

    public static final String BKEY_GLOBAL_STYLE = TAG + ":global";

    private final MutableLiveData<Void> onModified = new MutableLiveData<>();
    @NonNull
    private final List<WrappedBookLevelColumn> wrappedBookLevelColumnList = new ArrayList<>();
    private String templateUuid;
    /** The style we're editing. */
    private WritableStyle style;
    /** The list of groups with a boolean flag for when the user is editing the groups. */
    @Nullable
    private List<WrappedGroup> wrappedGroupList;

    @Nullable
    private StyleDataStore styleDataStore;

    @DrawableRes
    static int getIconResId(@NonNull final Sort sort) {
        final int iconResId;
        switch (sort) {
            case Unsorted:
                iconResId = R.drawable.ic_baseline_sort_unsorted;
                break;
            case Asc:
                iconResId = R.drawable.ic_baseline_sort_ascending;
                break;
            case Desc:
                iconResId = R.drawable.ic_baseline_sort_descending;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return iconResId;
    }

    @StringRes
    private static int getStringResId(@NonNull final Sort sort) {
        final int labelResId;
        switch (sort) {
            case Unsorted:
                labelResId = R.string.lbl_sort_unsorted;
                break;
            case Asc:
                labelResId = R.string.lbl_sort_ascending;
                break;
            case Desc:
                labelResId = R.string.lbl_sort_descending;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return labelResId;
    }

    private StylesHelper stylesHelper;

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@NonNull final Context context,
              @NonNull final Bundle args) {
        if (style == null) {
            stylesHelper = ServiceLocator.getInstance().getStyles();

            if (args.getBoolean(BKEY_GLOBAL_STYLE, false)) {
                style = (WritableStyle) stylesHelper.getGlobalStyle();

            } else {
                final String uuid = SanityCheck.requireValue(args.getString(Style.BKEY_UUID),
                                                             Style.BKEY_UUID);
                // ALWAYS pass the original style uuid back.
                templateUuid = uuid;

                final Style dbStyle = stylesHelper.getStyle(uuid)
                                                  .orElseThrow(() -> new IllegalArgumentException(
                                                          "uuid not found: " + uuid));

                @EditStyleContract.EditAction
                final int action = args.getInt(EditStyleContract.BKEY_ACTION,
                                               EditStyleContract.ACTION_EDIT);

                if (action == EditStyleContract.ACTION_CLONE
                    || dbStyle.getType() == StyleType.Builtin) {
                    // We're cloning a style. If the style is Builtin, we force cloning.
                    style = dbStyle.clone(context);
                } else {
                    // just edit the style.
                    style = (WritableStyle) dbStyle;
                }

                // Only set if true, don't overwrite
                if (args.getBoolean(EditStyleContract.BKEY_SET_AS_PREFERRED)) {
                    style.setPreferred(true);
                }
            }

            styleDataStore = new StyleDataStore(style, onModified);
        }
    }

    @NonNull
    MutableLiveData<Void> onModified() {
        return onModified;
    }

    @NonNull
    WritableStyle getStyle() {
        return style;
    }

    @Nullable
    StyleDataStore getStyleDataStore() {
        return styleDataStore;
    }

    /**
     * Get the template UUID this style edit was based on  for the Activity result.
     * <p>
     * <strong>DO NOT use</strong> for {@link StyleType#Global}.
     *
     * @return uuid
     */
    @NonNull
    String getTemplateUuid() {
        return Objects.requireNonNull(templateUuid, "templateUuid");
    }

    /**
     * Called when the user leaves the fragment. Save any updates needed.
     *
     * @param context Current context
     *
     * @return {@code true} if the style was modified
     */
    boolean updateOrInsertStyle(@NonNull final Context context) {
        //noinspection DataFlowIssue
        if (styleDataStore.isModified()) {
            return stylesHelper.updateOrInsert(context, style);
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

    @NonNull
    List<WrappedBookLevelColumn> getWrappedBookLevelColumnList() {
        if (wrappedBookLevelColumnList.isEmpty()) {
            style.getBookLevelFieldsOrderBy().forEach((dbKey, sort) -> wrappedBookLevelColumnList
                    .add(new WrappedBookLevelColumn(dbKey, sort)));
        }
        return wrappedBookLevelColumnList;
    }

    void updateBookLevelColumnList() {
        style.setBookLevelFieldsOrderBy(
                wrappedBookLevelColumnList
                        .stream()
                        .collect(Collectors.toMap(WrappedBookLevelColumn::getDbKey,
                                                  WrappedBookLevelColumn::getSort,
                                                  (existingKey, replacement) -> {
                                                      throw new IllegalArgumentException(
                                                              "keys should already be unique");
                                                  },
                                                  LinkedHashMap::new)));

        //noinspection DataFlowIssue
        styleDataStore.setModified();
    }

    @NonNull
    String getBookLevelSortingPreferenceSummary(@NonNull final Context context) {
        return getWrappedBookLevelColumnList()
                .stream()
                .filter(column -> column.getSort() != Sort.Unsorted)
                .map(column -> context.getString(R.string.a_b,
                                                 column.getLabel(context),
                                                 column.getSort().getSymbol()))
                .collect(Collectors.joining(", "));
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

    /**
     * Wraps a book-level field, a {@link DBKey}, with its {@link Sort} option.
     */
    static class WrappedBookLevelColumn {

        @NonNull
        private final String dbKey;

        @NonNull
        private Sort sort;

        WrappedBookLevelColumn(@NonNull final String dbKey,
                               @NonNull final Sort sort) {
            this.dbKey = dbKey;
            this.sort = sort;
        }

        @NonNull
        public String getDbKey() {
            return dbKey;
        }

        public String getLabel(@NonNull final Context context) {
            return MapDBKey.getLabel(context, dbKey);
        }

        @NonNull
        public Sort getSort() {
            return sort;
        }

        public void setSort(@NonNull final Sort sort) {
            this.sort = sort;
        }
    }
}
