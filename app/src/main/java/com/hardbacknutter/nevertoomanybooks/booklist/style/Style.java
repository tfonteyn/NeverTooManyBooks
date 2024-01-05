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

import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public interface Style {

    /**
     * The (arbitrary) position for a style which is not on the user preferred style list.
     * i.e. it's at the very end.
     */
    int MENU_POSITION_NOT_PREFERRED = 1000;

    /**
     * A Style <strong>UUID</strong>.
     * <p>
     * <br>type: {@code String}
     */
    String BKEY_UUID = "Style:uuid";

    /**
     * Text Scaling.
     * <strong>Never change these values</strong>, they get stored in the db.
     * The book title in the list is by default 'medium' (see styles.xml)
     * Other elements are always 1 size 'less' than the title.
     * <p>
     * These values are used as the index into a resource array.
     *
     * @see com.hardbacknutter.nevertoomanybooks.R.array#bob_text_size_in_sp
     * @see com.hardbacknutter.nevertoomanybooks.R.array#bob_text_padding_in_percent
     */
    int TEXT_SCALE_0_VERY_SMALL = 0;
    int TEXT_SCALE_1_SMALL = 1;
    int TEXT_SCALE_2_MEDIUM = 2;
    int TEXT_SCALE_3_LARGE = 3;
    int TEXT_SCALE_4_VERY_LARGE = 4;

    int DEFAULT_TEXT_SCALE = TEXT_SCALE_2_MEDIUM;

    /**
     * Cover Scaling.
     * <strong>Never change these values</strong>, they get stored in the db.
     * <p>
     * These values are used as the index into a resource array.
     *
     * @see com.hardbacknutter.nevertoomanybooks.R.array#cover_book_list_longest_side
     */
    int COVER_SCALE_HIDDEN = 0;
    int COVER_SCALE_SMALL = 1;
    int COVER_SCALE_MEDIUM = 2;
    int COVER_SCALE_LARGE = 3;
    int DEFAULT_COVER_SCALE = COVER_SCALE_MEDIUM;

    @SuppressWarnings("ClassReferencesSubclass")
    @NonNull
    WritableStyle clone(@NonNull Context context);

    /**
     * Get the id.
     * <ul>
     *      <li>Positive ID's: user-defined styles</li>
     *      <li>Negative ID's: builtin styles</li>
     *      <li>0: a user-defined style which has not been saved yet</li>
     * </ul>
     *
     * @return id
     */
    long getId();

    /**
     * Set the id.
     *
     * @param id to set
     */
    void setId(long id);

    /**
     * Get the UUID for this style.
     *
     * @return the UUID
     */
    @NonNull
    String getUuid();

    /**
     * Get the type of this style.
     *
     * @return type
     */
    @NonNull
    StyleType getType();

    /**
     * Get the label to use. This is for <strong>displaying only</strong>.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    @NonNull
    String getLabel(@NonNull Context context);

    /**
     * Get the menu position of this style as sorted by the user.
     *
     * @return menuPosition
     */
    int getMenuPosition();

    /**
     * Set the menu position of this style as sorted by the user.
     *
     * @param menuPosition to set
     */
    void setMenuPosition(int menuPosition);

    /**
     * Check if this is a user preferred style.
     *
     * @return flag
     */
    boolean isPreferred();

    /**
     * Set this style as a user preferred style.
     *
     * @param preferred flag
     */
    void setPreferred(boolean preferred);

    /**
     * Get the default visible level for the list.
     * i.e. the level which will be visible but not expanded.
     * i.o.w. the top-level where items above will be expanded/visible,
     * and items below will be hidden.
     *
     * @return level
     */
    @IntRange(from = 1)
    int getExpansionLevel();

    /**
     * Get the layout as set on the style.
     *
     * @return layout
     */
    @NonNull
    Layout getLayout();

    /**
     * Get the layout as set on the style, potentially overriding it
     * depending on the given flag.
     * <p>
     * <strong>Do not use this method for storing the layout!</strong>
     *
     * @param hasEmbeddedDetailsFrame whether the display Activity is showing
     *                                the embedded details-frame.
     *
     * @return layout
     */
    @NonNull
    default Layout getLayout(final boolean hasEmbeddedDetailsFrame) {
        if (hasEmbeddedDetailsFrame) {
            // We tested with using the grid when having the embedded frame,
            // but it was just to confusing and a mess to look at.
            // Hence just use the list layout in this setup.
            return Style.Layout.List;
        } else {
            return getLayout();
        }
    }

    /**
     * Get the action to take when the user taps the cover image in the BoB list.
     *
     * @return action
     */
    @NonNull
    CoverClickAction getCoverClickAction();

    /**
     * Get the action to take when the user long-clicks a cover image in the BoB list.
     *
     * @return action
     */
    @NonNull
    CoverLongClickAction getCoverLongClickAction();

    /**
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @return {@code true} if the Given name should be displayed before the Family name
     */
    boolean isShowAuthorByGivenName();

    boolean isShowReorderedTitle();

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @return {@code true} if the Given name should be sorted before then the Family name
     */
    boolean isSortAuthorByGivenName();


    /**
     * Get the text scale <strong>identifier</strong> used by the style.
     *
     * @return scale
     */
    @Style.TextScale
    int getTextScale();

    /**
     * Get the cover scale <strong>identifier</strong> used by the style.
     * <p>
     * Used for user preferences and stored in the database.
     *
     * @return scale
     */
    @Style.CoverScale
    int getCoverScale();

    /**
     * Calculate the scaled cover maximum size in pixels.
     * <p>
     * Uses the {@link #getCoverScale()} identifier to lookup an a
     * resource value in {code dp} and returns that as an amount of pixels.
     *
     * @param context Current context
     *
     * @return max size in pixels
     *
     * @see #getCoverMaxSizeInPixels(Context, Layout)
     */
    @Dimension
    int getCoverMaxSizeInPixels(@NonNull Context context);

    /**
     * Calculate the scaled cover maximum size in pixels.
     * <p>
     * Uses the {@link #getCoverScale()} identifier to lookup an a
     * resource value in {code dp} and returns that as an amount of pixels.
     *
     * @param context Current context
     * @param layout  overrule the layout-setting of the style
     *
     * @return max size in pixels
     *
     * @see #getCoverMaxSizeInPixels(Context)
     */
    @Dimension
    int getCoverMaxSizeInPixels(@NonNull Context context,
                                @NonNull Layout layout);

    /**
     * Check if the style wants the specified header to be displayed.
     *
     * @param bit to check
     *
     * @return {@code true} if the header should be shown
     */
    boolean isShowHeaderField(@BooklistHeader.Option int bit);

    /**
     * Get the bitmap value with the list header fields to show.
     *
     * @return bitmask
     */
    @BooklistHeader.Option
    int getHeaderFieldVisibilityValue();

    /**
     * Check if the given field should be displayed.
     *
     * @param screen to get the setting for
     * @param dbKey  to check - one of the {@link DBKey} constants.
     *
     * @return {@code true} if in use
     */
    boolean isShowField(@NonNull FieldVisibility.Screen screen,
                        @NonNull String dbKey);

    /**
     * Get the visibility keys for the given screen.
     *
     * @param screen to get the setting for
     * @param all    set to {@code true} to get all keys regardless of visibility;
     *               Use {@code false} to get only the currently visible fields (keys).
     *
     * @return a new unmodifiable Set
     *
     * @see FieldVisibility#getKeys(boolean)
     */
    @NonNull
    Set<String> getFieldVisibilityKeys(@NonNull FieldVisibility.Screen screen,
                                       boolean all);

    /**
     * Get the visibility bitmask value for the given screen.
     *
     * @param screen to get the setting for
     *
     * @return bitmask
     *
     * @see FieldVisibility#getBitValue()
     */
    long getFieldVisibilityValue(@NonNull FieldVisibility.Screen screen);

    /**
     * Get the fields and how to sort on them for the lowest level (books) in the BoB.
     *
     * @return an <strong>unmodifiable</strong> map
     */
    @NonNull
    Map<String, Sort> getBookLevelFieldsOrderBy();

    /**
     * Should rows be shown using the system's preferred height or minimum height.
     *
     * @return {@code true} for "?attr/listPreferredItemHeightSmall"
     *         or {@code false} for {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}
     */
    boolean isGroupRowUsesPreferredHeight();

    /**
     * Get the number of groups in this style.
     *
     * @return the number of groups
     */
    int getGroupCount();

    /**
     * Check if the given group is present, using the given group id.
     *
     * @param id group id
     *
     * @return {@code true} if present
     */
    boolean hasGroup(@BooklistGroup.Id int id);

    /**
     * Get the group for the given id.
     *
     * @param id to get
     *
     * @return Optional with the group
     */
    @NonNull
    Optional<BooklistGroup> getGroupById(@BooklistGroup.Id int id);

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
    default BooklistGroup requireGroupById(@BooklistGroup.Id final int id)
            throws NullPointerException {
        return getGroupById(id).orElseThrow(() -> new NullPointerException(
                "Missing group: id=" + id + ", " + getUuid()));
    }

    /**
     * Get the group at the given level.
     *
     * @param level to get
     *
     * @return group
     *
     * @throws IndexOutOfBoundsException on bug
     */
    @NonNull
    BooklistGroup getGroupByLevel(@IntRange(from = 1) int level)
            throws IndexOutOfBoundsException;

    /**
     * Get all groups assigned to this style.
     *
     * @return an <strong>unmodifiable</strong> List
     */
    @NonNull
    List<BooklistGroup> getGroupList();

    /**
     * {@link AuthorBooklistGroup} property.
     *
     * @return bitmask representing the type of author we consider the primary author
     */
    @Author.Type
    int getPrimaryAuthorType();

    /**
     * Should books be shown under each of the given row type.
     *
     * @param groupId the {@link BooklistGroup} id
     *
     * @return flag
     */
    boolean isShowBooksUnderEachGroup(@BooklistGroup.Id int groupId);

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the in-use group names as a CSV String.
     *
     * @param context Current context
     *
     * @return summary text
     */
    @NonNull
    String getGroupsSummaryText(@NonNull Context context);

    enum Layout {
        /** Original list-style. */
        List(0),
        /** New for 5.0 release. */
        Grid(1);

        private final int id;

        Layout(final int id) {
            this.id = id;
        }

        @NonNull
        public static Layout byId(final int id) {
            if (id == 0) {
                return List;
            }
            return Grid;
        }

        public int getId() {
            return id;
        }
    }

    enum CoverClickAction {
        /** Tapping the cover image will invoke a zoom dialog with the cover. */
        Zoom(0),
        /** Tapping the cover image will trigger opening the book detail page. */
        OpenBookDetails(1);

        private final int id;

        CoverClickAction(final int id) {
            this.id = id;
        }

        @NonNull
        public static CoverClickAction byId(final int id) {
            if (id == 0) {
                return Zoom;
            }
            return OpenBookDetails;
        }

        public int getId() {
            return id;
        }
    }

    enum CoverLongClickAction {
        /** Ignore long-click on a cover. */
        Ignore(0),
        /** Open the book menu as a popup. */
        PopupMenu(1);

        private final int id;

        CoverLongClickAction(final int id) {
            this.id = id;
        }

        @NonNull
        public static CoverLongClickAction byId(final int id) {
            if (id == 0) {
                return Ignore;
            }
            return PopupMenu;
        }

        public int getId() {
            return id;
        }
    }
    /**
     * See {@link #isShowBooksUnderEachGroup(int)}.
     */
    enum UnderEach {
        /** {@link AuthorBooklistGroup}. */
        Author(BooklistGroup.AUTHOR,
               DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
               "style.booklist.group.authors.show.all"
        ),

        /** {@link SeriesBooklistGroup} . */
        Series(BooklistGroup.SERIES,
               DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
               "style.booklist.group.series.show.all"
        ),

        /** {@link PublisherBooklistGroup}. */
        Publisher(BooklistGroup.PUBLISHER,
                  DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                  "style.booklist.group.publisher.show.all"
        ),

        /** {@link BookshelfBooklistGroup}. */
        Bookshelf(BooklistGroup.BOOKSHELF,
                  DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,
                  "style.booklist.group.bookshelf.show.all"
        );

        @NonNull
        private final String dbKey;
        @NonNull
        private final String prefKey;
        @BooklistGroup.Id
        private final int groupId;

        UnderEach(@BooklistGroup.Id final int groupId,
                  @NonNull final String dbKey,
                  @NonNull final String prefKey) {
            this.groupId = groupId;
            this.dbKey = dbKey;
            this.prefKey = prefKey;
        }

        @Nullable
        static UnderEach findByPrefKey(@NonNull final String prefKey) {
            return Arrays.stream(values())
                         .filter(v -> v.getPrefKey().equals(prefKey))
                         .findFirst()
                         .orElse(null);
        }

        /**
         * Get the {@link DBKey} for this element.
         *
         * @return key
         */
        @NonNull
        public String getDbKey() {
            return dbKey;
        }

        /**
         * Get the Preferences key for this element.
         *
         * @return key
         */
        @NonNull
        public String getPrefKey() {
            return prefKey;
        }

        /**
         * Get the {@link BooklistGroup} for this element.
         *
         * @return key
         */
        @SuppressWarnings("WeakerAccess")
        @BooklistGroup.Id
        public int getGroupId() {
            return groupId;
        }
    }

    @IntDef({TEXT_SCALE_0_VERY_SMALL,
            TEXT_SCALE_1_SMALL,
            TEXT_SCALE_2_MEDIUM,
            TEXT_SCALE_3_LARGE,
            TEXT_SCALE_4_VERY_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    @interface TextScale {

    }

    @IntDef({COVER_SCALE_HIDDEN,
            COVER_SCALE_SMALL,
            COVER_SCALE_MEDIUM,
            COVER_SCALE_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    @interface CoverScale {

    }
}
