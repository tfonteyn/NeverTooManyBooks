/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.os.StrictMode;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookViewModel;
import com.hardbacknutter.nevertoomanybooks.booklist.RebuildBooklist;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.WritableStyle;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkCheckerImpl;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.settings.FastScrollerMode;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.ColorMapper;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"OverlyBroadThrowsClause", "MissingJavadoc"})
public abstract class BaseDBTest {

    protected ServiceLocator serviceLocator;
    protected Context context;
    private Context appContext;

    /**
     * Setup.
     *
     * @param uiLocale the user configured UI Locale.
     */
    @CallSuper
    public void setup(@NonNull final String uiLocale)
            throws StorageException {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                       .detectLeakedSqlLiteObjects()
                                       .detectLeakedClosableObjects()
                                       .penaltyDeath()
                                       .penaltyLog()
                                       .build());

        serviceLocator = ServiceLocator.getInstance();
        appContext = serviceLocator.getAppContext();

        PreferenceManager.getDefaultSharedPreferences(appContext)
                         .edit()
                         .putString(Prefs.PK_UI_LOCALE, uiLocale)
                         .putString(CoverVolume.PK_VOLUME_INDEX, "0")

                         .putBoolean(EditBookFragment.PK_EDIT_BOOK_TABS_EXTERNAL_ID, false)
                         .putBoolean(NetworkCheckerImpl.PK_NETWORK_ALLOW_METERED, true)
                         .putBoolean(ColorMapper.PK_SEARCH_REFORMAT_COLOR, true)
                         .putBoolean(FormatMapper.PK_SEARCH_REFORMAT_FORMAT, true)
                         .putBoolean("search.site.amazon.data.enabled", false)
                         .putBoolean("search.site.bedetheque.data.enabled", false)
                         .putBoolean("search.site.bol.data.enabled", false)
                         .putBoolean("search.site.bookfinder.data.enabled", false)
                         .putBoolean("search.site.dnb.data.enabled", false)
                         .putBoolean("search.site.douban.data.enabled", false)
                         .putBoolean("search.site.googlebooks.data.enabled", false)
                         .putBoolean("search.site.isfdb.data.enabled", true)
                         .putBoolean("search.site.kbnl.data.enabled", false)
                         .putBoolean("search.site.lastdodo.data.enabled", false)
                         .putBoolean("search.site.openlibrary.data.enabled", false)
                         .putBoolean("search.site.stripinfo.data.enabled", false)
                         .putBoolean("search.site.stripweb.data.enabled", false)
                         .putBoolean(ReorderHelper.PK_SORT_TITLE_REORDERED, true)
                         .putBoolean("tips.tip.book_list", false)
                         .putBoolean("tips.tip.booklist_styles_editor", false)
                         .putBoolean("tips.tip.configure_sites", false)
                         .putBoolean("tips.tip.view_only_help", false)
                         .putInt(ExportHelper.PK_BACKUP_COUNTDOWN, 5)
                         // Bit pattern with most valid field bits turned on
                         .putLong(FieldVisibility.PK_FIELD_VISIBILITY,
                                  9223372036854775807L)
                         .putString(ExtMenuButton.PK_MODE, "0")
                         .putString(FastScrollerMode.PK_OVERLAY, "3")
                         .putString(RebuildBooklist.PK_BOOKLIST_REBUILD_STATE, "0")
                         .putString(EditBookViewModel.PK_EDIT_BOOK_ISBN_CHECKS, "1")
                         .putString("search.siteOrder.data",
                                    "amazon,googlebooks,isfdb,bookfinder,openlibrary,stripinfo"
                                    + ",lastdodo,stripweb,bedetheque,kbnl,bol,dnb,douban")
                         .apply();

        // This will use the above prefs to determine the user Locale
        context = serviceLocator.getLocalizedAppContext();

        // Bootstrap the cover storage
        final int configuredVolume = CoverVolume.getVolume(appContext);
        assertEquals(0, configuredVolume);
        final boolean available = CoverVolume.isAvailable(appContext, configuredVolume);
        assertTrue(available);
        serviceLocator.getCoverStorage().initDir();

        // Bootstrap the database
        serviceLocator.getDb();

        // Ensure we have a user-style for our tests
        if (getUserStyle().isEmpty()) {
            final Style style = getBuiltinStyle();
            final UserStyle clone = (UserStyle) style.clone(context);
            clone.setName("A user style");
            serviceLocator.getStyles().insertOrUpdate(context, clone);
        }
    }

    @NonNull
    public Context getContext() {
        return context;
    }

    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    @NonNull
    protected Style getBuiltinStyle() {
        return BuiltinStyle.ALL.stream()
                               .filter(def -> def.getId() == BuiltinStyle.ID_FOR_TESTING_ONLY)
                               .findFirst()
                               .map(BuiltinStyle.Definition::getUuid)
                               .map(uuid -> serviceLocator.getStyles().getStyle(uuid))
                               .orElseThrow()
                               .orElseThrow();
    }

    @NonNull
    protected Optional<WritableStyle> getUserStyle() {
        return serviceLocator.getStyles()
                             .getStyles(true)
                             .stream()
                             .filter(def -> def.getType() == Style.Type.User)
                             .map(style -> (WritableStyle) style)
                             .findFirst();
    }

    /** Load and parse a JSoup document from a raw HTML resource. */
    @NonNull
    protected Document loadDocument(final int resId,
                                    @Nullable final String charset,
                                    @NonNull final String locationHeader)
            throws IOException {
        final Document document;
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            assertNotNull(is);
            document = Jsoup.parse(is, charset, locationHeader);
            assertNotNull(document);
            assertTrue(document.hasText());
        }
        return document;
    }

    @NonNull
    protected JSONObject loadJSONObject(final int resId)
            throws IOException {
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            final String response = reader.lines().collect(Collectors.joining());
            return new JSONObject(response);
        }
    }
}
