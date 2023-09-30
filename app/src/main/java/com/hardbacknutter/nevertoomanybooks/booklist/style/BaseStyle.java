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
import android.content.res.TypedArray;
import android.view.ViewGroup;

import androidx.annotation.Dimension;
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.UnderEachGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.utils.LinkedMap;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

/**
 * Represents a specific style of booklist (e.g. Authors/Series).<br>
 * Individual {@link BooklistGroup} objects are added to a {@link BaseStyle} in order
 * to describe the resulting list style.
 */
public abstract class BaseStyle
        implements Style {

    private static final String ERROR_UUID_IS_EMPTY = "uuid.isEmpty()";

    /**
     * IMPORTANT: this is the ALMOST the same set as used by BookLevelFieldVisibility
     * and should be kept in sync.
     * but note the differences:
     * <ul>
     *     <li>TITLE added: we ALWAYS display it.</li>
     *     <li>ISBN & LANGUAGE removed: we already have it added during BooklistBuilder setup.</li>
     * </ul>
     * Also note this is an <strong>ORDERED LIST!</strong>
     */
    private static final Map<String, Sort> BOOK_LEVEL_FIELDS_DEFAULTS = new LinkedHashMap<>();

    static {
        // The default is sorting by book title only
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE, Sort.Asc);

        // The field order here on assuming the user will need to sort more likely
        // on the fields at the top.
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE_ORIGINAL_LANG, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_AUTHOR, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_SERIES, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_PUBLISHER, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.BOOK_PUBLICATION__DATE, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FORMAT, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOCATION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.RATING, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.PAGE_COUNT, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.BOOK_CONDITION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.SIGNED__BOOL, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.EDITION__BITMASK, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOANEE_NAME, Sort.Unsorted);
    }


    /** Configuration for the fields shown on the Book level in the book list. */
    @NonNull
    private final FieldVisibility bookLevelFieldVisibility;

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
    @NonNull
    private final Map<String, Sort> bookLevelFieldsOrderBy = new LinkedHashMap<>();
    @NonNull
    private final String uuid;
    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    private long id;

    @NonNull
    private Layout layout = Layout.List;

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

        bookLevelFieldVisibility = new BookLevelFieldVisibility();

        detailsFieldVisibility = new BookDetailsFieldVisibility();

        initBookLevelFieldsOrderByDefaults();
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

        layout = style.layout;

        expansionLevel = style.getExpansionLevel();
        groupRowUsesPreferredHeight = style.isGroupRowUsesPreferredHeight();

        showAuthorByGivenName = style.isShowAuthorByGivenName();
        sortAuthorByGivenName = style.isSortAuthorByGivenName();

        textScale = style.getTextScale();
        coverScale = style.getCoverScale();

        setHeaderFieldVisibilityValue(style.getHeaderFieldVisibilityValue());
        for (final Screen screen : Screen.values()) {
            getFieldVisibility(screen).setBitValue(style.getFieldVisibility(screen).getBitValue());
        }

        // set groups first!
        setGroupList(style.getGroupList());

        setPrimaryAuthorType(style.getPrimaryAuthorType());

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooks(item, style.isShowBooks(item));
        }
    }

    /** load the default sorting options for the optional book-level fields. */
    private void initBookLevelFieldsOrderByDefaults() {
        bookLevelFieldsOrderBy.putAll(BOOK_LEVEL_FIELDS_DEFAULTS);
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
    @NonNull
    public Layout getLayout() {
        return layout;
    }

    public void setLayout(@NonNull final Layout layout) {
        this.layout = layout;
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

    @Dimension
    public int getCoverMaxSizeInPixels(@NonNull final Context context) {
        return getCoverMaxSizeInPixels(context, layout);
    }

    @Dimension
    public int getCoverMaxSizeInPixels(@NonNull final Context context,
                                       @NonNull final Layout layout) {
        final int scale;
        if (layout == Layout.Grid) {
            switch (WindowSizeClass.getWidthVariant(context)) {
                case Compact:
                    scale = COVER_SCALE_SMALL;
                    break;
                case Expanded:
                    scale = COVER_SCALE_LARGE;
                    break;
                case Medium:
                default:
                    scale = COVER_SCALE_MEDIUM;
                    break;
            }
        } else {
            // default: List: use the value as set by the user preferences.
            scale = coverScale;
        }

        // The scale is used to retrieve the cover dimensions.
        // We use a square space for the image so both portrait/landscape images work out.
        final TypedArray coverSizes = context
                .getResources().obtainTypedArray(R.array.cover_book_list_longest_side);
        try {
            return coverSizes.getDimensionPixelSize(scale, 0);
        } finally {
            coverSizes.recycle();
        }
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
        // If we have a field which is simply not defined on the respective FieldVisibility,
        // use the global and if that fails, just return 'true'
        switch (screen) {
            case List:
                return bookLevelFieldVisibility.isVisible(dbKey).orElseGet(
                        () -> ServiceLocator.getInstance().getGlobalFieldVisibility()
                                            .isVisible(dbKey)
                                            .orElse(true));

            case Detail:
                return detailsFieldVisibility.isVisible(dbKey).orElseGet(
                        () -> ServiceLocator.getInstance().getGlobalFieldVisibility()
                                            .isVisible(dbKey)
                                            .orElse(true));

            case Global:
                return ServiceLocator.getInstance().getGlobalFieldVisibility()
                                     .isVisible(dbKey)
                                     .orElse(true);
        }
        throw new IllegalArgumentException();
    }

    @NonNull
    public Map<String, Sort> getBookLevelFieldsOrderBy() {
        return bookLevelFieldsOrderBy;
    }

    public void setBookLevelFieldsOrderBy(@NonNull final Map<String, Sort> map) {
        bookLevelFieldsOrderBy.clear();
        bookLevelFieldsOrderBy.putAll(map);
        // add any fields with their default which might be missing.
        BOOK_LEVEL_FIELDS_DEFAULTS.forEach(bookLevelFieldsOrderBy::putIfAbsent);
    }

    @Override
    @NonNull
    public FieldVisibility getFieldVisibility(@NonNull final Screen screen) {
        switch (screen) {
            case List:
                return bookLevelFieldVisibility;
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

               && Objects.equals(bookLevelFieldVisibility, style.bookLevelFieldVisibility)
               && Objects.equals(detailsFieldVisibility, style.detailsFieldVisibility)
               && LinkedMap.equals(groups, style.groups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, menuPosition, preferred,
                            showAuthorByGivenName, sortAuthorByGivenName,
                            expansionLevel, headerFieldVisibility, groupRowUsesPreferredHeight,
                            coverScale, textScale,
                            bookLevelFieldVisibility, detailsFieldVisibility,
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
               + ", listFieldVisibility=" + bookLevelFieldVisibility
               + ", detailsFieldVisibility=" + detailsFieldVisibility
               + '}';
    }

}
