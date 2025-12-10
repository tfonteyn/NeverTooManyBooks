/*
 * @Copyright 2018-2025 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.UnderEachGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.citations.CitationType;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.utils.LinkedMap;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public abstract class BaseStyle
        implements Style {

    private static final String ERROR_UUID_IS_EMPTY = "uuid.isEmpty()";

    /**
     * IMPORTANT: this is the ALMOST the same set as used by {@link BookLevelFieldVisibility}
     * and should be kept in sync.
     * but note the differences:
     * <ul>
     *     <li>TITLE is added here;
     *         We ALWAYS display it.</li>
     *     <li>ISBN & LANGUAGE are removed here;
     *         We already add these during BooklistBuilder setup.</li>
     * </ul>
     * Also note this is an <strong>ORDERED LIST!</strong>
     */
    private static final Map<String, Sort> BOOK_LEVEL_FIELDS_DEFAULTS = new LinkedHashMap<>();

    /*
     * NEWTHINGS: BookLevelField: Keys must be kept in sync with
     *  {@link StyleDataStore} preference keys
     *  com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle BOOK_LEVEL_FIELDS_DEFAULTS
     *  "res/xml/preferences_style_book_details.xml"
     */
    static {
        // The default is sorting by book title only
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TITLE, Sort.Asc);

        // The field order here is assuming the user will need to sort more likely
        // on the fields listed at the top.
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TRANSLATION_ORIGINAL_TITLE, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_AUTHOR, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_SERIES, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_PUBLISHER, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.PUBLICATION_DATE, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FIRST_PUBLICATION_DATE, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FORMAT, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOCATION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.RATING, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.READ_PROGRESS, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.PAGES, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.CONDITION_BOOK, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.SIGNED__BOOL, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.EDITION, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LOANEE_NAME, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.FK_BOOKSHELF, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.LANGUAGE, Sort.Unsorted);

        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.DATE_ADDED__UTC, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.DATE_LAST_UPDATED__UTC, Sort.Unsorted);
        BOOK_LEVEL_FIELDS_DEFAULTS.put(DBKey.DATE_ACQUIRED, Sort.Unsorted);
    }

    /** Configuration for the fields shown on the given {@link FieldVisibility.Screen}. */
    @NonNull
    final Map<FieldVisibility.Screen, FieldVisibility> fieldVisibility =
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
    private ScreenLayout layout = ScreenLayout.List;

    /**
     * Tapping a cover image in the BoB list-mode/grid-mode.
     */
    @NonNull
    private CoverClickAction coverClickAction = CoverClickAction.Zoom;
    /**
     * Long-click a cover image in the BoB grid-mode.
     * Not used for the BoB list-mode.
     */
    @NonNull
    private CoverLongClickAction coverLongClickAction = CoverLongClickAction.Ignore;

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
    @NonNull
    private TextScale textScale = TextScale.DEFAULT;
    /** Relative scaling factor for covers on the list screen. */
    @NonNull
    private CoverScale coverScale = CoverScale.DEFAULT;
    /** Local override. */
    private boolean sortAuthorByGivenName;
    /** Local override. */
    private boolean showAuthorByGivenName;
    private boolean showReorderedTitle;
    private boolean showGroupBookCount = true;

    private boolean useReadProgress;

    @NonNull
    private CitationType citationType = CitationType.Default;

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
    private boolean groupRowUsesPreferredHeight = true;

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
     * Used by {@link Style.Type#Global} and {@link Style.Type#User},
     * but NOT by {@link Style.Type#Builtin}.
     *
     * @param rowData to use
     *
     * @throws IllegalArgumentException if the UUID is not a valid string
     */
    BaseStyle(@NonNull final DataHolder rowData) {
        uuid = rowData.getString(DBKey.STYLE.UUID);
        id = rowData.getLong(DBKey.PK_ID);

        // Sanity check
        if (uuid.isEmpty()) {
            throw new IllegalArgumentException(ERROR_UUID_IS_EMPTY);
        }

        preferred = rowData.getBoolean(DBKey.STYLE.IS_PREFERRED);
        menuPosition = rowData.getInt(DBKey.STYLE.MENU_POSITION);

        //NEWTHINGS: style option: read from rowData

        // 'simple' options
        setLayout(ScreenLayout.byId(rowData.getInt(DBKey.STYLE.LAYOUT)));
        setCoverClickAction(Style.CoverClickAction.byId(
                rowData.getInt(DBKey.STYLE.COVER_CLICK_ACTION)));
        setCoverLongClickAction(Style.CoverLongClickAction.byId(
                rowData.getInt(DBKey.STYLE.COVER_LONG_CLICK_ACTION)));
        setCoverScale(CoverScale.byId(rowData.getInt(DBKey.STYLE.COVER_SCALE)));
        setTextScale(TextScale.byId(rowData.getInt(DBKey.STYLE.TEXT_SCALE)));
        setCitationType(CitationType.byId(rowData.getInt(DBKey.STYLE.CITATION_TYPE)));

        groupRowUsesPreferredHeight = rowData.getBoolean(DBKey.STYLE.ROW_USES_PREF_HEIGHT);

        setHeaderFieldVisibility(rowData.getInt(DBKey.STYLE.LIST_HEADER));
        setBookLevelFieldsOrderBy(StyleCoder.decodeBookLevelFieldsOrderBy(
                rowData.getString(DBKey.STYLE.BOOK_LIST_FIELD_ORDER_BY)));

        fieldVisibility.put(FieldVisibility.Screen.List, new BookLevelFieldVisibility(
                rowData.getLong(DBKey.STYLE.BOOK_LIST_FIELD_VISIBILITY)));
        fieldVisibility.put(FieldVisibility.Screen.Detail, new BookDetailsFieldVisibility(
                rowData.getLong(DBKey.STYLE.BOOK_DETAIL_FIELD_VISIBILITY)));

        sortAuthorByGivenName = rowData.getBoolean(DBKey.STYLE.AUTHOR_SORT_BY_GIVEN_NAME);

        showAuthorByGivenName = rowData.getBoolean(DBKey.STYLE.AUTHOR_SHOW_BY_GIVEN_NAME);
        showReorderedTitle = rowData.getBoolean(DBKey.STYLE.TITLE_SHOW_REORDERED);

        showGroupBookCount = rowData.getBoolean(DBKey.STYLE.SHOW_GROUP_BOOK_COUNT);

        useReadProgress = rowData.getBoolean(DBKey.STYLE.READ_STATUS_WITH_PROGRESS);

        // groups
        expansionLevel = rowData.getInt(DBKey.STYLE.EXP_LEVEL);
        final String groupsAsCsv = rowData.getString(DBKey.STYLE.GROUPS);
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
        setPrimaryAuthorRole(rowData.getInt(DBKey.STYLE.GROUPS_AUTHOR_PRIMARY_ROLE));
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
    void copyBasicSettings(@NonNull final Style from) {
        preferred = from.isPreferred();
        menuPosition = from.getMenuPosition();

        // 'simple' options
        layout = from.getLayout();
        coverClickAction = from.getCoverClickAction();
        coverLongClickAction = from.getCoverLongClickAction();
        coverScale = from.getCoverScale();
        textScale = from.getTextScale();
        citationType = from.getCitationType();
        groupRowUsesPreferredHeight = from.isGroupRowUsesPreferredHeight();

        setHeaderFieldVisibility(from.getHeaderFieldVisibilityValue());
        setBookLevelFieldsOrderBy(new LinkedHashMap<>(from.getBookLevelFieldsOrderBy()));

        for (final FieldVisibility.Screen screen : FieldVisibility.Screen.values()) {
            final Set<String> keys = from.getFieldVisibilityKeys(screen, true);
            final long value = from.getFieldVisibilityValue(screen);
            fieldVisibility.put(screen, new FieldVisibility(keys, value));
        }

        sortAuthorByGivenName = from.isSortAuthorByGivenName();
        showAuthorByGivenName = from.isShowAuthorByGivenName();
        showReorderedTitle = from.isShowReorderedTitle();

        useReadProgress = from.useReadProgress();
    }

    /**
     * Copy all the <strong>group</strong> options (but not he actual groups)
     * from the given style into this style.
     *
     * @param from to copy from
     */
    void copyGroupOptions(@NonNull final Style from) {
        // group-options
        setExpansionLevel(from.getExpansionLevel());
        setPrimaryAuthorRole(from.getPrimaryAuthorRole());
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
        return new UserStyle(context, this);
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
     *      <li>Negative ID's: built-in styles</li>
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
    public ScreenLayout getLayout() {
        return layout;
    }

    @Override
    public void setLayout(@NonNull final ScreenLayout layout) {
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
    @NonNull
    public CoverLongClickAction getCoverLongClickAction() {
        return coverLongClickAction;
    }

    public void setCoverLongClickAction(@NonNull final CoverLongClickAction coverLongClickAction) {
        this.coverLongClickAction = coverLongClickAction;
    }

    @Override
    public boolean isShowAuthorByGivenName() {
        return showAuthorByGivenName;
    }

    public void setShowAuthorByGivenName(final boolean value) {
        showAuthorByGivenName = value;
    }

    @Override
    public boolean isShowReorderedTitle() {
        return showReorderedTitle;
    }

    public void setShowReorderedTitle(final boolean value) {
        showReorderedTitle = value;
    }

    @Override
    public boolean isSortAuthorByGivenName() {
        return sortAuthorByGivenName;
    }

    public void setSortAuthorByGivenName(final boolean value) {
        sortAuthorByGivenName = value;
    }

    @Override
    public boolean useReadProgress() {
        return useReadProgress;
    }

    public void setUseReadProgress(final boolean useReadProgress) {
        this.useReadProgress = useReadProgress;
    }

    @NonNull
    @Override
    public TextScale getTextScale() {
        return textScale;
    }

    public void setTextScale(@NonNull final TextScale scale) {
        textScale = scale;
    }

    @Override
    @NonNull
    public CoverScale getCoverScale() {
        return coverScale;
    }

    public void setCoverScale(@NonNull final CoverScale coverScale) {
        this.coverScale = coverScale;
    }

    @NonNull
    @Override
    public CitationType getCitationType() {
        return citationType;
    }

    public void setCitationType(@NonNull final CitationType type) {
        this.citationType = type;
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
        // do NOT use Map.copyOf ! We'd loose the order!
        return new LinkedHashMap<>(bookLevelFieldsOrderBy);
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
        return Optional.ofNullable(groups.get(id));
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
        // Create a list with new BooklistGroup's using the list of specified ids.
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
     * Wrapper that gets the primary-author-role from the {@link BooklistGroup#AUTHOR} group
     * (if we have it); or else the default {@link AuthorRole#UNKNOWN}.
     *
     * @return bitmask representing the role of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorRole() {
        return getGroupById(BooklistGroup.AUTHOR)
                .map(group -> ((AuthorBooklistGroup) group).getPrimaryRole())
                .orElse(AuthorRole.UNKNOWN);
    }

    /**
     * Set the primary Author role value for the {@link BooklistGroup#AUTHOR} group.
     * If this style does not have the group, this method does nothing.
     *
     * @param role to set
     */
    public void setPrimaryAuthorRole(@AuthorRole.Role final int role) {
        getGroupById(BooklistGroup.AUTHOR)
                .ifPresent(group -> ((AuthorBooklistGroup) group).setPrimaryRole(role));
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
    public boolean isShowGroupBookCount() {
        return showGroupBookCount;
    }

    public void setShowGroupBookCount(final boolean value) {
        showGroupBookCount = value;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseStyle that = (BaseStyle) o;
        return id == that.id
               && uuid.equals(that.uuid)
               && menuPosition == that.menuPosition
               && preferred == that.preferred

               && LinkedMap.equals(groups, that.groups)
               && LinkedMap.equals(bookLevelFieldsOrderBy, that.bookLevelFieldsOrderBy)

               && layout == that.layout
               && coverClickAction == that.coverClickAction
               && coverLongClickAction == that.coverLongClickAction
               && expansionLevel == that.expansionLevel
               && headerFieldVisibility == that.headerFieldVisibility
               && groupRowUsesPreferredHeight == that.groupRowUsesPreferredHeight
               && coverScale == that.coverScale
               && textScale == that.textScale

               && citationType == that.citationType

               && showAuthorByGivenName == that.showAuthorByGivenName
               && showReorderedTitle == that.showReorderedTitle
               && sortAuthorByGivenName == that.sortAuthorByGivenName

               && useReadProgress == that.useReadProgress

               && Objects.equals(fieldVisibility, that.fieldVisibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, menuPosition, preferred,
                            groups,
                            bookLevelFieldsOrderBy,

                            layout,
                            coverClickAction,
                            coverLongClickAction,
                            expansionLevel,
                            headerFieldVisibility,
                            groupRowUsesPreferredHeight,
                            coverScale,
                            textScale,

                            citationType,

                            showAuthorByGivenName,
                            showReorderedTitle,

                            sortAuthorByGivenName,

                            useReadProgress,

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
               + ", coverLongClickAction=" + coverLongClickAction
               + ", expansionLevel=" + expansionLevel
               + ", headerFieldVisibility=" + headerFieldVisibility
               + ", groupRowUsesPreferredHeight=" + groupRowUsesPreferredHeight
               + ", coverScale=" + coverScale
               + ", textScale=" + textScale

               + ", citationType=" + citationType

               + ", sortAuthorByGivenName=" + sortAuthorByGivenName
               + ", showAuthorByGivenName=" + showAuthorByGivenName
               + ", showReorderedTitle=" + showReorderedTitle
               + ", showGroupBookCount=" + showGroupBookCount

               + ", useReadProgress=" + useReadProgress

               + ", fieldVisibility=" + fieldVisibility
               + '}';
    }
}
