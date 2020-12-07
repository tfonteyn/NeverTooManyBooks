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
package com.hardbacknutter.nevertoomanybooks.booklist.groups;

import android.content.Context;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PCsvString;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleSharedPreferences;

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
 * @see PCsvString
 */
public class Groups
        implements PPref<List<Integer>>, PCsvString {

    /** Style group preferences. */
    public static final String PK_STYLE_GROUPS = "style.booklist.groups";

    /** The {@link ListStyle} this preference belongs to. */
    @NonNull
    private final ListStyle mStyle;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final List<Integer> mDefaultValue;
    /** in memory value used for non-persistence situations. */
    @NonNull
    private final List<Integer> mNonPersistedValue;

    /** All groups; ordered. Reminder: the underlying pref is only storing the id. */
    private final Map<Integer, BooklistGroup> mGroupMap = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   the style
     */
    public Groups(@NonNull final Context context,
                  @NonNull final ListStyle style) {
        mStyle = style;
        mKey = PK_STYLE_GROUPS;
        mDefaultValue = new ArrayList<>();
        mNonPersistedValue = new ArrayList<>();

        // load the group ID's from the SharedPreference and populates the Group object list.
        for (@BooklistGroup.Id final int id : getValue(context)) {
            mGroupMap.put(id, BooklistGroup.newInstance(context, id, style));
        }
    }

    /**
     * Copy constructor.
     *
     * @param groups to copy from
     */
    public Groups(@NonNull final Context context,
                  @NonNull final ListStyle style,
                  @NonNull final Groups groups) {
        mStyle = style;
        mKey = groups.mKey;
        mDefaultValue = new ArrayList<>(groups.mDefaultValue);
        mNonPersistedValue = new ArrayList<>(groups.mNonPersistedValue);

        for (final BooklistGroup group : groups.mGroupMap.values()) {
            final BooklistGroup clonedGroup;
            if (group instanceof AuthorBooklistGroup) {
                clonedGroup = new AuthorBooklistGroup(context, style, (AuthorBooklistGroup) group);
            } else if (group instanceof SeriesBooklistGroup) {
                clonedGroup = new SeriesBooklistGroup(style, (SeriesBooklistGroup) group);
            } else if (group instanceof PublisherBooklistGroup) {
                clonedGroup = new PublisherBooklistGroup(style, (PublisherBooklistGroup) group);
            } else if (group instanceof BookshelfBooklistGroup) {
                clonedGroup = new BookshelfBooklistGroup(style, (BookshelfBooklistGroup) group);
            } else {
                clonedGroup = new BooklistGroup(style, group);
            }
            mGroupMap.put(clonedGroup.getId(), clonedGroup);
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
        /* Dev note: we want this call to ALWAYS return a valid group.
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
         * TEST: We're using newListCursor everywhere now.
         * Seems 'get' -> existing cursor, with link to builder with link to style
         * while elsewhere we already have a new builder/style.
         */

        // note the use of a Supplier
        return Objects.requireNonNull(mGroupMap.get(id), ()
                -> "Group was NULL: id=" + id + ", " + this.toString());
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
    public List<Integer> getValue(@NonNull final Context context) {
        if (mStyle.isUserDefined()) {
            final List<Integer> value = mStyle.getSettings().getIntList(context, mKey);
            if (value != null) {
                return value;
            }
        } else {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    @Override
    public void setCsv(@Nullable final String values) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setString(mKey, values);
        } else {
            // Not implemented for now, and in fact not needed/used for now (2020-03-11)
            // Problem is that we'd need to split the incoming CSV string, and re-create the list.
            // But on this level, we don't know the real type of the elements in the Csv string.
            // i.o.w. this needs to be implemented in a concrete class.
            // Aside of that, current usage is that a List is concatenated to a Csv String and
            // given to this method. Implementing the non-persistent branch would bring a
            // pointless double conversion.
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void set(@Nullable final List<Integer> value) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setIntList(mKey, value);
        } else {
            mNonPersistedValue.clear();
            if (value != null) {
                mNonPersistedValue.addAll(value);
            }
        }
    }

    public void clear() {
        mGroupMap.clear();
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().remove(mKey);
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

        if (mStyle.isUserDefined()) {
            final StyleSharedPreferences styleSharedPreferences = mStyle.getSettings();
            final String list = styleSharedPreferences.getString(mKey);
            styleSharedPreferences
                    .setString(mKey, (list != null ? list + DELIM : "") + group.getId());
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

        if (mStyle.isUserDefined()) {
            final StyleSharedPreferences styleSharedPreferences = mStyle.getSettings();

            final String list = styleSharedPreferences.getString(mKey);
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
                    styleSharedPreferences.remove(mKey);
                } else {
                    styleSharedPreferences.setString(mKey, TextUtils.join(DELIM, newList));
                }
            }
        } else {
            mNonPersistedValue.remove(id);
        }
    }

    /**
     * Add all entries to the given map.
     *
     * @param map to add to
     */
    public void addToMap(@NonNull final Map<String, PPref> map) {
        // for each group used by the style, add its specific preferences to our list
        for (final BooklistGroup group : getGroupList()) {
            map.putAll(group.getPreferences());
        }
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        clear();

        final List<BooklistGroup> list = new ArrayList<>();
        in.readList(list, getClass().getClassLoader());
        // (faster) equivalent of add(@NonNull final BooklistGroup group)
        // but split in adding the group and...
        for (final BooklistGroup group : list) {
            mGroupMap.put(group.getId(), group);
        }
        // storing the ID's in SharedPreference.
        this.set(new ArrayList<>(mGroupMap.keySet()));
    }


    /**
     * Write the <strong>value</strong> to the Parcel.
     *
     * @param dest parcel to write to
     */
    public void writeToParcel(@NonNull final Parcel dest) {
        dest.writeList(new ArrayList<>(mGroupMap.values()));
    }

    @Override
    @NonNull
    public String toString() {
        return "Groups{"
               + "mGroups=" + mGroupMap
               + ", mKey=`" + mKey + '`'
               + ", mStyle=" + mStyle.getUuid()
               + ", mDefaultValue=`" + mDefaultValue + '`'
               + ", mNonPersistedValue=`" + mNonPersistedValue + '`'
               + ", value=`" + getValue(App.getAppContext()) + '`'
               + '}';
    }
}
