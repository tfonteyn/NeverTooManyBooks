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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.UnderEachGroup;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Represents a specific style of booklist (e.g. Authors/Series).<br>
 * Individual {@link BooklistGroup} objects are added to a {@link BaseStyle} in order
 * to describe the resulting list style.
 */
public abstract class BaseStyle
        implements Style {

    /** Configuration for the fields shown on the Book level in the book list. */
    @NonNull
    private final BooklistFieldVisibility listFieldVisibility;

    /** Configuration for the fields shown on the Book details screen. */
    @NonNull
    private final BookDetailsFieldVisibility detailsFieldVisibility;

    /** All groups; <strong>ordered</strong>. */
    private final Map<Integer, BooklistGroup> groups = new LinkedHashMap<>();

    @NonNull
    private final String uuid;

    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    private long id;

    /**
     * The menu position of this style as sorted by the user.
     * Preferred styles will be at the top.
     */
    private int menuPosition;

    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     */
    private boolean preferred;

    /** Relative scaling factor for text on the list screen. */
    @Style.TextScale
    private int textScale = Style.DEFAULT_TEXT_SCALE;

    /** Relative scaling factor for covers on the list screen. */
    @Style.CoverScale
    private int coverScale = Style.DEFAULT_COVER_SCALE;

    /** Local override. */
    private boolean sortAuthorByGivenName;

    /** Local override. */
    private boolean showAuthorByGivenName;

    /** The default number of levels to expand the list tree to. */
    private int expansionLevel = 1;

    /**
     * Show list header info.
     */
    private int headerFieldVisibility = BooklistHeader.BITMASK_ALL;

    /**
     * Should rows be shown using
     * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT} (false),
     * or as system "?attr/listPreferredItemHeightSmall" (true).
     */
    private boolean groupRowUsesPreferredHeight = true;

    /** Cached pixel value. */
    private int listPreferredItemHeightSmall;

    /**
     * Base constructor.
     *
     * @param uuid style UUID string
     * @param id   style id; can be {@code 0} for new styles (e.g. when importing)
     */
    BaseStyle(@NonNull final String uuid,
              @IntRange(from = 0) final long id) {
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException("uuid.isEmpty()");
        }
        this.uuid = uuid;
        this.id = id;

        listFieldVisibility = new BooklistFieldVisibility();
        detailsFieldVisibility = new BookDetailsFieldVisibility();
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return cloned/new instance
     */
    @SuppressWarnings("ClassReferencesSubclass")
    @Override
    @NonNull
    public UserStyle clone(@NonNull final Context context) {
        SanityCheck.requireValue(uuid, "uuid");
        // A cloned style is *always* a UserStyle/persistent regardless of the original
        // being a UserStyle or BuiltinStyle.
        return new UserStyle(context, this, 0, UUID.randomUUID().toString());
    }

    @Override
    @NonNull
    public String getUuid() {
        return uuid;
    }

    /**
     * Get the id.
     *
     * <ul>
     *      <li>Positive ID's: user-defined styles</li>
     *      <li>Negative ID's: builtin styles</li>
     *      <li>0: a user-defined style which has not been saved yet</li>
     * </ul>
     */
    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(final long id) {
        this.id = id;
    }

    @Override
    public int getMenuPosition() {
        return menuPosition;
    }

    @Override
    public void setMenuPosition(final int menuPosition) {
        this.menuPosition = menuPosition;
    }

    @Override
    public boolean isPreferred() {
        return preferred;
    }

    @Override
    public void setPreferred(final boolean preferred) {
        this.preferred = preferred;
    }

    @Override
    @IntRange(from = 1)
    public int getExpansionLevel() {
        // limit to the amount of groups!
        return MathUtils.clamp(expansionLevel, 1, groups.size());
    }

    public void setExpansionLevel(@IntRange(from = 1) final int value) {
        expansionLevel = value;
    }


    @Override
    public boolean isShowAuthorByGivenName() {
        return showAuthorByGivenName;
    }

    public void setShowAuthorByGivenName(final boolean value) {
        showAuthorByGivenName = value;
    }

    @Override
    public boolean isSortAuthorByGivenName() {
        return sortAuthorByGivenName;
    }

    public void setSortAuthorByGivenName(final boolean value) {
        sortAuthorByGivenName = value;
    }


    @Style.TextScale
    @Override
    public int getTextScale() {
        return textScale;
    }

    public void setTextScale(@Style.TextScale final int scale) {
        textScale = scale;
    }

    @Style.CoverScale
    @Override
    public int getCoverScale() {
        return coverScale;
    }

    public void setCoverScale(@Style.CoverScale final int coverScale) {
        this.coverScale = coverScale;
    }


    @Override
    public boolean isShowHeaderField(@BooklistHeader.Option final int bit) {
        return (headerFieldVisibility & bit) != 0;
    }

    @BooklistHeader.Option
    public int getHeaderFieldVisibility() {
        return headerFieldVisibility;
    }

    public void setHeaderFieldVisibility(@BooklistHeader.Option final int bitmask) {
        headerFieldVisibility = bitmask & BooklistHeader.BITMASK_ALL;
    }


    @Override
    public boolean isShowField(@NonNull final Screen screen,
                               @NonNull final String dbKey) {
        final Context context = ServiceLocator.getInstance().getAppContext();

        switch (screen) {
            case List:
                return listFieldVisibility
                        .isShowField(dbKey)
                        .orElseGet(() -> GlobalFieldVisibility.isUsed(context, dbKey));
            case Detail:
                return detailsFieldVisibility
                        .isShowField(dbKey)
                        .orElseGet(() -> GlobalFieldVisibility.isUsed(context, dbKey));
        }
        throw new IllegalArgumentException();
    }

    public void setShowField(@NonNull final Screen screen,
                             @NonNull final String dbKey,
                             final boolean show) {
        switch (screen) {
            case List:
                listFieldVisibility.setShowField(dbKey, show);
                break;
            case Detail:
                detailsFieldVisibility.setShowField(dbKey, show);
                break;
        }
    }

    @Override
    public long getFieldVisibility(@NonNull final Screen screen) {
        switch (screen) {
            case List:
                return listFieldVisibility.getValue();
            case Detail:
                return detailsFieldVisibility.getValue();
        }
        throw new IllegalArgumentException();
    }

    public void setFieldVisibility(@NonNull final Screen screen,
                                   final long bitmask) {
        switch (screen) {
            case List:
                listFieldVisibility.setValue(bitmask);
                break;
            case Detail:
                detailsFieldVisibility.setValue(bitmask);
                break;
        }
    }

    @Override
    @NonNull
    public String getFieldVisibilitySummaryText(@NonNull final Context context,
                                                @NonNull final Screen screen) {
        switch (screen) {
            case List:
                return listFieldVisibility.getSummaryText(context);
            case Detail:
                return detailsFieldVisibility.getSummaryText(context);
        }
        throw new IllegalArgumentException();
    }

    @Override
    public int getGroupRowHeight(@NonNull final Context context) {
        if (groupRowUsesPreferredHeight) {
            if (listPreferredItemHeightSmall == 0) {
                listPreferredItemHeightSmall = AttrUtils.getDimensionPixelSize(
                        context, com.google.android.material.R.attr.listPreferredItemHeightSmall);
            }
            return listPreferredItemHeightSmall;
        } else {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    /**
     * Should rows be shown using the system's preferred height or minimum height.
     *
     * @return {@code true} for "?attr/listPreferredItemHeightSmall"
     * or {@code false} for {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
     *
     * @see #getGroupRowHeight(Context)
     */
    public boolean isGroupRowUsesPreferredHeight() {
        return groupRowUsesPreferredHeight;
    }

    public void setGroupRowUsesPreferredHeight(final boolean value) {
        groupRowUsesPreferredHeight = value;
    }


    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public boolean hasGroup(@BooklistGroup.Id final int id) {
        return groups.containsKey(id);
    }

    @Override
    @NonNull
    public Optional<BooklistGroup> getGroupById(@BooklistGroup.Id final int id) {
        final BooklistGroup booklistGroup = groups.get(id);
        if (booklistGroup != null) {
            return Optional.of(booklistGroup);
        } else {
            return Optional.empty();
        }
    }

    @Override
    @NonNull
    public BooklistGroup getGroupByLevel(@IntRange(from = 1) final int level)
            throws IndexOutOfBoundsException {
        // can throw IndexOutOfBoundsException only if we have a bug passing an illegal level.
        return (BooklistGroup) groups.values().toArray()[level - 1];
    }

    @Override
    @NonNull
    public List<BooklistGroup> getGroupList() {
        return new ArrayList<>(groups.values());
    }

    public void setGroupList(@Nullable final List<BooklistGroup> list) {
        groups.clear();
        if (list != null) {
            list.forEach(group -> groups.put(group.getId(), group));
        }
    }

    public void setGroupIds(@NonNull final List<Integer> groupIds) {
        final List<BooklistGroup> list = groupIds
                .stream()
                .map(groupId -> BooklistGroup.newInstance(groupId, this))
                .collect(Collectors.toList());
        setGroupList(list);
    }

    @Override
    @NonNull
    public String getGroupsSummaryText(@NonNull final Context context) {
        return groups.values()
                     .stream()
                     .map(element -> element.getLabel(context))
                     .collect(Collectors.joining(", "));
    }

    /**
     * Wrapper that gets the primary-author-type from the {@link AuthorBooklistGroup},
     * if we have this group; or the default.
     *
     * @return the type of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorType() {
        return getGroupById(BooklistGroup.AUTHOR)
                .map(group -> ((AuthorBooklistGroup) group).getPrimaryType())
                .orElse(Author.TYPE_UNKNOWN);
    }

    public void setPrimaryAuthorTypes(final int value) {
        getGroupById(BooklistGroup.AUTHOR)
                .ifPresent(group -> ((AuthorBooklistGroup) group).setPrimaryType(value));
    }

    public void setShowBooks(@NonNull final UnderEach item,
                             final boolean value) {
        getGroupById(item.getGroupId())
                .ifPresent(group -> ((UnderEachGroup) group).setShowBooksUnderEach(value));
    }

    /**
     * Wrapper that gets the show-book-under-each from {@link AuthorBooklistGroup}
     * if we have this group; or the default.
     *
     * @return {@code true} if we want to show a book under each of its Authors
     */
    @Override
    public boolean isShowBooks(@NonNull final UnderEach item) {
        return getGroupById(item.getGroupId())
                .map(group -> ((UnderEachGroup) group).isShowBooksUnderEach())
                .orElse(false);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseStyle style = (BaseStyle) o;
        return id == style.id
               && uuid.equals(style.uuid)
               && menuPosition == style.menuPosition
               && preferred == style.preferred
               && showAuthorByGivenName == style.showAuthorByGivenName
               && sortAuthorByGivenName == style.sortAuthorByGivenName
               && expansionLevel == style.expansionLevel
               && headerFieldVisibility == style.headerFieldVisibility
               && groupRowUsesPreferredHeight == style.groupRowUsesPreferredHeight
               && coverScale == style.coverScale
               && textScale == style.textScale

               && Objects.equals(listFieldVisibility, style.listFieldVisibility)
               && Objects.equals(detailsFieldVisibility, style.detailsFieldVisibility)
               && Objects.equals(groups, style.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, menuPosition, preferred,
                            showAuthorByGivenName, sortAuthorByGivenName,
                            expansionLevel, headerFieldVisibility, groupRowUsesPreferredHeight,
                            coverScale, textScale,
                            listFieldVisibility, detailsFieldVisibility,
                            groups);
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseStyle{"
               + "id=" + id
               + ", uuid=`" + uuid + '`'
               + ", preferred=" + preferred
               + ", menuPosition=" + menuPosition
               + ", groupMap=" + groups

               + ", showAuthorByGivenName=" + showAuthorByGivenName
               + ", sortAuthorByGivenName=" + sortAuthorByGivenName

               + ", expansionLevel=" + expansionLevel
               + ", groupsUseListPreferredHeight=" + groupRowUsesPreferredHeight
               + ", listPreferredItemHeightSmall=" + listPreferredItemHeightSmall
               + ", showHeaderInfo=" + headerFieldVisibility

               + ", coverScale=" + coverScale
               + ", textScale=" + textScale
               + ", listScreenBookFields=" + listFieldVisibility
               + ", detailScreenBookFields=" + detailsFieldVisibility
               + '}';
    }

}
