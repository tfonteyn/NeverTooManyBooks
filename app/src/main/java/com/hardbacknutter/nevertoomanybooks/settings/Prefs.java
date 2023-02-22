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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.fastscroller.OverlayProviderFactory;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;

/**
 * All keys <strong>MUST</strong> be kept in sync with "src/main/res/xml/preferences*.xml"
 */
@SuppressWarnings("WeakerAccess")
public final class Prefs {

    /** The locale the user is running our app in (which can be different from the device). */
    public static final String pk_ui_locale = "ui.locale";
    /** Not the actual theme, but the day/night part only. */
    public static final String pk_ui_theme = "ui.theme";

    public static final String pk_network_allow_metered = "network.allow.metered";

    public static final String pk_sounds_scan_isbn_valid = "sounds.scan.isbn.valid";
    public static final String pk_sounds_scan_isbn_invalid = "sounds.scan.isbn.invalid";
    public static final String pk_sounds_scan_found_barcode = "sounds.scan.barcode.found";

    public static final String pk_search_reformat_format = "search.reformat.format";
    public static final String pk_search_reformat_color = "search.reformat.color";
    public static final String pk_search_isbn_prefer_10 = "search.byIsbn.prefer.10";

    public static final String pk_camera_image_autorotate = "camera.image.autorotate";
    public static final String pk_camera_image_action = "camera.image.action";
    public static final String pk_camera_lens_facing = "camera.lens.facing";

    public static final String pk_image_cache_resized = "image.cache.resized";
    public static final String pk_image_cropper_frame_whole = "image.cropper.frame.whole";

    public static final String pk_storage_volume = "storage.volume.index";

    public static final String pk_booklist_rebuild_state = "booklist.rebuild.state";
    public static final String pk_booklist_context_menu = "booklist.context.menu";
    public static final String pk_booklist_fastscroller_overlay = "booklist.fastscroller.overlay";

    public static final String pk_edit_book_isbn_checks = "edit.book.isbn.checks";
    /** Whether to show the fragment that allows the user to edit the external id's. */
    public static final String pk_edit_book_tabs_external_id = "edit.book.tab.externalId";

    public static final String pk_sqlite_max_lines = "sqlite.shell.max.lines";

    public static final String pk_suffix_host_url = ".host.url";

    public static final String pk_timeout_connect_in_seconds = "timeout.connect";
    public static final String pk_timeout_read_in_seconds = "timeout.read";


    private Prefs() {
    }

    public static int getTimeoutValueInMs(@NonNull final String key,
                                          final int defValueInMs) {
        final int seconds = ServiceLocator.getPreferences().getInt(key, 0);
        // <1000 as sanity check for roque preference file imports
        if (seconds > 0 && seconds < 1000) {
            return seconds * 1000;
        } else {
            return defValueInMs;
        }
    }

    @OverlayProviderFactory.OverlayType
    public static int getFastScrollerOverlayType(@NonNull final Context context) {
        return IntListPref.getInt(context,
                                  pk_booklist_fastscroller_overlay,
                                  OverlayProviderFactory.TYPE_MD2);
    }
}
