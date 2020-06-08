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
package com.hardbacknutter.nevertoomanybooks.debug;

public final class ErrorMsg {

    public static final String UNEXPECTED_VALUE = "Unexpected value: ";

    public static final String ISBN_MUST_BE_VALID = "isbn must be valid";
    public static final String NO_TARGET_FRAGMENT_SET = "no target fragment set";

    public static final String TRANSACTION_REQUIRED = "Requires a transaction";

    public static final String LISTENER_WAS_NULL = "Listener was NULL";
    public static final String LISTENER_WAS_DEAD = "Listener was dead";

    public static final String EMPTY_ARRAY = "Empty array";
    public static final String EMPTY_KEY = "Empty key";
    public static final String EMPTY_UUID = "Empty UUID";

    // Generic Objects.requireNonNull
    public static final String NULL_CURRENT_BOOKSHELF = "CurrentBookshelf";
    public static final String NULL_CURSOR = "Cursor";
    public static final String NULL_DRAWABLE = "Drawable";
    public static final String NULL_FRAGMENT_TAG = "Fragment Tag";
    public static final String NULL_GALLERY_ADAPTER = "GalleryAdapter";
    public static final String NULL_INTENT_DATA = "Intent data";
    public static final String NULL_ISBN_STR = "IsbnStr";
    public static final String NULL_MENU = "Menu";
    public static final String NULL_NATIVE_ID = "nativeId";
    public static final String NULL_NON_PERSISTED_VALUE = "NonPersistedValue";
    public static final String NULL_ROW_POS = "rowPos";
    public static final String NULL_SCANNER_MODEL = "ScannerModel";
    public static final String NULL_TASK_RESULTS = "Task result";
    public static final String NULL_URI = "uri";
    public static final String NULL_UUID = "UUID";

    // Argument/Extras Objects.requireNonNull
    public static final String ARGS_MISSING_AUTHOR = "Author";
    public static final String ARGS_MISSING_BOOKSHELF = "Bookshelf";
    public static final String ARGS_MISSING_CHECKLIST = "Checklist";
    public static final String ARGS_MISSING_EXTRAS = "Extras";
    public static final String ARGS_MISSING_IMAGE_PATH = "ImagePath";
    public static final String ARGS_MISSING_ISBN = "ISBN";
    public static final String ARGS_MISSING_PUBLISHER = "Publisher";
    public static final String ARGS_MISSING_SERIES = "Series";
    public static final String ARGS_MISSING_STYLE = "Style";
    public static final String ARGS_MISSING_TOC_ENTRIES = "TocEntries";

    private ErrorMsg() {
    }
}
