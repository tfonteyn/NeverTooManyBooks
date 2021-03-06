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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;

public interface ListStyle {

    /**
     * the amount of details to show in the header.
     * NEVER change these values, they get stored in preferences.
     * <p>
     * not in use: 1 << 2
     */
    int HEADER_SHOW_BOOK_COUNT = 1;
    /** the amount of details to show in the header. */
    int HEADER_SHOW_STYLE_NAME = 1 << 3;
    /** the amount of details to show in the header. */
    int HEADER_SHOW_FILTER = 1 << 4;
    /** the amount of details to show in the header. */
    int HEADER_BITMASK_ALL =
            HEADER_SHOW_BOOK_COUNT
            | HEADER_SHOW_STYLE_NAME
            | HEADER_SHOW_FILTER;

    /** Thumbnail Scaling. */
    int IMAGE_SCALE_0_NOT_DISPLAYED = 0;
    /** Thumbnail Scaling. */
    int IMAGE_SCALE_1_VERY_SMALL = 1;
    /** Thumbnail Scaling. */
    int IMAGE_SCALE_2_SMALL = 2;
    /** Thumbnail Scaling. */
    int IMAGE_SCALE_3_MEDIUM = 3;
    /** Thumbnail Scaling. */
    int IMAGE_SCALE_4_LARGE = 4;
    /** Thumbnail Scaling. */
    int IMAGE_SCALE_5_VERY_LARGE = 5;

    /** Thumbnail Scaling. */
    int IMAGE_SCALE_DEFAULT = IMAGE_SCALE_3_MEDIUM;
    /**
     * The (arbitrary) position for a style which is not on the user preferred style list.
     * i.e. it's at the very end.
     */
    int MENU_POSITION_NOT_PREFERRED = 1000;

    /**
     * A ListStyle <strong>UUID</strong>.
     * <p>
     * <br>type: {@code String}
     */
    String BKEY_STYLE_UUID = "ListStyle:uuid";

    @NonNull
    StylePersistenceLayer getPersistenceLayer();

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
     * Convenience/clarity method: check if this style represents the global settings.
     *
     * @return {@code true} if global
     */
    default boolean isGlobal() {
        return getUuid().isEmpty();
    }

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
     * @param isPreferred flag
     */
    void setPreferred(boolean isPreferred);

    /**
     * Check if the style wants the specified header to be displayed.
     *
     * @param headerMask to check
     *
     * @return {@code true} if the header should be shown
     */
    boolean isShowHeader(@ListHeaderOption int headerMask);

    /**
     * Get the default visible level for the list.
     * i.e. the level which will be visible but not expanded.
     * i.o.w. the top-level where items above will be expanded/visible,
     * and items below will be hidden.
     *
     * @return level
     */
    @IntRange(from = 1)
    int getTopLevel();

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
     * Get the text style used by the style.
     *
     * @return TextScale
     */
    TextScale getTextScale();

    /**
     * Get the Groups style object.
     *
     * @return the Groups object
     */
    @NonNull
    Groups getGroups();

    /**
     * Get the Filters style object.
     *
     * @return the Filters object
     */
    @NonNull
    Filters getFilters();

    /**
     * Get the ListScreenBookFields style object.
     *
     * @return the ListScreenBookFields object
     */
    @NonNull
    ListScreenBookFields getListScreenBookFields();

    /**
     * Get the DetailScreenBookFields style object.
     *
     * @return the DetailScreenBookFields object
     */
    @NonNull
    DetailScreenBookFields getDetailScreenBookFields();

    boolean isShowBooksUnderEachSeries();

    boolean isShowBooksUnderEachPublisher();

    boolean isShowBooksUnderEachBookshelf();

    boolean isShowBooksUnderEachAuthor();

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

    int getPrimaryAuthorType();

    /**
     * Get a flat map with accumulated preferences for this object and it's children.<br>
     * Provides low-level access to all preferences.<br>
     * This should only be called for export/import.
     *
     * @return flat map
     */
    @NonNull
    Map<String, PPref<?>> getRawPreferences();

    @IntDef(flag = true, value = {HEADER_SHOW_BOOK_COUNT,
                                  HEADER_SHOW_STYLE_NAME,
                                  HEADER_SHOW_FILTER})
    @Retention(RetentionPolicy.SOURCE)
    @interface ListHeaderOption {

    }

    @IntDef({IMAGE_SCALE_0_NOT_DISPLAYED,
             IMAGE_SCALE_1_VERY_SMALL, IMAGE_SCALE_2_SMALL, IMAGE_SCALE_3_MEDIUM,
             IMAGE_SCALE_4_LARGE, IMAGE_SCALE_5_VERY_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    @interface CoverScale {

    }
}
