/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * A workaround for the problematic Android MenuItem interface.
 * While the latter is an interface by name, in reality it's
 * a blackbox set of classes which are near-impossible to replace
 * with a custom implementation.
 * One of the major issues is that it does not preserve/expose the icon resource id
 * but only the icon Drawable. As a result, a MenuItem cannot be parcelled.
 * <p>
 * Use this class to lookup the icon resource id based on the manu resource id.
 * <p>
 * TODO: for now only actively used for the BottomSheet menu. Perhaps use universally?
 */
final class IconMapper {

    private static final Map<Integer, Integer> MAP = Map.ofEntries(
            // R.menu.book
            Map.entry(R.id.MENU_SYNC_LIST_WITH_DETAILS, R.drawable.arrow_back_24px),
            Map.entry(R.id.MENU_BOOK_SET_READ, R.drawable.check_box_24px),
            Map.entry(R.id.MENU_BOOK_SET_UNREAD, R.drawable.check_box_outline_blank_24px),
            Map.entry(R.id.MENU_BOOK_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_BOOK_DUPLICATE, R.drawable.content_copy_24px),
            Map.entry(R.id.MENU_BOOK_DELETE, R.drawable.delete_24px),
            Map.entry(R.id.MENU_UPDATE_FROM_INTERNET_SINGLE_BOOK, R.drawable.cloud_download_24px),
            Map.entry(R.id.MENU_BOOK_LOAN_ADD, R.drawable.group_24px),
            Map.entry(R.id.MENU_BOOK_LOAN_DELETE, R.drawable.group_24px),
            Map.entry(R.id.MENU_SHARE, R.drawable.share_24px),

            // BoB for a Book
            Map.entry(R.id.MENU_LANGUAGE_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_LOCATION_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_GENRE_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_FORMAT_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_COLOR_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_NEXT_MISSING_COVER, R.drawable.broken_image_24px),


            // R.menu.author
            Map.entry(R.id.MENU_AUTHOR_WORKS_FILTER, R.drawable.details_24px),
            Map.entry(R.id.MENU_AUTHOR_SET_COMPLETE, R.drawable.check_box_24px),
            Map.entry(R.id.MENU_AUTHOR_SET_INCOMPLETE, R.drawable.check_box_outline_blank_24px),
            Map.entry(R.id.MENU_AUTHOR_EDIT, R.drawable.edit_24px),
            // R.menu.series
            Map.entry(R.id.MENU_SERIES_SET_COMPLETE, R.drawable.check_box_24px),
            Map.entry(R.id.MENU_SERIES_SET_INCOMPLETE, R.drawable.check_box_outline_blank_24px),
            Map.entry(R.id.MENU_SERIES_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_SERIES_DELETE, R.drawable.delete_24px),
            // R.menu.publisher
            Map.entry(R.id.MENU_PUBLISHER_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_PUBLISHER_DELETE, R.drawable.delete_24px),
            // R.menu.bookshelf
            Map.entry(R.id.MENU_BOOKSHELF_EDIT, R.drawable.edit_24px),
            Map.entry(R.id.MENU_BOOKSHELF_DELETE, R.drawable.delete_24px),

            // R.menu.sm_calibre
            Map.entry(R.id.SUBMENU_CALIBRE, R.drawable.cloud_24px),
            Map.entry(R.id.MENU_CALIBRE_READ, R.drawable.menu_book_24px),
            Map.entry(R.id.MENU_CALIBRE_DOWNLOAD, R.drawable.cloud_download_24px),
            Map.entry(R.id.MENU_CALIBRE_SETTINGS, R.drawable.settings_24px),
            // R.menu.sm_view_on_site
            Map.entry(R.id.SUBMENU_VIEW_BOOK_AT_SITE, R.drawable.link_24px),
            // R.menu.sm_search_on_amazon
            Map.entry(R.id.SUBMENU_AMAZON_SEARCH, R.drawable.search_24px),
            Map.entry(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.drawable.search_24px),
            Map.entry(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.drawable.search_24px),
            Map.entry(R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.drawable.search_24px),
            // R.menu.sm_search_on_bol
            Map.entry(R.id.SUBMENU_BOL_SEARCH, R.drawable.search_24px),
            Map.entry(R.id.MENU_BOL_BOOKS_BY_AUTHOR, R.drawable.search_24px),
            Map.entry(R.id.MENU_BOL_BOOKS_BY_AUTHOR_IN_SERIES, R.drawable.search_24px),
            Map.entry(R.id.MENU_BOL_BOOKS_IN_SERIES, R.drawable.search_24px),


            // common to several menus
            Map.entry(R.id.MENU_UPDATE_FROM_INTERNET, R.drawable.cloud_download_24px),
            Map.entry(R.id.MENU_LEVEL_EXPAND, R.drawable.unfold_more_24px)
    );

    private IconMapper() {
    }

    /**
     * Map the given menu-item id to a suitable icon (Drawable) resource id.
     *
     * @param menuItemId to map
     *
     * @return drawable icon id; or {@code 0} if none found
     */
    @DrawableRes
    public static int getIconResId(@IdRes final int menuItemId) {
        final Integer iconId = MAP.get(menuItemId);
        return iconId != null ? iconId : 0;
    }
}
