/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.MapDBKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

public class StyleViewModel
        extends ViewModel {

    private static final String TAG = "StyleViewModel";

    /** boolean. Flag indicating we're editing the global style settings. */
    public static final String BKEY_GLOBAL_STYLE = TAG + ":global";

    private final MutableLiveData<String> onDataStoreModified = new MutableLiveData<>();
    private final MutableLiveData<String> onNameNotUnique = new MutableLiveData<>();

    private StyleDataStore styleDataStore;
    private StylesHelper stylesHelper;

    private String templateUuid;

    /**
     * The style we're editing.
     * <p>
     * A {@link UserStyle} or {@link GlobalStyle},
     * but <strong>never</strong> a {@link BuiltinStyle}
     */
    private WritableStyle style;

    @Nullable
    private List<WrappedBookLevelField> wrappedBookLevelFields;
    @Nullable
    private List<WrappedGroup> wrappedGroups;
    private boolean isDefaultStyle;

    @DrawableRes
    static int getIconResId(@NonNull final Sort sort) {
        final int iconResId;
        switch (sort) {
            case Unsorted:
                iconResId = R.drawable.sort_unsorted;
                break;
            case Asc:
                iconResId = R.drawable.sort_ascending;
                break;
            case Desc:
                iconResId = R.drawable.sort_descending;
                break;
            default:
                throw new IllegalArgumentException(sort.toString());
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
                throw new IllegalArgumentException(sort.toString());
        }
        return labelResId;
    }

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

            isDefaultStyle = args.getBoolean(BKEY_GLOBAL_STYLE, false);
            if (isDefaultStyle) {
                style = (WritableStyle) stylesHelper.getGlobalStyle();

            } else {
                // We MUST have a style
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
                    || dbStyle.getType() == Style.Type.Builtin) {
                    // We're cloning a style. If it's a built-in Style, we force cloning.
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

            styleDataStore = new StyleDataStore(style, onDataStoreModified);
        }
    }

    boolean isDefaultStyle() {
        return isDefaultStyle;
    }

    /**
     * WARNING: do not use the style to build summaries for the current fragment.
     * Those current style settings will not have been saved back to the Style yet.
     *
     * @return style
     */
    @NonNull
    Style getStyle() {
        return style;
    }

    @NonNull
    StyleDataStore getStyleDataStore() {
        return styleDataStore;
    }

    /**
     * Get the template UUID this style edit was based on  for the Activity result.
     * <p>
     * <strong>DO NOT use</strong> for {@link Style.Type#Global}.
     *
     * @return uuid
     */
    @NonNull
    String getTemplateUuid() {
        return Objects.requireNonNull(templateUuid, "templateUuid");
    }


    /**
     * The list of groups. This is the combination of:
     * <ol>
     *     <li>The groups already in the Style; initially with {@code present = true}</li>
     *     <li>All other groups; initially with {@code present = false}</li>
     * </ol>
     * The user will be able to order them, and adding or removing them from the style.
     *
     * @return groups
     */
    @NonNull
    List<WrappedGroup> getGroups() {
        if (wrappedGroups == null) {
            // Build an array list with the groups already present in the style
            wrappedGroups = style
                    .getGroupList()
                    .stream()
                    .map(group -> new WrappedGroup(group, true))
                    .collect(Collectors.toList());

            // Get all other groups and add any missing ones to the list
            // so the user can add them if wanted.
            BooklistGroup.getAllGroups(style)
                         .stream()
                         .filter(group -> !style.hasGroup(group.getId()))
                         .forEach(group -> wrappedGroups.add(new WrappedGroup(group, false)));
        }
        return wrappedGroups;
    }

    /**
     * Update the style with the selected groups.
     */
    void updateGroups() {
        //noinspection DataFlowIssue
        style.setGroupList(wrappedGroups.stream()
                                        .filter(WrappedGroup::isPresent)
                                        .map(WrappedGroup::getGroup)
                                        .collect(Collectors.toList()));
        styleDataStore.setModified(StyleDataStore.PK_GROUPS);
    }

    /**
     * Get a csv list of all groups present in the Style.
     *
     * @param context Current context
     *
     * @return csv list
     */
    @NonNull
    String getGroupsSummary(@NonNull final Context context) {
        return style.getGroupList()
                    .stream()
                    .map(element -> element.getLabel(context))
                    .collect(Collectors.joining(", "));
    }

    /**
     * The list of fields which can be displayed on the book level.
     * This is for the purpose of ordering and independent from the fields actually displayed.
     * <p>
     * The user will be able to order them, and and set the ordering for each.
     *
     * @return list
     */
    @NonNull
    List<WrappedBookLevelField> getBookLevelFieldsSorting() {
        if (wrappedBookLevelFields == null) {
            wrappedBookLevelFields = style
                    .getBookLevelFieldsOrderBy()
                    .entrySet()
                    .stream()
                    .map(e -> new WrappedBookLevelField(
                            e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }
        return wrappedBookLevelFields;
    }

    /**
     * Update the style with the selected book level fields.
     */
    void updateBookLevelFieldsSorting() {
        //noinspection DataFlowIssue
        final Map<String, Sort> fields = wrappedBookLevelFields
                .stream().collect(Collectors.toMap(
                        WrappedBookLevelField::getDbKey,
                        WrappedBookLevelField::getSort,
                        (existingKey, replacement) -> {
                            throw new IllegalArgumentException("keys should already be unique");
                        },
                        LinkedHashMap::new));
        style.setBookLevelFieldsOrderBy(fields);
        styleDataStore.setModified(StyleDataStore.PSK_LIST_BOOK_LEVEL_SORTING);
    }

    /**
     * Get a csv list of all book level fields present in the Style,
     * together with the ordering (asc/desc).
     * Unordered fields are not listed.
     *
     * @param context Current context
     *
     * @return csv list
     */
    @NonNull
    String getBookLevelFieldsSortingSummary(@NonNull final Context context) {
        return style.getBookLevelFieldsOrderBy()
                    .entrySet()
                    .stream()
                    .filter(field -> field.getValue() != Sort.Unsorted)
                    .map(field -> context.getString(R.string.a_b,
                                                    MapDBKey.getLabel(context, field.getKey()),
                                                    field.getValue().getSymbol()))
                    .collect(Collectors.joining(", "));
    }


    /**
     * Get a csv list of all book level fields which will be visible.
     * <p>
     * <strong>READ ONLY, modification will be lost</strong>
     *
     * @param context Current context
     *
     * @return csv list
     */
    @NonNull
    String getBookLevelFieldsVisibilitySummary(@NonNull final Context context) {
        final String labels = style
                .getFieldVisibilityKeys(FieldVisibility.Screen.List, false)
                .stream()
                .map(key -> MapDBKey.getLabel(context, key))
                .sorted()
                .collect(Collectors.joining(", "));

        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return labels;
        }
    }


    /**
     * Get the groups from the style which support sorting.
     * <p>
     * These are meant to be displayed at the top of the list (a header)
     * to show the user these <strong>come first in the sorting process</strong>.
     * <p>
     * <strong>READ ONLY, modification will be lost</strong>
     *
     * @return NEW list
     */
    @NonNull
    List<WrappedBookLevelField> getGroupSortingFields() {
        return style
                .getGroupList()
                .stream()
                // only show the groups that are sorted
                .flatMap(group -> group.getGroupDomainExpressions().stream())
                .filter(domainExpression -> domainExpression.getSort() != Sort.Unsorted)
                // We can get duplicate names;
                // e.g. "Year Read" and "Month Read" both use "Read" as the name.
                // So we use a LinkedHashMap (preserving the order!) to prevent duplicates
                .collect(Collectors.toMap(
                        domainExpression -> domainExpression.getDomain().getName(),
                        DomainExpression::getSort,
                        (existingKey, replacement) -> existingKey,
                        LinkedHashMap::new))
                .entrySet()
                .stream()
                // Now convert the map back to a list
                .map(entry -> new WrappedBookLevelField(
                        entry.getKey(),
                        entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * It IS legal to use the same name as a builtin Style.
     * It is NOT legal to use the same name as another {@link UserStyle}.
     * <p>
     * Example: clone a built-in style using the same name,
     * and set the original builtin to 'not preferred' is fine.
     *
     * @param context Current context
     *
     * @return flag
     */
    private boolean isNameUnique(@NonNull final Context context) {
        // always ... flw
        if (style instanceof GlobalStyle) {
            return true;
        }

        final long id = style.getId();
        final String name = ((UserStyle) style).getName();

        if (name.isEmpty()) {
            onNameNotUnique.setValue(context.getString(R.string.vldt_non_blank_required_for_x,
                                                       context.getString(R.string.lbl_name)));
            return false;
        }

        final boolean exists = stylesHelper.getStyles(true)
                                           .stream()
                                           .filter(s -> s.getType() == Style.Type.User)
                                           // skip THIS style obviously
                                           .filter(s -> s.getId() != id)
                                           .anyMatch(s -> ((UserStyle) s).getName().equals(name));

        if (exists) {
            onNameNotUnique.setValue(context.getString(R.string.warning_x_already_exists,
                                                       context.getString(R.string.quoted, name)));
            return false;
        }

        return true;
    }

    @NonNull
    LiveData<String> onNameNotUnique() {
        return onNameNotUnique;
    }

    @NonNull
    LiveData<String> onDataStoreModified() {
        return onDataStoreModified;
    }

    /**
     * Called when the user leaves the fragment. Save any updates needed.
     *
     * @param context Current context
     *
     * @return status
     */
    @NonNull
    Saved insertOrUpdateStyle(@NonNull final Context context) {
        if (!styleDataStore.isModified()) {
            return new Saved(true, false);
        }

        if (!isNameUnique(context)) {
            return new Saved(false, true);
        }

        final boolean success = stylesHelper.insertOrUpdate(context, style);
        return new Saved(success, true);
    }

    // temporary until the db layer is updated... almost certain we'll regret doing it this way...
    static class Saved {
        private final boolean success;

        private final boolean modified;

        Saved(final boolean success,
              final boolean modified) {
            this.success = success;
            this.modified = modified;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isModified() {
            return modified;
        }
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
    static class WrappedBookLevelField {

        @NonNull
        private final String dbKey;

        @NonNull
        private Sort sort;

        WrappedBookLevelField(@NonNull final String dbKey,
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
