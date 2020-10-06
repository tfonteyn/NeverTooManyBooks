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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;

/**
 * Encapsulate the list of {@code BooklistGroup} with backend storage in a preference,
 * and all related data/logic.
 */
public class Groups
        extends PIntList {

    /** Style group preferences. */
    public static final String PK_STYLE_GROUPS = "style.booklist.groups";

    /** All groups; ordered. Reminder: the underlying PIntList is only storing the id. */
    private final Map<Integer, BooklistGroup> mGroupMap = new LinkedHashMap<>();

    /**
     * Constructor.
     *
     * @param context Current context
     * @param style   the style
     */
    public Groups(@NonNull final Context context,
                  @NonNull final BooklistStyle style) {
        super(style.getStyleSharedPreferences(), style.isUserDefined(), PK_STYLE_GROUPS);

        // load the group ID's from the SharedPreference and populates the Group object list.
        mGroupMap.clear();
        for (@BooklistGroup.Id int id : getValue(context)) {
            mGroupMap.put(id, BooklistGroup.newInstance(context, id, style));
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
     * <p>
     * Dev note: we want this call to ALWAYS return a valid group.
     * We had (have?) a bug in the past:
     * <p>
     * at BooklistStyle.getGroupById(BooklistStyle.java:1152)
     * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:247)
     * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:96)
     * <p>
     * the STYLE is the wrong one...
     * 2020-09-11: java.lang.IllegalArgumentException: Group was NULL: id=14
     * 14 is READ_YEAR
     * but the style dumped was "Books - Author, Series"
     * so it's the STYLE itself which was wrong...
     * TEST: We're using newListCursor everywhere now.
     * Seems 'get' -> existing cursor, with link to builder with link to style
     * while elsewhere we already have a new builder/style.
     *
     * @param id to get
     *
     * @return group
     *
     * @throws IllegalArgumentException on bug
     */
    @NonNull
    public BooklistGroup getGroupByIdOrCrash(final int id) {
        final BooklistGroup group = mGroupMap.get(id);
        if (group == null) {
            // Don't use a Objects.requireNonNull() ... message is evaluated before null test.
            throw new IllegalArgumentException(
                    "Group was NULL: id=" + id + ", " + this.toString());
        }
        return group;
    }

    /**
     * Get the group for the given id.
     *
     * @param id to get
     *
     * @return group, or {@code null} if not present.
     */
    @Nullable
    BooklistGroup getGroupById(@BooklistGroup.Id final int id) {
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

    /**
     * Add a new group to the end of the list.
     *
     * @param group to add
     */
    public void add(@NonNull final BooklistGroup group) {
        mGroupMap.put(group.getId(), group);
        super.add(group.getId());
    }

    @Override
    public void add(@NonNull final Integer element) {
        // we need the actual group to add it to mGroups
        throw new IllegalStateException("use add(BooklistGroup) instead");
    }

    /**
     * Remove the given group.
     *
     * @param id of group to remove
     */
    @Override
    public void remove(@BooklistGroup.Id @NonNull final Integer id) {
        mGroupMap.remove(id);
        super.remove(id);
    }

    /**
     * Add all entries to the given map.
     *
     * @param map to add to
     */
    void addToMap(@NonNull final Map<String, PPref> map) {
        // for each group used by the style, add its specific preferences to our list
        for (BooklistGroup group : getGroupList()) {
            map.putAll(group.getPreferences());
        }
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    @Override
    public void set(@NonNull final Parcel in) {
        mGroupMap.clear();
        super.clear();

        final List<BooklistGroup> list = new ArrayList<>();
        in.readList(list, getClass().getClassLoader());
        // (faster) equivalent of add(@NonNull final BooklistGroup group)
        // but split in adding the group and...
        for (BooklistGroup group : list) {
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
    @Override
    public void writeToParcel(@NonNull final Parcel dest) {
        dest.writeList(new ArrayList<>(mGroupMap.values()));
    }

    @Override
    @NonNull
    public String toString() {
        return "Groups{" + super.toString()
               + "mGroups=" + mGroupMap
               + '}';
    }
}
