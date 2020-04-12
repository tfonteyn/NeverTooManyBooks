/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
 * Global RequestCode constants.
 */
@SuppressWarnings("WeakerAccess")
public final class RequestCode {

    /** Request code: navigation panel. */
    public static final int NAV_PANEL_EDIT_BOOKSHELVES = 1_001;
    /** Request code: navigation panel. */
    public static final int NAV_PANEL_EDIT_STYLES = 1_002;
    /** Request code: navigation panel. */
    public static final int NAV_PANEL_IMPORT = 1_003;
    /** Request code: navigation panel. */
    public static final int NAV_PANEL_EXPORT = 1_004;
    /** Request code: navigation panel. */
    public static final int NAV_PANEL_GOODREADS = 1_005;
    /** Request code: navigation panel call to bring up the Settings. */
    public static final int NAV_PANEL_SETTINGS = 1_010;
    /** Request code: <strong>non</strong>-navigation panel call to bring up the Settings. */
    public static final int SETTINGS = 1_011;

    /** Request code: edit a Style. */
    public static final int EDIT_STYLE = 1_100;
    /** Request code: edit the Groups of a Style. */
    public static final int EDIT_STYLE_GROUPS = 1_101;

    /** request code: open the book view screen. */
    public static final int BOOK_VIEW = 2_001;
    /** Request code: open the book edit screen. */
    public static final int BOOK_EDIT = 2_002;
    /** Request code: open the book edit screen with a new, duplicate, book. */
    public static final int BOOK_DUPLICATE = 2_003;
    /** Request code: open the book 'update-from-internet' screen. */
    public static final int UPDATE_FIELDS_FROM_INTERNET = 2_004;

    /** Request code: open the book internet-search screen. */
    public static final int BOOK_SEARCH = 2_101;
    /** Request code: open the advanced (FTS) local search screen. */
    public static final int ADVANCED_LOCAL_SEARCH = 2_102;
    /** Request code: open the scanner. */
    public static final int SCAN_BARCODE = 2_103;

    /** Request code: open the admin page to order and en/disable sites. */
    public static final int PREFERRED_SEARCH_SITES = 2_201;

    /** Request code: Let the user pick a Uri to export to. */
    public static final int EXPORT_PICK_URI = 2_501;
    /** Request code: Let the user pick a Uri to import from. */
    public static final int IMPORT_PICK_URI = 2_502;


    /** Request code: ask the CoverBrowserFragment to get an alternative edition cover. */
    public static final int ACTION_COVER_BROWSER = 3_001;
    /** Request code: use internal routines for cropping images. */
    public static final int CROP_IMAGE = 3_002;
    /** Request code: start an intent for an external application to do the cropping. */
    public static final int EDIT_IMAGE = 3_003;
    /** Request code: start an intent to get an image from the Camera. */
    public static final int ACTION_IMAGE_CAPTURE = 3_004;
    /** Request code: start an intent to get an image from the an app that provides content. */
    public static final int ACTION_GET_CONTENT = 3_005;

    /** Request code: open the author "all works" screen. */
    public static final int AUTHOR_WORKS = 4_001;

    /** Request code: system request to ask the user for permissions. */
    public static final int ANDROID_PERMISSIONS = 9_000;
    /** Request code: system request to ask the user to install this stuff. */
    public static final int UPDATE_GOOGLE_PLAY_SERVICES = 9_001;

    private RequestCode() {
    }
}
