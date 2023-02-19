/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.os.Environment;
import android.os.LocaleList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks._mocks.os.ContextMock;
import com.hardbacknutter.nevertoomanybooks._mocks.os.SharedPreferencesMock;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
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
import org.xml.sax.SAXException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Strictness.LENIENT: this setup is shared + provides answers/returns for future additions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Base {

    private static final String PACKAGE_NAME = "com.hardbacknutter.nevertoomanybooks";

    @Mock
    protected App app;
    @Mock
    protected Resources resources;
    @Mock
    protected Configuration configuration;
    @Mock
    protected LocaleList localeList;

    protected Book book;
    protected Context context;
    protected SharedPreferences mockPreferences;

    /** set during setup() call. */
    @Nullable
    protected Locale locale0;

    private Locale jdkLocale;

    /**
     * @param locale0 to use for
     *                JDK
     *                context.getResources().getConfiguration().getLocales().get(0)
     */
    public void setLocale(@NonNull final Locale locale0) {
        this.locale0 = locale0;
        Locale.setDefault(this.locale0);
    }

    @AfterEach
    void tearDown() {
        locale0 = null;
        Locale.setDefault(jdkLocale);
    }

    @NonNull
    protected static File getTmpDir() {
        final String tmpDir = System.getProperty("java.io.tmpdir");
        //noinspection ConstantConditions
        return new File(tmpDir);
    }

    /**
     * Each test <strong>should</strong> call {@link #setLocale(Locale)} as needed.
     * The default is Locale.US.
     */
    @BeforeEach
    @CallSuper
    public void setup()
            throws ParserConfigurationException, SAXException {
        // save the JDK locale first
        jdkLocale = Locale.getDefault();
        // set as default
        setLocale(Locale.US);

        context = ContextMock.create(PACKAGE_NAME);

        mockPreferences = SharedPreferencesMock.create();

        book = new Book(BundleMock.create());

        when(app.getApplicationContext()).thenReturn(context);

        when(context.getResources()).thenReturn(resources);

        when(context.getFilesDir()).thenAnswer((Answer<File>) invocation -> getTmpDir());

        when(context.getExternalFilesDirs(eq(Environment.DIRECTORY_PICTURES))).thenAnswer(
                (Answer<File[]>) invocation -> {
                    final String tmpDir = System.getProperty("java.io.tmpdir");
                    final File[] dirs = new File[1];
                    dirs[0] = new File(tmpDir, "Pictures");
                    //noinspection ResultOfMethodCallIgnored
                    dirs[0].mkdir();
                    return dirs;
                });

        // Global prefs
        when(context.getSharedPreferences(eq(PACKAGE_NAME + "_preferences"), anyInt()))
                .thenReturn(mockPreferences);

        // Styles
        when(context.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mockPreferences);


        doAnswer(invocation -> resources.getString(invocation.getArgument(0)))
                .when(context).getString(anyInt());

        when(resources.getConfiguration()).thenReturn(configuration);
        when(configuration.getLocales()).thenReturn(localeList);
        doNothing().when(configuration).setLocale(any(Locale.class));

        when(localeList.get(0)).thenAnswer((Answer<Locale>) invocation -> locale0);


        // See class docs.
        ImageDownloader.IGNORE_RENAME_FAILURE = true;

        ServiceLocator.create(context, BundleMock::create);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        serviceLocator.setSystemLocaleSupplier(() -> List.of(Locale.US));
        serviceLocator.setLogger(new TestLogger(getTmpDir()));
        serviceLocator.setNetworkChecker(new TestNetworkChecker(true));

        setupStringResources(resources);
        setupLanguageMap(context);
        setupSearchEnginePreferences();
    }

    private void setupLanguageMap(@NonNull final Context context) {
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

        when(context.getSharedPreferences(eq(Languages.LANGUAGE_MAP), anyInt()))
                .thenReturn(languageMap);
    }

    private void setupSearchEnginePreferences() {
        mockPreferences.edit()
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

                       // deliberate added 4 (LibraryThing) and omitted 128/256
                       .putString("search.siteOrder.data", "64,32,16,8,4,2,1")
                       .putString("search.siteOrder.covers", "16,2,8,64,32")
                       .putString("search.siteOrder.alted", "16,4")

                       .apply();

        when(mockPreferences.getString(eq(AmazonSearchEngine.PK_HOST_URL),
                                       nullable(String.class)))
                .thenAnswer((Answer<String>) invocation ->
                        getLocalizedSiteUrl("amazon", true));

        when(mockPreferences.getString(eq(LibraryThingSearchEngine.PK_HOST_URL),
                                       nullable(String.class)))
                .thenAnswer((Answer<String>) invocation ->
                        getLocalizedSiteUrl("librarything", false));
    }

    @NonNull
    private String getLocalizedSiteUrl(@NonNull final String site,
                                       final boolean hasUkSite) {
        if (locale0 != null) {
            final String iso3 = locale0.getISO3Language();
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
