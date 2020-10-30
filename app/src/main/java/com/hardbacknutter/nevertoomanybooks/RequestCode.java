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
package com.hardbacknutter.nevertoomanybooks;

/**
 * Global startActivityForResult RequestCode constants.
 */
@SuppressWarnings("WeakerAccess")
public final class RequestCode {

    /** navigation panel. */
    public static final int NAV_PANEL_MANAGE_BOOKSHELVES = 1_001;
    /** navigation panel. */
    public static final int NAV_PANEL_MANAGE_STYLES = 1_002;
    /** navigation panel. */
    public static final int NAV_PANEL_IMPORT = 1_003;
    /** navigation panel. */
    public static final int NAV_PANEL_EXPORT = 1_004;
    /** navigation panel. */
    public static final int NAV_PANEL_GOODREADS = 1_005;
    /** navigation panel call to bring up the Settings. */
    public static final int NAV_PANEL_SETTINGS = 1_010;


    /** edit a Style. */
    public static final int EDIT_STYLE = 1_100;
    /** edit the Groups of a Style. */
    public static final int EDIT_STYLE_GROUPS = 1_101;


    /** open the book view screen. */
    public static final int BOOK_VIEW = 2_001;
    /** open the book edit screen. */
    public static final int BOOK_EDIT = 2_002;
    /** open the book edit screen with a new, duplicate, book. */
    public static final int BOOK_DUPLICATE = 2_003;
    /** open the book 'update-from-internet' screen. */
    public static final int UPDATE_FIELDS_FROM_INTERNET = 2_004;

    /** open the book internet-search screen. */
    public static final int BOOK_SEARCH = 2_101;
    /** open the advanced (FTS) local search screen. */
    public static final int NAV_PANEL_ADVANCED_LOCAL_SEARCH = 2_102;

    /** open the author "all works" screen. */
    public static final int AUTHOR_WORKS = 3_001;

    private RequestCode() {
    }
}
