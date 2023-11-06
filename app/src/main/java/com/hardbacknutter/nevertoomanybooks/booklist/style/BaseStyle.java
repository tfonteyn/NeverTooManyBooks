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

import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.UnderEachGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.utils.LinkedMap;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public abstract class BaseStyle
        implements Style {

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
    private static final String ERROR_UUID_IS_EMPTY = "uuid.isEmpty()";

    static {
        // The default is sorting by book title only
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE, Sort.Asc);

        // The field order here is assuming the user will need to sort more likely
        // on the fields listed at the top.
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

    /** Configuration for the fields shown on the given {@link FieldVisibility.Screen}. */
    @NonNull
    protected final Map<FieldVisibility.Screen, FieldVisibility> fieldVisibility =
            new EnumMap<>(FieldVisibility.Screen.class);

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
    private final Map<String, Sort> bookLevelFieldsOrderBy =
            new LinkedHashMap<>(BOOK_LEVEL_FIELDS_DEFAULTS);
    @NonNull
    private final String uuid;
    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     * <p>
     * The global style will have {@code Integer.MIN_VALUE}.
     */
    private long id;

    @NonNull
    private Layout layout = Layout.List;

    @NonNull
    private CoverClickAction coverClickAction = CoverClickAction.Zoom;

    /**
     * The menu position of this style as sorted by the user.
     * Preferred styles will be at the top.
     */
    private int menuPosition = MENU_POSITION_NOT_PREFERRED;
    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     */
    private boolean preferred;
    /** Relative scaling factor for text on the list screen. */
    @Style.TextScale
    private int textScale = DEFAULT_TEXT_SCALE;
    /** Relative scaling factor for covers on the list screen. */
    @Style.CoverScale
    private int coverScale = DEFAULT_COVER_SCALE;
    /** Local override. */
    private boolean sortAuthorByGivenName;
    /** Local override. */
    private boolean showAuthorByGivenName;
    /** The default number of levels to expand the list tree to. */
    private int expansionLevel = 1;
    /**
     * Bitmap value with the list header fields to show.
     */
    private int headerFieldVisibility = BooklistHeader.SHOW_BOOK_COUNT
                                        | BooklistHeader.SHOW_STYLE_NAME;
    /**
     * Should rows be shown using
     * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT} {@code false},
     * or as system "?attr/listPreferredItemHeightSmall" {@code true}.
     */
    private boolean groupRowUsesPreferredHeight;

    /**
     * Constructor.
     *
     * @param uuid for the new style
     * @param id   for the new style
     *
     * @throws IllegalArgumentException if the UUID is not a valid string
     */
    BaseStyle(@NonNull final String uuid,
              final long id) {
        this.uuid = uuid;
        this.id = id;

        // Sanity check
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException(ERROR_UUID_IS_EMPTY);
        }
    }

    /**
     * Constructor. Load the style data from the database.
     * <p>
     * Used by {@link StyleType#Global} and {@link StyleType#User},
     * but NOT by {@link StyleType#Builtin}.
     *
     * @param rowData to use
     *
     * @throws IllegalArgumentException if the UUID is not a valid string
     */
    BaseStyle(@NonNull final DataHolder rowData) {
        uuid = rowData.getString(DBKey.STYLE_UUID);
        id = rowData.getLong(DBKey.PK_ID);

        // Sanity check
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException(ERROR_UUID_IS_EMPTY);
        }

        preferred = rowData.getBoolean(DBKey.STYLE_IS_PREFERRED);
        menuPosition = rowData.getInt(DBKey.STYLE_MENU_POSITION);

        // 'simple' options
        layout = Layout.byId(rowData.getInt(DBKey.STYLE_LAYOUT));
        coverClickAction = CoverClickAction.byId(rowData.getInt(DBKey.STYLE_COVER_CLICK_ACTION));
        coverScale = rowData.getInt(DBKey.STYLE_COVER_SCALE);
        textScale = rowData.getInt(DBKey.STYLE_TEXT_SCALE);
        groupRowUsesPreferredHeight = rowData.getBoolean(DBKey.STYLE_ROW_USES_PREF_HEIGHT);

        setHeaderFieldVisibility(rowData.getInt(DBKey.STYLE_LIST_HEADER));
        setBookLevelFieldsOrderBy(StyleCoder.decodeBookLevelFieldsOrderBy(
                rowData.getString(DBKey.STYLE_BOOK_LEVEL_FIELDS_ORDER_BY)));

        fieldVisibility.put(FieldVisibility.Screen.List, new BookLevelFieldVisibility(
                rowData.getLong(DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY)));
        fieldVisibility.put(FieldVisibility.Screen.Detail, new BookDetailsFieldVisibility(
                rowData.getLong(DBKey.STYLE_DETAILS_SHOW_FIELDS)));

        sortAuthorByGivenName = rowData.getBoolean(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME);
        showAuthorByGivenName = rowData.getBoolean(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME);

        // groups
        expansionLevel = rowData.getInt(DBKey.STYLE_EXP_LEVEL);
        final String groupsAsCsv = rowData.getString(DBKey.STYLE_GROUPS);
        if (!groupsAsCsv.isEmpty()) {
            List<Integer> groupIds;
            try {
                groupIds = Arrays.stream(groupsAsCsv.split(","))
                                 .map(Integer::parseInt)
                                 .collect(Collectors.toList());
            } catch (@NonNull final NumberFormatException ignore) {
                // we should never get here... flw... try to recover.
                groupIds = List.of(BooklistGroup.AUTHOR);
            }
            setGroupIds(groupIds);
        }
        // group-options
        setPrimaryAuthorType(rowData.getInt(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE));
        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooksUnderEachGroup(item.getGroupId(), rowData.getBoolean(item.getDbKey()));
        }
    }

    /**
     * Copy all the <strong>non-group</strong> options
     * from the given style into this style.
     *
     * @param from to copy from
     */
    void copyNonGroupSettings(@NonNull final Style from) {
        preferred = from.isPreferred();
        menuPosition = from.getMenuPosition();

        // 'simple' options
        layout = from.getLayout();
        coverClickAction = from.getCoverClickAction();
        coverScale = from.getCoverScale();
        textScale = from.getTextScale();
        groupRowUsesPreferredHeight = from.isGroupRowUsesPreferredHeight();

        setHeaderFieldVisibility(from.getHeaderFieldVisibilityValue());
        setBookLevelFieldsOrderBy(new LinkedHashMap<>(from.getBookLevelFieldsOrderBy()));

        for (final FieldVisibility.Screen screen : FieldVisibility.Screen.values()) {
            final Set<String> keys = from.getFieldVisibilityKeys(screen, true);
            final long value = from.getFieldVisibilityValue(screen);
            fieldVisibility.put(screen, new FieldVisibility(keys, value));
        }

        showAuthorByGivenName = from.isShowAuthorByGivenName();
        sortAuthorByGivenName = from.isSortAuthorByGivenName();
    }

    /**
     * Copy all the <strong>group</strong> options (but not he actual groups)
     * from the given style into this style.
     *
     * @param from to copy from
     */
    void copyGroupOptions(@NonNull final Style from) {
        setGroupList(from.getGroupList());
        // group-options
        setExpansionLevel(from.getExpansionLevel());
        setPrimaryAuthorType(from.getPrimaryAuthorType());
        for (final UnderEach item : UnderEach.values()) {
            final int groupId = item.getGroupId();
            setShowBooksUnderEachGroup(groupId, from.isShowBooksUnderEachGroup(groupId));
        }
    }

    /**
     * Construct a clone of this Style with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return a new {@link WritableStyle} instance
     */
    @Override
    @NonNull
    public WritableStyle clone(@NonNull final Context context) {
        // A cloned style is *always* a UserStyle regardless of the original type
        //noinspection ClassReferencesSubclass
        final UserStyle cloned = new UserStyle(UUID.randomUUID().toString(), 0);
        cloned.setName(this.getLabel(context));

        cloned.copyNonGroupSettings(this);
        cloned.setGroupList(this.getGroupList());
        cloned.copyGroupOptions(this);

        return cloned;
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
    @NonNull
    public CoverClickAction getCoverClickAction() {
        return coverClickAction;
    }

    public void setCoverClickAction(@NonNull final CoverClickAction coverClickAction) {
        this.coverClickAction = coverClickAction;
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

    @Override
    @BooklistHeader.Option
    public int getHeaderFieldVisibilityValue() {
        return headerFieldVisibility;
    }

    public void setHeaderFieldVisibility(@BooklistHeader.Option final int bitmask) {
        headerFieldVisibility = bitmask & BooklistHeader.BITMASK_ALL;
    }

    @Override
    public boolean isShowField(@NonNull final FieldVisibility.Screen screen,
                               @NonNull final String dbKey) {
        // First check the style itself,
        // but if a field not defined on the respective FieldVisibility, use the global.
        //noinspection DataFlowIssue
        return fieldVisibility.get(screen).isVisible(dbKey).orElseGet(
                () -> ServiceLocator.getInstance().isFieldEnabled(dbKey));
    }

    @NonNull
    public Map<String, Sort> getBookLevelFieldsOrderBy() {
        return Map.copyOf(bookLevelFieldsOrderBy);
    }

    public void setBookLevelFieldsOrderBy(@NonNull final Map<String, Sort> map) {
        bookLevelFieldsOrderBy.clear();
        bookLevelFieldsOrderBy.putAll(map);
        // add any fields with their default which might be missing.
        BOOK_LEVEL_FIELDS_DEFAULTS.forEach(bookLevelFieldsOrderBy::putIfAbsent);
    }

    @NonNull
    @Override
    public Set<String> getFieldVisibilityKeys(@NonNull final FieldVisibility.Screen screen,
                                              final boolean all) {
        //noinspection DataFlowIssue
        return fieldVisibility.get(screen).getKeys(all);
    }

    @Override
    public long getFieldVisibilityValue(@NonNull final FieldVisibility.Screen screen) {
        //noinspection DataFlowIssue
        return fieldVisibility.get(screen).getBitValue();
    }

    public void setFieldVisibility(@NonNull final FieldVisibility.Screen screen,
                                   final long bitmask) {
        //noinspection DataFlowIssue
        fieldVisibility.get(screen).setBitValue(bitmask);
    }

    public void setFieldVisibility(@NonNull final FieldVisibility.Screen screen,
                                   @NonNull final String dbKey,
                                   final boolean show) {
        //noinspection DataFlowIssue
        fieldVisibility.get(screen).setVisible(dbKey, show);
    }

    @Override
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
        return List.copyOf(groups.values());
    }

    public void setGroupList(@Nullable final List<BooklistGroup> list) {
        groups.clear();
        if (list != null) {
            list.forEach(group -> groups.put(group.getId(), group));
        }
    }

    public void setGroupIds(@NonNull final List<Integer> groupIds) {
        setGroupList(BooklistGroup.createList(groupIds, this));
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
     * @return bitmask representing the type of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorType() {
        return getGroupById(BooklistGroup.AUTHOR)
                .map(group -> ((AuthorBooklistGroup) group).getPrimaryType())
                .orElse(Author.TYPE_UNKNOWN);
    }

    /**
     * Set the primary Author type value for the {@link BooklistGroup#AUTHOR} group.
     * If this style does not have the group, this method does nothing.
     *
     * @param type to set
     */
    public void setPrimaryAuthorType(@Author.Type final int type) {
        getGroupById(BooklistGroup.AUTHOR)
                .ifPresent(group -> ((AuthorBooklistGroup) group).setPrimaryType(type));
    }

    /**
     * Set the {@link UnderEach} value for the given group.
     * If this style does not have the group, this method does nothing.
     *
     * @param groupId to set
     * @param value   to set
     */
    public void setShowBooksUnderEachGroup(@BooklistGroup.Id final int groupId,
                                           final boolean value) {
        getGroupById(groupId)
                .ifPresent(group -> ((UnderEachGroup) group).setShowBooksUnderEach(value));
    }

    /**
     * Wrapper that gets the show-book-under-each for the given wrapped group (if we have it);
     * or by default {@code false}.
     *
     * @param groupId the {@link BooklistGroup} id
     *
     * @return {@code true} if we want to show a book under each of the given group (id)
     */
    @Override
    public boolean isShowBooksUnderEachGroup(@BooklistGroup.Id final int groupId) {
        return getGroupById(groupId)
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

               && LinkedMap.equals(groups, style.groups)
               && LinkedMap.equals(bookLevelFieldsOrderBy, style.bookLevelFieldsOrderBy)

               && layout == style.layout
               && coverClickAction == style.coverClickAction
               && expansionLevel == style.expansionLevel
               && headerFieldVisibility == style.headerFieldVisibility
               && groupRowUsesPreferredHeight == style.groupRowUsesPreferredHeight
               && coverScale == style.coverScale
               && textScale == style.textScale

               && showAuthorByGivenName == style.showAuthorByGivenName
               && sortAuthorByGivenName == style.sortAuthorByGivenName

               && Objects.equals(fieldVisibility, style.fieldVisibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, menuPosition, preferred,
                            groups,
                            bookLevelFieldsOrderBy,

                            layout,
                            coverClickAction,
                            expansionLevel,
                            headerFieldVisibility,
                            groupRowUsesPreferredHeight,
                            coverScale,
                            textScale,

                            showAuthorByGivenName,
                            sortAuthorByGivenName,

                            fieldVisibility);
    }

    @Override
    @NonNull
    public String toString() {
        return "BaseStyle{"
               + "id=" + id
               + ", uuid=`" + uuid + '`'
               + ", menuPosition=" + menuPosition
               + ", preferred=" + preferred
               + ", groups=" + groups
               + ", bookLevelFieldsOrderBy=" + bookLevelFieldsOrderBy

               + ", layout=" + layout
               + ", coverClickAction=" + coverClickAction
               + ", expansionLevel=" + expansionLevel
               + ", headerFieldVisibility=" + headerFieldVisibility
               + ", groupRowUsesPreferredHeight=" + groupRowUsesPreferredHeight
               + ", coverScale=" + coverScale
               + ", textScale=" + textScale

               + ", showAuthorByGivenName=" + showAuthorByGivenName
               + ", sortAuthorByGivenName=" + sortAuthorByGivenName

               + ", fieldVisibility=" + fieldVisibility
               + '}';
    }
}
