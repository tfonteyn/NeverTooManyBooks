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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommonSetup {

    @Mock
    protected Context mContext;
    @Mock
    protected SharedPreferences mSharedPreferences;
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
     *                DateUtils.create(locale0)
     *                context.getResources().getConfiguration().getLocales().get(0)
     */
    public void setLocale(@NonNull final Locale locale0) {
        mLocale0 = locale0;
        Locale.setDefault(mLocale0);
        DateUtils.create(mLocale0);
    }

    /**
     * Each test <strong>MUST</strong> call {@link #setLocale(Locale)} as needed.
     */
    @AfterEach
    void tearDown() {
        mLocale0 = null;
        Locale.setDefault(mJdkLocale);
        DateUtils.clear();
    }

    @BeforeEach
    @CallSuper
    public void setUp() {
        mJdkLocale = Locale.getDefault();

        MockitoAnnotations.initMocks(this);

        mRawData = BundleMock.mock();

        mContext = mock(Context.class);
        mResources = mock(Resources.class);
        mSharedPreferences = mock(SharedPreferences.class);
        mConfiguration = mock(Configuration.class);
        mLocaleList = mock(LocaleList.class);

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

        when(mContext.getString(R.string.unknown)).thenReturn("Unknown");

        when(mSharedPreferences.getString(eq(Prefs.pk_ui_locale), eq(LocaleUtils.SYSTEM_LANGUAGE)))
                .thenReturn(LocaleUtils.SYSTEM_LANGUAGE);

        when(mSharedPreferences.getString(eq("nederlands"), anyString())).thenReturn("nld");
        when(mSharedPreferences.getString(eq("frans"), anyString())).thenReturn("fra");
        when(mSharedPreferences.getString(eq("duits"), anyString())).thenReturn("ger");
        when(mSharedPreferences.getString(eq("engels"), anyString())).thenReturn("eng");
        when(mSharedPreferences.getString(eq("english"), anyString())).thenReturn("eng");

        when(mSharedPreferences.getString(eq(AmazonSearchEngine.PREFS_HOST_URL), anyString()))
                .thenReturn("https://www.amazon.co.uk");
        when(mSharedPreferences.getString(eq(IsfdbSearchEngine.PREFS_HOST_URL), anyString()))
                .thenReturn("http://www.isfdb.org");
        when(mSharedPreferences.getString(eq(KbNlSearchEngine.PREFS_HOST_URL), anyString()))
                .thenReturn("http://opc4.kb.nl");
    }

    public static class DummyCaller
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
