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

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
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
     */
    int COVER_SCALE_HIDDEN = 0;
    int COVER_SCALE_SMALL = 1;
    int COVER_SCALE_MEDIUM = 2;
    int COVER_SCALE_LARGE = 3;

    int DEFAULT_COVER_SCALE = COVER_SCALE_MEDIUM;

    boolean isUserDefined();

    @SuppressWarnings("ClassReferencesSubclass")
    @NonNull
    UserStyle clone(@NonNull Context context);

    /**
     * Get the database row id of the entity.
     *
     * @return id
     */
    long getId();

    void setId(long id);

    /**
     * Get a short description of the type of this style.
     *
     * @param context Current context
     *
     * @return description
     */
    @NonNull
    String getTypeDescription(@NonNull Context context);

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
     * Get the UUID for this style.
     *
     * @return the UUID
     */
    @NonNull
    String getUuid();

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
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @return {@code true} when Given names should come first
     */
    boolean isShowAuthorByGivenName();

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @return {@code true} when Given names should come first
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
     *
     * @return scale
     */
    @Style.CoverScale
    int getCoverScale();


    /**
     * Check if the style wants the specified header to be displayed.
     *
     * @param bit to check
     *
     * @return {@code true} if the header should be shown
     */
    boolean isShowHeaderField(@BooklistHeader.Option int bit);

    /**
     * Check if the given field should be displayed.
     *
     * @param screen to get the setting for
     * @param dbKey  to check - one of the {@link DBKey} constants.
     *
     * @return {@code true} if in use
     */
    boolean isShowField(@NonNull Screen screen,
                        @NonNull String dbKey);

    /**
     * Get the bitmask value which defines book-field visibility.
     *
     * @param screen to get the setting for
     *
     * @return bitmask
     */
    long getFieldVisibility(@NonNull Screen screen);

    /**
     * Get the group row <strong>height</strong> to be applied to
     * the {@link android.view.ViewGroup.LayoutParams}.
     *
     * @param context Current context
     *
     * @return group row height value in pixels
     */
    int getGroupRowHeight(@NonNull Context context);

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
     * @return new List
     */
    @NonNull
    List<BooklistGroup> getGroupList();


    /**
     * {@link AuthorBooklistGroup} property.
     *
     * @return bitmask
     */
    @Author.Type
    int getPrimaryAuthorType();


    /**
     * Should books be shown under each of the given row type.
     *
     * @param underEach row type
     *
     * @return flag
     */
    boolean isShowBooks(@NonNull UnderEach underEach);

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

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the visible fields as a CSV String.
     *
     * @param context Current context
     * @param screen  to get the setting for
     *
     * @return summary text
     */
    @NonNull
    String getFieldVisibilitySummaryText(@NonNull Context context,
                                         @NonNull Screen screen);


    enum Screen {
        List,
        Detail
    }

    /**
     * See {@link #isShowBooks(UnderEach)}.
     */
    enum UnderEach {
        /** {@link AuthorBooklistGroup}. */
        Author(BooklistGroup.AUTHOR,
               DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
               StyleDataStore.PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH
        ),

        /** {@link SeriesBooklistGroup} . */
        Series(BooklistGroup.SERIES,
               DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
               StyleDataStore.PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH
        ),

        /** {@link PublisherBooklistGroup}. */
        Publisher(BooklistGroup.PUBLISHER,
                  DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                  StyleDataStore.PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH
        ),

        /** {@link BookshelfBooklistGroup}. */
        Bookshelf(BooklistGroup.BOOKSHELF,
                  DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,
                  StyleDataStore.PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH
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

        @NonNull
        public String getDbKey() {
            return dbKey;
        }

        @NonNull
        public String getPrefKey() {
            return prefKey;
        }

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
