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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.fastscroller.OverlayProviderFactory;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

/**
 * All keys <strong>MUST</strong> be kept in sync with "src/main/res/xml/preferences*.xml"
 */
@SuppressWarnings("WeakerAccess")
public final class Prefs {

    /**
     * The locale the user is running our app in (which can be different from the device).
     * {@code String}: The literal {@code "system"} or an Android Locale code.
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.AppLocaleImpl
     */
    public static final String PK_UI_LOCALE = "ui.locale";

    /**
     * Whether to normalize {@link DBKey#FORMAT} values after a search.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.mappers.FormatMapper
     */
    public static final String PK_SEARCH_REFORMAT_FORMAT = "search.reformat.format";
    /**
     * Whether to normalize {@link DBKey#COLOR} values after a search.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.utils.mappers.ColorMapper
     */
    public static final String PK_SEARCH_REFORMAT_COLOR = "search.reformat.color";

    public static final String pk_normalize_series_title = "normalize.series.title";
    public static final String pk_normalize_toc_title = "normalize.toc.title";
    public static final String pk_normalize_publisher_name = "normalize.publisher.name";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Whether to search by using the ISBN10 value or the original {@link DBKey#BOOK_ISBN}.
     * <p>
     * {@code boolean}
     */
    public static final String PK_SEARCH_ISBN_PREFER_10 = "search.byIsbn.prefer.10";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Whether a shopping menu should be shown.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig
     */
    public static final String PK_SEARCH_SHOW_SHOPPING_MENU = "search.shopping.menu";

    public static final String PK_CAMERA_IMAGE_AUTOROTATE = "camera.image.autorotate";
    public static final String PK_CAMERA_IMAGE_ACTION = "camera.image.action";
    public static final String PK_CAMERA_LENS_FACING = "camera.lens.facing";

    public static final String PK_SCAN_MODE_SINGLE = "scan.mode.single";

    public static final String pk_image_undo_enabled = "image.undo.enabled";
    public static final String pk_image_cache_resized = "image.cache.resized";
    public static final String PK_STORAGE_VOLUME = "storage.volume.index";

    public static final String PK_BOOKLIST_REBUILD_STATE = "booklist.rebuild.state";
    /**
     * How ACCESS to context/row menus is provide.
     * <p>
     * {@code int}
     *
     * @see com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton
     */
    public static final String PK_BOOKLIST_CONTEXT_MENU = "booklist.context.menu";
    /**
     * Whether and how a Text Bubble with row information is shown
     * while fast-scrolling through a list.
     * <p>
     * {@code int}
     *
     * @see OverlayProviderFactory
     */
    public static final String PK_BOOKLIST_FASTSCROLLER_OVERLAY = "booklist.fastscroller.overlay";

    /**
     * Whether to show the fragment that allows the user to edit the external id's.
     * <p>
     * {@code boolean}
     *
     * @see com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment
     */
    public static final String PK_EDIT_BOOK_TABS_EXTERNAL_ID = "edit.book.tab.externalId";

    public static final String pk_sqlite_max_lines = "sqlite.shell.max.lines";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * A full url, including the http(s) part.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_URL = "host.url";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * A full url, including the http(s) part.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_USER = "host.user";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Clear text, but removed from debug reports.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_PASSWORD = "host.password";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket connect timeout.
     * <p>
     * {@code int} in seconds
     */
    public static final String PK_TIMEOUT_CONNECT_IN_SECONDS = "timeout.connect";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket read timeout
     * <p>
     * {@code int} in seconds
     */
    public static final String PK_TIMEOUT_READ_IN_SECONDS = "timeout.read";

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP GET/HEAD requests will log urls, response-codes and manual redirects.
     * <p>
     * {@code boolean}
     */
    public static final String PK_ENABLE_HTTP_LOGGING = "logging.http.get";


    private Prefs() {
    }

    @OverlayProviderFactory.OverlayType
    public static int getFastScrollerOverlayType(@NonNull final Context context) {
        return IntListPref.getInt(context,
                                  PK_BOOKLIST_FASTSCROLLER_OVERLAY,
                                  OverlayProviderFactory.TYPE_MD2);
    }

    public static boolean normalizeSeriesTitle(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_normalize_series_title, false);
    }

    public static boolean normalizePublisherName(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_normalize_publisher_name, false);
    }

    public static boolean normalizeTocEntryName(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(pk_normalize_toc_title, false);
    }
}
