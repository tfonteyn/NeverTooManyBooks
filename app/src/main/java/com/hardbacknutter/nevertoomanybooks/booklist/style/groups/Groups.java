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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;

/**
 * Encapsulate the list of {@code BooklistGroup} with backend storage in a preference,
 * and all related data/logic.
 * <p>
 * A {@code List<Integer>} is stored as a CSV String.
 * <p>
 * No equivalent Preference widget
 * <p>
 * A Set or a List is always represented by a {@code Set<String>} in the SharedPreferences
 * due to limitations of {@link androidx.preference.ListPreference}
 * and {@link androidx.preference.MultiSelectListPreference}
 * <p>
 * All of them are written as a CSV String to preserve the order.
 *
 * @see PIntList
 */
public class Groups
        implements PPref<List<Integer>>, PIntList {

    /** Style group preferences. */
    public static final String PK_STYLE_GROUPS = "style.booklist.groups";

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final ListStyle style;
    /** key for the Preference. */
    @NonNull
    private final String key;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final ArrayList<Integer> defaultValue;
    /** Flag indicating we should use the persistence store, or use {@link #nonPersistedValue}. */
    private final boolean persisted;
    /** in memory value used for non-persistence situations. */
    @NonNull
    private final ArrayList<Integer> nonPersistedValue;
    /**
     * All groups; <strong>ordered</strong>.
     * Reminder: the underlying pref is only storing the id.
     */
    private final Map<Integer, BooklistGroup> groupMap = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    public Groups(final boolean isPersistent,
                  @NonNull final ListStyle style) {
        persisted = isPersistent;
        this.style = style;
        key = PK_STYLE_GROUPS;
        defaultValue = new ArrayList<>();
        nonPersistedValue = new ArrayList<>();
        // initial load of the groups
        initGroupMap(getValue());
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     * @param that         to copy from
     */
    public Groups(final boolean isPersistent,
                  @NonNull final ListStyle style,
                  @NonNull final Groups that) {
        persisted = isPersistent;
        this.style = style;
        key = that.key;
        defaultValue = new ArrayList<>(that.defaultValue);
        nonPersistedValue = new ArrayList<>(that.nonPersistedValue);

        if (persisted) {
            set(that.getValue());
        }
    }

    private void initGroupMap(@Nullable final Collection<Integer> value) {
        groupMap.clear();
        if (value != null) {
            value.forEach(id -> groupMap
                    .put(id, BooklistGroup.newInstance(id, persisted, style)));
        }
    }

    /**
     * Get all groups assigned to this style.
     *
     * @return an immutable List
     */
    @NonNull
    public List<BooklistGroup> getGroupList() {
        return List.copyOf(groupMap.values());
    }

    /**
     * Check if the given group is present, using the given group id.
     *
     * @param id group id
     *
     * @return {@code true} if present
     */
    public boolean contains(@BooklistGroup.Id final int id) {
        return groupMap.containsKey(id);
    }

    /**
     * Get the group for the given id.
     *
     * @param id to get
     *
     * @return group
     *
     * @throws NullPointerException on bug
     */
    @NonNull
    public BooklistGroup getGroupByIdOrCrash(final int id) {
        /* Dev note: Leaving the below for historical purposes; fixed now.
         *
         * we want this call to ALWAYS return a valid group.
         * We had (have?) a bug in the past:
         * <p>
         * at BooklistStyle.getGroupById(BooklistStyle.java:1152)
         * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:247)
         * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:96)
         * <p>
         * the STYLE was the wrong one...
         * 2020-09-11: java.lang.IllegalArgumentException: Group was NULL: id=14
         * 14 is READ_YEAR
         * but the style dumped was "Books - Author, Series"
         * so it's the STYLE itself which was wrong...
         * Seems 'get' -> existing cursor, with link to builder with link to style
         * while elsewhere we already have a new builder/style.
         */

        // note the use of a Supplier
        return Objects.requireNonNull(groupMap.get(id), ()
                -> "Group was NULL: id=" + id + ", " + this);
    }

    /**
     * Get the group for the given id.
     *
     * @param id to get
     *
     * @return Optional with the group
     */
    @NonNull
    public Optional<BooklistGroup> getGroupById(@BooklistGroup.Id final int id) {
        final BooklistGroup booklistGroup = groupMap.get(id);
        if (booklistGroup != null) {
            return Optional.of(booklistGroup);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the group at the given level.
     *
     * @param level to get
     *
     * @return group
     */
    @NonNull
    public BooklistGroup getGroupByLevel(@IntRange(from = 1) final int level) {
        // can throw IndexOutOfBoundsException only if we have a bug passing an illegal level.
        return (BooklistGroup) groupMap.values().toArray()[level - 1];
    }

    /**
     * Get the number of groups in this style.
     *
     * @return the number of groups
     */
    public int size() {
        return groupMap.size();
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the in-use group names.
     *
     * @param context Current context
     *
     * @return summary text
     */
    @NonNull
    public String getSummaryText(@NonNull final Context context) {
        return groupMap.values().stream()
                       .map(element -> element.getLabel(context))
                       .collect(Collectors.joining(", "));
    }

    @NonNull
    @Override
    public String getKey() {
        return key;
    }

    @NonNull
    @Override
    public ArrayList<Integer> getValue() {
        if (persisted) {
            return style.getPersistenceLayer().getStringedIntList(key).orElse(defaultValue);
        } else {
            return nonPersistedValue;
        }
    }

    @Override
    public void set(@Nullable final List<Integer> value) {
        if (persisted) {
            style.getPersistenceLayer().setStringedIntList(key, value);
        } else {
            nonPersistedValue.clear();
            if (value != null) {
                nonPersistedValue.addAll(value);
            }
        }

        // init the individual groups AFTER the value of the Groups is changed
        initGroupMap(value);
    }

    public void clear() {
        groupMap.clear();
        if (persisted) {
            style.getPersistenceLayer().remove(key);
        } else {
            nonPersistedValue.clear();
        }
    }

    /**
     * Add a new group to the end of the list.
     *
     * @param group to add
     */
    public void add(@NonNull final BooklistGroup group) {
        groupMap.put(group.getId(), group);

        if (persisted) {
            final StylePersistenceLayer persistenceLayer = style.getPersistenceLayer();
            final String list = persistenceLayer.getNonGlobalString(key);
            persistenceLayer.setString(key, (list != null ? list + DELIM : "") + group.getId());
        } else {
            nonPersistedValue.add(group.getId());
        }
    }

    /**
     * Remove the given group.
     *
     * @param id of group to remove
     */
    public void remove(@BooklistGroup.Id final int id) {
        groupMap.remove(id);

        if (persisted) {
            final StylePersistenceLayer persistenceLayer = style.getPersistenceLayer();
            final String list = persistenceLayer.getNonGlobalString(key);
            if (list != null && !list.isEmpty()) {
                // create a new list, and copy the elements from the old list
                // except the one to remove
                final List<String> newList = Arrays.stream(list.split(DELIM))
                                                   .filter(e -> !e.equals(String.valueOf(id)))
                                                   .collect(Collectors.toList());
                if (newList.isEmpty()) {
                    persistenceLayer.remove(key);
                } else {
                    persistenceLayer.setString(key, TextUtils.join(DELIM, newList));
                }
            }
        } else {
            nonPersistedValue.remove(id);
        }
    }

    /**
     * Get a flat list with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return list
     */
    @NonNull
    public Collection<PPref<?>> getRawPreferences() {
        final Collection<PPref<?>> list = new ArrayList<>();
        // the actual groups which is a list of integers => the group id's
        list.add(this);
        // flatten each group specific preferences
        getGroupList().stream().map(BooklistGroup::getRawPreferences).forEach(list::addAll);
        return list;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Groups groups = (Groups) o;
        // mPersisted/mStyle is NOT part of the values to compare!
        return key.equals(groups.key)
               && nonPersistedValue.equals(groups.nonPersistedValue)
               && defaultValue.equals(groups.defaultValue)

               && Objects.equals(groupMap, groups.groupMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, nonPersistedValue, defaultValue, groupMap);
    }

    @Override
    @NonNull
    public String toString() {
        return "Groups{"
               + "key=`" + key + '`'
               + ", defaultValue=`" + defaultValue + '`'
               + ", persisted=" + persisted
               + ", nonPersistedValue=`" + nonPersistedValue + '`'
               + ", groupMap=" + groupMap
               + '}';
    }
}
