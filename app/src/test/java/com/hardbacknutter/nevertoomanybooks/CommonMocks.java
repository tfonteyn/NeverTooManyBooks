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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Strictness.LENIENT: this setup is shared + provides answers/returns for future additions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CommonMocks {

    @Mock
    protected App mApp;
    @Mock
    protected Context mContext;
    @Mock
    protected SharedPreferences mSharedPreferences;
    @Mock
    protected SharedPreferences.Editor mSharedPreferencesEditor;

    @Mock
    protected Resources mResources;
    @Mock
    protected Configuration mConfiguration;

    @Mock
    protected LocaleList mLocaleList;

    @Mock
    protected Bundle mRawData;

    /** set during setup() call. */
    @Nullable
    protected Locale mLocale0;

    private Locale mJdkLocale;

    /**
     * @param locale0 to use for
     *                JDK
     *                DateParser.create(locale0)
     *                context.getResources().getConfiguration().getLocales().get(0)
     */
    public void setLocale(@NonNull final Locale locale0) {
        mLocale0 = locale0;
        Locale.setDefault(mLocale0);
        DateParser.createForTesting(mLocale0);
    }


    @AfterEach
    void tearDown() {
        mLocale0 = null;
        Locale.setDefault(mJdkLocale);
    }

    /**
     * Each test <strong>should</strong> call {@link #setLocale(Locale)} as needed.
     * The default is Locale.US.
     */
    @BeforeEach
    @CallSuper
    public void setUp() {
        setLocale(Locale.US);

        mJdkLocale = Locale.getDefault();

        mRawData = BundleMock.mock();

        when(mApp.getApplicationContext()).thenReturn(mContext);

        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.createConfigurationContext(any())).thenReturn(mContext);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mContext.getPackageName()).thenReturn("com.hardbacknutter.nevertoomanybooks");

        when(mResources.getConfiguration()).thenReturn(mConfiguration);

        when(mConfiguration.getLocales()).thenReturn(mLocaleList);


        when(mLocaleList.get(0)).thenAnswer((Answer<Locale>) invocation -> mLocale0);


        when(mContext.getString(R.string.book_format_paperback)).thenReturn("Paperback");
        when(mContext.getString(R.string.book_format_softcover)).thenReturn("Softcover");
        when(mContext.getString(R.string.book_format_hardcover)).thenReturn("Hardcover");
        when(mContext.getString(R.string.book_format_dimensions)).thenReturn("Dim");

        when(mContext.getString(R.string.unknownName)).thenReturn("Unknown");

        when(mSharedPreferences.getString(eq(Prefs.pk_ui_locale), eq(LocaleUtils.SYSTEM_LANGUAGE)))
                .thenReturn(LocaleUtils.SYSTEM_LANGUAGE);

        when(mSharedPreferences.getString(eq("english"), anyString())).thenReturn("eng");
        when(mSharedPreferences.getString(eq("engels"), anyString())).thenReturn("eng");
        when(mSharedPreferences.getString(eq("anglais"), anyString())).thenReturn("eng");
        when(mSharedPreferences.getString(eq("englisch"), anyString())).thenReturn("eng");

        when(mSharedPreferences.getString(eq("french"), anyString())).thenReturn("fra");
        when(mSharedPreferences.getString(eq("français"), anyString())).thenReturn("fra");
        when(mSharedPreferences.getString(eq("französisch"), anyString())).thenReturn("fra");
        when(mSharedPreferences.getString(eq("frans"), anyString())).thenReturn("fra");

        when(mSharedPreferences.getString(eq("german"), anyString())).thenReturn("ger");
        when(mSharedPreferences.getString(eq("allemand"), anyString())).thenReturn("ger");
        when(mSharedPreferences.getString(eq("deutsch"), anyString())).thenReturn("ger");
        when(mSharedPreferences.getString(eq("duits"), anyString())).thenReturn("ger");

        when(mSharedPreferences.getString(eq("dutch"), anyString())).thenReturn("nld");
        when(mSharedPreferences.getString(eq("néerlandais"), anyString())).thenReturn("nld");
        when(mSharedPreferences.getString(eq("niederländisch"), anyString())).thenReturn("nld");
        when(mSharedPreferences.getString(eq("nederlands"), anyString())).thenReturn("nld");

        when(mSharedPreferences.getString(eq(AmazonSearchEngine.PREFS_HOST_URL), anyString()))
                .thenAnswer((Answer<String>) invocation -> {
                    if (mLocale0 != null) {
                        final String iso3 = mLocale0.getISO3Language();
                        if (Locale.US.getISO3Language().equals(iso3)) {
                            return "https://www.amazon.com";
                        } else if (Locale.UK.getISO3Language().equals(iso3)) {
                            return "https://www.amazon.co.uk";
                        } else if (Locale.FRANCE.getISO3Language().equals(iso3)) {
                            return "https://www.amazon.fr";
                        } else if (Locale.GERMANY.getISO3Language().equals(iso3)) {
                            return "https://www.amazon.de";
                        } else if (new Locale("nl").getISO3Language().equals(iso3)) {
                            return "https://www.amazon.nl";
                        }
                    }
                    return "https://www.amazon.com";
                });

        when(mSharedPreferences.edit()).thenReturn(mSharedPreferencesEditor);

        when(mSharedPreferencesEditor.putString(anyString(), anyString()))
                .thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putStringSet(anyString(), anySet()))
                .thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putBoolean(anyString(), anyBoolean()))
                .thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putInt(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putLong(anyString(), anyLong()))
                .thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putFloat(anyString(), anyFloat()))
                .thenReturn(mSharedPreferencesEditor);
    }

    public static class TextCaller
            implements Canceller {

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
