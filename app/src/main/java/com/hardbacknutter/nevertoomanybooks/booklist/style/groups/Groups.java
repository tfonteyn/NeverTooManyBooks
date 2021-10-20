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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        implements PPref<ArrayList<Integer>>, PIntList {

    /** Style group preferences. */
    public static final String PK_STYLE_GROUPS = "style.booklist.groups";
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final ListStyle mStyle;
    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final ArrayList<Integer> mDefaultValue;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** in memory value used for non-persistence situations. */
    @NonNull
    private final ArrayList<Integer> mNonPersistedValue;
    /**
     * All groups; <strong>ordered</strong>.
     * Reminder: the underlying pref is only storing the id.
     */
    private final Map<Integer, BooklistGroup> mGroupMap = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param isPersistent flag
     * @param style        Style reference.
     */
    public Groups(final boolean isPersistent,
                  @NonNull final ListStyle style) {
        mPersisted = isPersistent;
        mStyle = style;
        mKey = PK_STYLE_GROUPS;
        mDefaultValue = new ArrayList<>();
        mNonPersistedValue = new ArrayList<>();
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
        mPersisted = isPersistent;
        mStyle = style;
        mKey = that.mKey;
        mDefaultValue = new ArrayList<>(that.mDefaultValue);
        mNonPersistedValue = new ArrayList<>(that.mNonPersistedValue);

        if (mPersisted) {
            set(that.getValue());
        }
    }

    private void initGroupMap(@Nullable final Collection<Integer> value) {
        mGroupMap.clear();
        if (value != null) {
            for (@BooklistGroup.Id final int id : value) {
                mGroupMap.put(id, BooklistGroup.newInstance(id, mPersisted, mStyle));
            }
        }
    }

    /**
     * Get all groups assigned to this style.
     *
     * @return group list
     */
    @NonNull
    public ArrayList<BooklistGroup> getGroupList() {
        return new ArrayList<>(mGroupMap.values());
    }

    /**
     * Check if the given group is present, using the given group id.
     *
     * @param id group id
     *
     * @return {@code true} if present
     */
    public boolean contains(@BooklistGroup.Id final int id) {
        return mGroupMap.containsKey(id);
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
        return Objects.requireNonNull(mGroupMap.get(id), ()
                -> "Group was NULL: id=" + id + ", " + this);
    }

    /**
     * Get the group for the given id.
     *
     * @param id to get
     *
     * @return group, or {@code null} if not present.
     */
    @Nullable
    public BooklistGroup getGroupById(@BooklistGroup.Id final int id) {
        return mGroupMap.get(id);
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
        return (BooklistGroup) mGroupMap.values().toArray()[level - 1];
    }

    /**
     * Get the number of groups in this style.
     *
     * @return the number of groups
     */
    public int size() {
        return mGroupMap.size();
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
        return mGroupMap.values().stream()
                        .map(element -> element.getLabel(context))
                        .collect(Collectors.joining(", "));
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @NonNull
    @Override
    public ArrayList<Integer> getValue() {
        if (mPersisted) {
            final ArrayList<Integer> value = mStyle.getPersistenceLayer().getStringedIntList(mKey);
            if (value != null) {
                return value;
            }
        } else {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    @Override
    public void set(@Nullable final ArrayList<Integer> value) {
        if (mPersisted) {
            mStyle.getPersistenceLayer().setStringedIntList(mKey, value);
        } else {
            mNonPersistedValue.clear();
            if (value != null) {
                mNonPersistedValue.addAll(value);
            }
        }

        // init the individual groups AFTER the value of the Groups is changed
        initGroupMap(value);
    }

    public void clear() {
        mGroupMap.clear();
        if (mPersisted) {
            mStyle.getPersistenceLayer().remove(mKey);
        } else {
            mNonPersistedValue.clear();
        }
    }

    /**
     * Add a new group to the end of the list.
     *
     * @param group to add
     */
    public void add(@NonNull final BooklistGroup group) {
        mGroupMap.put(group.getId(), group);

        if (mPersisted) {
            final StylePersistenceLayer persistenceLayer = mStyle.getPersistenceLayer();
            final String list = persistenceLayer.getNonGlobalString(mKey);
            persistenceLayer.setString(mKey, (list != null ? list + DELIM : "") + group.getId());
        } else {
            mNonPersistedValue.add(group.getId());
        }
    }

    /**
     * Remove the given group.
     *
     * @param id of group to remove
     */
    public void remove(@BooklistGroup.Id final int id) {
        mGroupMap.remove(id);

        if (mPersisted) {
            final StylePersistenceLayer persistenceLayer = mStyle.getPersistenceLayer();
            final String list = persistenceLayer.getNonGlobalString(mKey);
            if (list != null && !list.isEmpty()) {
                // create a new list, and copy the elements from the old list
                // except the one to remove
                final List<String> newList = new ArrayList<>();
                for (final String e : list.split(DELIM)) {
                    if (!e.equals(String.valueOf(id))) {
                        newList.add(e);
                    }
                }
                if (newList.isEmpty()) {
                    persistenceLayer.remove(mKey);
                } else {
                    persistenceLayer.setString(mKey, TextUtils.join(DELIM, newList));
                }
            }
        } else {
            mNonPersistedValue.remove(id);
        }
    }

    /**
     * Get a flat map with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return flat map
     */
    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = new HashMap<>();
        // the actual groups which is a list of integers => the group id's
        map.put(mKey, this);
        // flatten each group specific preferences
        for (final BooklistGroup group : getGroupList()) {
            map.putAll(group.getRawPreferences());
        }
        return map;
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
        return mKey.equals(groups.mKey)
               && mNonPersistedValue.equals(groups.mNonPersistedValue)
               && mDefaultValue.equals(groups.mDefaultValue)

               && Objects.equals(mGroupMap, groups.mGroupMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mNonPersistedValue, mDefaultValue, mGroupMap);
    }

    @Override
    @NonNull
    public String toString() {
        return "Groups{"
               + "mKey=`" + mKey + '`'
               + ", mDefaultValue=`" + mDefaultValue + '`'
               + ", mPersisted=" + mPersisted
               + ", mNonPersistedValue=`" + mNonPersistedValue + '`'
               + ", mGroupMap=" + mGroupMap
               + '}';
    }
}
