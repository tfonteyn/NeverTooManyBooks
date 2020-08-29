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
package com.hardbacknutter.nevertoomanybooks.debug;

public final class ErrorMsg {

    public static final String UNEXPECTED_VALUE = "Unexpected value: ";

    public static final String INVALID_ISBN = "isbn must be valid";

    public static final String LISTENER_WAS_NULL = "Listener was NULL";
    public static final String LISTENER_WAS_DEAD = "Listener was dead";

    public static final String EMPTY_ARRAY_OR_LIST = "Empty array/list";
    public static final String EMPTY_KEY = "Empty key";
    public static final String EMPTY_UUID = "Empty UUID";

    public static final String ZERO_ID_FOR_AUTHOR = "Author id==0";
    public static final String ZERO_ID_FOR_BOOK = "Book id==0";
    public static final String ZERO_ID_FOR_STYLE = "Style id==0";

    // Objects.requireNonNull
    public static final String NULL_AUTHOR = "Author";
    public static final String NULL_BUILDER = "Builder";
    public static final String NULL_CHECKLIST = "Checklist";
    public static final String NULL_BOOKSHELF = "Bookshelf";
    public static final String NULL_CURSOR = "Cursor";
    public static final String NULL_DRAWABLE = "Drawable";
    public static final String NULL_EXTRAS = "Extras";
    public static final String NULL_EXCEPTION = "Exception";
    public static final String NULL_EXTERNAL_ID = "externalId";
    public static final String NULL_FRAGMENT_TAG = "Fragment Tag";
    public static final String NULL_GALLERY_ADAPTER = "GalleryAdapter";
    public static final String NULL_LAYOUT_MANAGER = "LinearLayoutManager";
    public static final String NULL_IMAGE_PATH = "ImagePath";
    public static final String NULL_INPUT_STREAM = "InputStream was NULL";
    public static final String NULL_INTENT_DATA = "Intent data";
    public static final String NULL_ISBN = "ISBN";
    public static final String NULL_ISBN_STR = "IsbnStr";
    public static final String NULL_MENU = "Menu";
    public static final String NULL_NON_PERSISTED_VALUE = "NonPersistedValue";
    public static final String NULL_PUBLISHER = "Publisher";
    public static final String NULL_SCANNER_MODEL = "ScannerModel";
    public static final String NULL_SERIES = "Series";
    public static final String NULL_STYLE = "Style";
    public static final String NULL_TASK_RESULTS = "Task result";
    public static final String NULL_TOC_ENTRY = "TocEntry";
    public static final String NULL_URI = "uri";
    public static final String NULL_UUID = "UUID";

    public static final String BUNDLE_NOT_PUSHED = "Bundle not pushed!";
    public static final String BUNDLE_ALREADY_PUSHED = "Bundle already pushed!";
    public static final String CREATE_WAS_NOT_CALLED = "create was not called?";
    public static final String IMPORT_NOT_SUPPORTED = "Import not supported";
    public static final String OPTIONS_NOT_SET = "options not set";
    public static final String ONLY_ONE_OPTION_ALLOWED = "only one option allowed";
    public static final String NULL_OUTPUT_STREAM = "OutputStream was NULL";

    private ErrorMsg() {
    }
}
