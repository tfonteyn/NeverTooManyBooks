/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.BundleMock;
import com.hardbacknutter.nevertoomanybooks.R;

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
    protected Bundle mBookData;

    @BeforeEach
    protected void setUp() {
        MockitoAnnotations.initMocks(this);

        mBookData = BundleMock.mock();

        mContext = mock(Context.class);
        mResources = mock(Resources.class);
        mSharedPreferences = mock(SharedPreferences.class);
        mConfiguration = mock(Configuration.class);

        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.createConfigurationContext(any())).thenReturn(mContext);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);

        when(mResources.getConfiguration()).thenReturn(mConfiguration);

        when(mContext.getString(R.string.book_format_paperback)).thenReturn("Paperback");
        when(mContext.getString(R.string.book_format_softcover)).thenReturn("Softcover");
        when(mContext.getString(R.string.book_format_hardcover)).thenReturn("Hardcover");

        when(mSharedPreferences.getString(eq("nederlands"), eq("Nederlands"))).thenReturn("nld");
        when(mSharedPreferences.getString(eq("frans"), eq("Frans"))).thenReturn("fre");
        when(mSharedPreferences.getString(eq("duits"), eq("Duits"))).thenReturn("ger");
        when(mSharedPreferences.getString(eq("engels"), eq("Engels"))).thenReturn("eng");
    }
}
