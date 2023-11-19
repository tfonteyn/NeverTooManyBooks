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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks._mocks.os.ContextMock;
import com.hardbacknutter.nevertoomanybooks._mocks.os.SharedPreferencesMock;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Strictness.LENIENT: this setup is shared + provides answers/returns for future additions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Base {

    private static final String PACKAGE_NAME = "com.hardbacknutter.nevertoomanybooks";
    // create a default Locale list; override with calls to setLocale(...)
    protected final List<Locale> locales = new ArrayList<>(List.of(Locale.US));
    @Mock
    protected App app;
    @Mock
    protected Resources resources;
    @Mock
    protected Configuration configuration;
    @Mock
    protected Style style;
    protected Context context;
    protected ServiceLocatorMock serviceLocatorMock;

    @Mock
    private CoverStorage coverStorage;
    @Mock
    private StylesHelper stylesHelper;
    private SharedPreferences sharedPreferences;
    @Mock
    private LocaleList localeList;
    private Locale jdkLocale;

    @NonNull
    protected static File getTmpDir() {
        //noinspection DataFlowIssue
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     * @param locales to use for
     *                JDK
     *                context.getResources().getConfiguration().getLocales()
     */
    public void setLocale(@NonNull final Locale... locales) {
        this.locales.clear();
        this.locales.addAll(Arrays.asList(locales));
        Locale.setDefault(this.locales.get(0));
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(jdkLocale);
    }

    /**
     * Each test <strong>should</strong> call {@link #setLocale(Locale...)} as needed.
     * The default is Locale.US.
     */
    @BeforeEach
    @CallSuper
    public void setup()
            throws Exception {
        // save the JDK locale first
        jdkLocale = Locale.getDefault();

        context = ContextMock.create(PACKAGE_NAME);

        when(app.getApplicationContext()).thenReturn(context);
        when(context.getResources()).thenReturn(resources);

        sharedPreferences = SharedPreferencesMock.create();
        setupSearchEnginePreferences();
        when(context.getSharedPreferences(eq(PACKAGE_NAME + "_preferences"), anyInt()))
                .thenReturn(sharedPreferences);
        final SharedPreferences languageMap = createLanguageMap();
        when(context.getSharedPreferences(eq(Languages.LANGUAGE_MAP), anyInt()))
                .thenReturn(languageMap);

        // String resource
        setupStringResources(resources);
        doAnswer(invocation -> resources.getString(invocation.getArgument(0)))
                .when(context).getString(anyInt());


        when(resources.getConfiguration()).thenReturn(configuration);
        when(configuration.getLocales()).thenReturn(localeList);
        // we CANNOT mock the setters - as I understand due to Configuration.class being final
        // TODO: look into mocking Configuration.class
//        doNothing().when(configuration).setLocales(any(LocaleList.class));
//        doNothing().when(configuration).setLocale(any(Locale.class));

        when(localeList.size()).thenReturn(locales.size());
        doAnswer(invocation -> locales.get(invocation.getArgument(0)))
                .when(localeList).get(anyInt());

        when(style.getUuid()).thenReturn(BuiltinStyle.HARD_DEFAULT_UUID);
        when(stylesHelper.getDefault()).thenReturn(style);

        when(coverStorage.getDir()).thenReturn(getTmpDir());
        when(coverStorage.getTempDir()).thenReturn(getTmpDir());
        when(coverStorage.isAcceptableSize(any(File.class))).thenReturn(true);

        doAnswer(invocation -> invocation.getArgument(1))
                .when(coverStorage).persist(any(InputStream.class), any(File.class));

        serviceLocatorMock = new ServiceLocatorMock(context,
                                                    localeList,
                                                    stylesHelper,
                                                    coverStorage);
        // See class docs.
        ImageDownloader.IGNORE_RENAME_FAILURE = true;

        LoggerFactory.setLogger(new TestLogger(getTmpDir()));
        ServiceLocator.create(serviceLocatorMock);
        SearchEngineConfig.createRegistry(context, serviceLocatorMock.getLanguages());
    }

    private SharedPreferences createLanguageMap() {
        /*
         * SharedPreferences for the language map.
         */
        final SharedPreferences languageMap = SharedPreferencesMock.create();
        languageMap.edit()
                   .putString("english", "eng")
                   .putString("engels", "eng")
                   .putString("anglais", "eng")
                   .putString("englisch", "eng")

                   .putString("french", "fra")
                   .putString("français", "fra")
                   .putString("französisch", "fra")
                   .putString("frans", "fra")

                   .putString("german", "ger")
                   .putString("allemand", "ger")
                   .putString("deutsch", "ger")
                   .putString("duits", "ger")

                   .putString("dutch", "nld")
                   .putString("néerlandais", "nld")
                   .putString("niederländisch", "nld")
                   .putString("nederlands", "nld")

                   .apply();
        return languageMap;
    }

    private void setupSearchEnginePreferences() {
        sharedPreferences.edit()
                         .putString(Prefs.pk_ui_locale, AppLocale.SYSTEM_LANGUAGE)
                         // random some at true, some at false.
                         .putBoolean("search.site.amazon.data.enabled", true)
                         .putBoolean("search.site.googlebooks.data.enabled", false)
                         .putBoolean("search.site.isfdb.data.enabled", true)
                         .putBoolean("search.site.kbnl.data.enabled", false)
                         .putBoolean("search.site.lastdodo.data.enabled", true)
                         .putBoolean("search.site.librarything.data.enabled", false)
                         .putBoolean("search.site.openlibrary.data.enabled", true)
                         .putBoolean("search.site.stripinfo.data.enabled", false)

                         .putString("search.siteOrder.data",
                                    "kbnl,openlibrary,isfdb,librarything,amazon,googlebooks")
                         .putString("search.siteOrder.covers",
                                    "isfdb,amazon,kbnl,openlibrary")
                         .putString("search.siteOrder.alted",
                                    "isfdb,librarything")

                         .apply();

        when(sharedPreferences.getString(eq(AmazonSearchEngine.PK_HOST_URL),
                                         nullable(String.class)))
                .thenAnswer((Answer<String>) invocation ->
                        getLocalizedSiteUrl("amazon", true));
    }

    @NonNull
    private String getLocalizedSiteUrl(@NonNull final String site,
                                       final boolean hasUkSite) {
        final String iso3 = locales.get(0).getISO3Language();
        if (Locale.US.getISO3Language().equals(iso3)) {
            return "https://www." + site + ".com";
        } else if (Locale.FRANCE.getISO3Language().equals(iso3)) {
            return "https://www." + site + ".fr";
        } else if (Locale.GERMANY.getISO3Language().equals(iso3)) {
            return "https://www." + site + ".de";
        } else if (new Locale("nl").getISO3Language().equals(iso3)) {
            return "https://www." + site + ".nl";

        } else if (hasUkSite && Locale.UK.getISO3Language().equals(iso3)) {
            return "https://www." + site + ".co.uk";
        }

        return "https://www." + site + ".com";
    }

    /*
     * String resources.
     */
    private void setupStringResources(@NonNull final Resources resources) {
        when(resources.getString(eq(R.string.book_format_paperback))).thenReturn("Paperback");
        when(resources.getString(eq(R.string.book_format_softcover))).thenReturn("Softcover");
        when(resources.getString(eq(R.string.book_format_hardcover))).thenReturn("Hardcover");
        when(resources.getString(eq(R.string.book_format_dimensions))).thenReturn("Dim");

        when(resources.getString(eq(R.string.unknown_title))).thenReturn("[Unknown title]");
        when(resources.getString(eq(R.string.unknown_author))).thenReturn("[Unknown author]");

        when(resources.getString(eq(R.string.lbl_book))).thenReturn("Book");
    }

}
