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
import androidx.annotation.Px;
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
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.utils.LinkedMap;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Represents a specific style of booklist (e.g. Authors/Series).<br>
 * Individual {@link BooklistGroup} objects are added to a {@link BaseStyle} in order
 * to describe the resulting list style.
 */
public abstract class BaseStyle
        implements Style {

    private static final String ERROR_UUID_IS_EMPTY = "uuid.isEmpty()";
    /** Configuration for the fields shown on the Book level in the book list. */
    @NonNull
    private final FieldVisibility listFieldVisibility;

    /** Configuration for the fields shown on the Book details screen. */
    @NonNull
    private final FieldVisibility detailsFieldVisibility;

    /**
     * The <strong>ordered</strong> {@link BooklistGroup}s shown/handled by this style.
     * <p>
     * Key: @BooklistGroup.Id
     */
    private final Map<Integer, BooklistGroup> groups = new LinkedHashMap<>();

    /**
     * The <strong>ordered</strong> fields on the book-level shown/handled by this style
     * with their sorting preference.
     * <p>
     * Key: the {@link DBKey} string.
     */
    private final Map<String, FieldOrder> optionalFieldOrder = new LinkedHashMap<>();
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
    /**
     * Cached pixel value.
     *
     * @see #getGroupRowHeight(Context)
     */
    @Px
    private int listPreferredItemHeightSmall;

    /**
     * Base constructor.
     *
     * @param uuid style UUID string
     * @param id   style id; can be {@code 0} for new styles (e.g. when importing)
     *
     * @throws IllegalArgumentException if the given UUID is empty
     */
    BaseStyle(@NonNull final String uuid,
              @IntRange(from = 0) final long id) {
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException(ERROR_UUID_IS_EMPTY);
        }
        this.uuid = uuid;
        this.id = id;

        listFieldVisibility = new BooklistFieldVisibility();
        detailsFieldVisibility = new BookDetailsFieldVisibility();

        initOptionalFieldOrderDefaults();
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param id    for the new style
     * @param uuid  for the new style
     * @param style to clone
     */
    BaseStyle(@NonNull final String uuid,
              @IntRange(from = 0) final long id,
              @NonNull final BaseStyle style) {
        this(uuid, id);

        preferred = style.isPreferred();
        menuPosition = style.getMenuPosition();

        expansionLevel = style.getExpansionLevel();
        groupRowUsesPreferredHeight = style.isGroupRowUsesPreferredHeight();

        showAuthorByGivenName = style.isShowAuthorByGivenName();
        sortAuthorByGivenName = style.isSortAuthorByGivenName();

        textScale = style.getTextScale();
        coverScale = style.getCoverScale();

        setHeaderFieldVisibilityValue(style.getHeaderFieldVisibilityValue());
        for (final Screen screen : Screen.values()) {
            getFieldVisibility(screen).setValue(style.getFieldVisibility(screen).getValue());
        }

        // set groups first!
        setGroupList(style.getGroupList());

        setPrimaryAuthorType(style.getPrimaryAuthorType());

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooks(item, style.isShowBooks(item));
        }
    }

    /** load the default sorting options for the optional book-level fields. */
    private void initOptionalFieldOrderDefaults() {
        optionalFieldOrder.put(DBKey.TITLE, new FieldOrder(DBKey.TITLE, Sort.Asc));

        final List<String> dbKeys = List.of(DBKey.COVER[0],
                                            DBKey.EDITION__BITMASK,
                                            DBKey.SIGNED__BOOL,
                                            DBKey.BOOK_CONDITION,
                                            DBKey.TITLE_ORIGINAL_LANG,
                                            DBKey.LOANEE_NAME,

                                            DBKey.FK_BOOKSHELF,
                                            DBKey.FK_AUTHOR,
                                            DBKey.FK_SERIES,
                                            DBKey.FK_PUBLISHER,

                                            DBKey.BOOK_PUBLICATION__DATE,
                                            DBKey.FORMAT,
                                            DBKey.LOCATION,
                                            DBKey.RATING,
                                            DBKey.PAGE_COUNT);

        for (final String dbKey : dbKeys) {
            if (isShowField(Screen.List, dbKey)) {
                optionalFieldOrder.put(dbKey, new FieldOrder(dbKey, Sort.Unsorted));
            }
        }
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return cloned/new instance
     *
     * @throws IllegalArgumentException if the given UUID is empty
     */
    @SuppressWarnings("ClassReferencesSubclass")
    @Override
    @NonNull
    public UserStyle clone(@NonNull final Context context) {
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException(ERROR_UUID_IS_EMPTY);
        }
        // A cloned style is *always* a UserStyle/persistent regardless of the original
        // being a UserStyle or BuiltinStyle.
        return new UserStyle(context, 0, UUID.randomUUID().toString(), this);
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

    /**
     * Set the preference whether to <strong>show</strong> the Author full name
     * with their given-name first, or their family name first.
     * i.e.
     *
     * @param value {@code true} for "given family", or {@code false} for "family, given"
     */
    public void setShowAuthorByGivenName(final boolean value) {
        showAuthorByGivenName = value;
    }

    @Override
    public boolean isSortAuthorByGivenName() {
        return sortAuthorByGivenName;
    }

    /**
     * Set the preference whether to <strong>sort</strong> the Author full name
     * with their given-name first, or their family name first.
     * i.e.
     *
     * @param value {@code true} for "given family", or {@code false} for "family, given"
     */
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
    public int getHeaderFieldVisibilityValue() {
        return headerFieldVisibility;
    }

    public void setHeaderFieldVisibilityValue(@BooklistHeader.Option final int bitmask) {
        headerFieldVisibility = bitmask & BooklistHeader.BITMASK_ALL;
    }

    @Override
    public boolean isShowField(@NonNull final Screen screen,
                               @NonNull final String dbKey) {
        // First check the style!
        // If we have a field which is simply not defined on the style, use the global
        switch (screen) {
            case List:
                return listFieldVisibility
                        .isShowFieldOpt(dbKey)
                        .orElseGet(() -> ServiceLocator.getInstance().getGlobalFieldVisibility()
                                                       .isShowFieldOpt(dbKey)
                                                       .orElse(true));

            case Detail:
                return detailsFieldVisibility
                        .isShowFieldOpt(dbKey)
                        .orElseGet(() -> ServiceLocator.getInstance().getGlobalFieldVisibility()
                                                       .isShowFieldOpt(dbKey)
                                                       .orElse(true));

            case Global:
                return ServiceLocator.getInstance().getGlobalFieldVisibility()
                                     .isShowFieldOpt(dbKey)
                                     .orElse(true);
        }
        throw new IllegalArgumentException();
    }

    /**
     * Create the list of {@link DomainExpression}s for the optional fields to be shown
     * on the book-level in the {@link com.hardbacknutter.nevertoomanybooks.booklist.Booklist}.
     * <p>
     * WARNING: the field {@link DBKey#LOANEE_NAME} requires the caller to do a {@code LEFT JOIN}
     * with {@link DBDefinitions#TBL_BOOK_LOANEE}.
     *
     * @return list
     */
    @NonNull
    public List<DomainExpression> getOptionalFieldDomainExpressions() {
        final List<DomainExpression> all = new ArrayList<>();

        optionalFieldOrder
                .values()
                .stream()
                .filter(fieldOrder -> isShowField(Screen.List, fieldOrder.dbKey))
                .forEachOrdered(fieldOrder -> {

                    if (DBKey.COVER[0].equals(fieldOrder.dbKey)) {
                        // We need the UUID for the book to get covers
                        all.add(new DomainExpression(
                                DBDefinitions.DOM_BOOK_UUID,
                                DBDefinitions.TBL_BOOKS,
                                fieldOrder.sort));
                    } else {
                        switch (fieldOrder.dbKey) {
                            case DBKey.TITLE:
                                // Title for displaying; do NOT sort on it
                                // Example: "The Dream Master"
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_TITLE,
                                        DBDefinitions.TBL_BOOKS,
                                        Sort.Unsorted));
                                // Title for sorting
                                // Example: "dreammasterthe" OR "thedreammaster"
                                // i.e. depending on user preference, the first format
                                // consists of the original title stripped of whitespace and any
                                // special characters, and with the article/prefix moved to the end.
                                // The second format leaves the article/prefix in its original
                                // location.
                                // The choice between the two formats is a user preference which,
                                // when changed, updates ALL rows in the database with the
                                // newly formatted title.
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_TITLE_OB,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.EDITION__BITMASK:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_EDITION,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.SIGNED__BOOL:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_SIGNED,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.BOOK_CONDITION:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_CONDITION,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.TITLE_ORIGINAL_LANG:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_TITLE_ORIGINAL_LANG,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.LOANEE_NAME:
                                // Used to display/hide the 'lend' icon for each book.
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_LOANEE,
                                        DBDefinitions.TBL_BOOK_LOANEE,
                                        fieldOrder.sort));
                                break;

                            case DBKey.FK_BOOKSHELF:
                                // Collect a CSV list of the bookshelves the book is on.
                                // It is ALWAYS unsorted, as the list is build by SQLite internals
                                // and the order returned is arbitrary.
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOKSHELF_NAME_CSV,
                                        DBDefinitions.EXP_BOOKSHELF_NAME_CSV,
                                        Sort.Unsorted));
                                break;

                            case DBKey.FK_AUTHOR:
                                // primary author only
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                                        AuthorDaoImpl.getDisplayDomainExpression(
                                                isShowAuthorByGivenName()),
                                        fieldOrder.sort));
                                break;

                            case DBKey.FK_SERIES:
                                // primary series only
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_SERIES_TITLE,
                                        DBDefinitions.TBL_SERIES,
                                        fieldOrder.sort));
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_NUM_IN_SERIES,
                                        DBDefinitions.TBL_BOOK_SERIES,
                                        fieldOrder.sort));
                                break;

                            case DBKey.FK_PUBLISHER:
                                // primary publisher only
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_PUBLISHER_NAME,
                                        DBDefinitions.TBL_PUBLISHERS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.BOOK_PUBLICATION__DATE:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.FORMAT:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_FORMAT,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.LOCATION:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_LOCATION,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.RATING:
                                all.add(new DomainExpression(
                                        DBDefinitions.DOM_BOOK_RATING,
                                        DBDefinitions.TBL_BOOKS,
                                        fieldOrder.sort));
                                break;

                            case DBKey.PAGE_COUNT:
                                all.add(new DomainExpression(DBDefinitions.DOM_BOOK_PAGES,
                                                             DBDefinitions.TBL_BOOKS,
                                                             fieldOrder.sort));
                                break;

                            default:
                                throw new IllegalArgumentException("DBKey missing: "
                                                                   + fieldOrder.dbKey);
                        }
                    }
                });

        return all;
    }

    @Override
    @NonNull
    public FieldVisibility getFieldVisibility(@NonNull final Screen screen) {
        switch (screen) {
            case List:
                return listFieldVisibility;
            case Detail:
                return detailsFieldVisibility;
            case Global:
                return ServiceLocator.getInstance().getGlobalFieldVisibility();
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
     *         or {@code false} for {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
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

    /**
     * Set the list of groups.
     *
     * @param list to set
     */
    public void setGroupList(@Nullable final List<BooklistGroup> list) {
        groups.clear();
        if (list != null) {
            list.forEach(group -> groups.put(group.getId(), group));
        }
    }

    /**
     * Using the given group-ids, create and set the group list.
     *
     * @param groupIds to create groups for
     */
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
     * Wrapper that gets the primary-author-type from the {@link AuthorBooklistGroup}
     * (if we have it); or the default {@link Author#TYPE_UNKNOWN}.
     *
     * @return the type of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorType() {
        return getGroupById(BooklistGroup.AUTHOR)
                .map(group -> ((AuthorBooklistGroup) group).getPrimaryType())
                .orElse(Author.TYPE_UNKNOWN);
    }

    /**
     * Wrapper to set the primary-author-type from the {@link AuthorBooklistGroup} (if we have it).
     *
     * @param type the Author type
     */
    public void setPrimaryAuthorType(@Author.Type final int type) {
        getGroupById(BooklistGroup.AUTHOR)
                .ifPresent(group -> ((AuthorBooklistGroup) group).setPrimaryType(type));
    }

    /**
     * Wrapper to set the show-book-under-each for the given wrapped group (if we have it).
     *
     * @param item  the wrapped group
     * @param value to set
     */
    public void setShowBooks(@NonNull final UnderEach item,
                             final boolean value) {
        getGroupById(item.getGroupId())
                .ifPresent(group -> ((UnderEachGroup) group).setShowBooksUnderEach(value));
    }

    /**
     * Wrapper that gets the show-book-under-each for the given wrapped group (if we have it);
     * or by default {@code false}.
     *
     * @param item the wrapped group
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
               && LinkedMap.equals(groups, style.groups);
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
               + ", groups=" + groups

               + ", showAuthorByGivenName=" + showAuthorByGivenName
               + ", sortAuthorByGivenName=" + sortAuthorByGivenName

               + ", expansionLevel=" + expansionLevel
               + ", groupRowUsesPreferredHeight=" + groupRowUsesPreferredHeight
               + ", listPreferredItemHeightSmall=" + listPreferredItemHeightSmall
               + ", headerFieldVisibility=" + headerFieldVisibility

               + ", coverScale=" + coverScale
               + ", textScale=" + textScale
               + ", listFieldVisibility=" + listFieldVisibility
               + ", detailsFieldVisibility=" + detailsFieldVisibility
               + '}';
    }

    private static class FieldOrder {
        @NonNull
        final String dbKey;
        @NonNull
        final Sort sort;

        FieldOrder(@NonNull final String dbKey,
                   @NonNull final Sort sort) {
            this.dbKey = dbKey;
            this.sort = sort;
        }
    }
}
