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
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkChecker;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;

import org.mockito.Mock;

/**
 * Some services are mocked / passed into the constructor.
 * Others are used as-is.
 */
public class ServiceLocatorMock
        extends ServiceLocator {

    @NonNull
    private final LocaleList systemLocaleList;

    @NonNull
    private final StylesHelper stylesHelper;
    @Mock
    private final CoverStorage coverStorage;

    public ServiceLocatorMock(@NonNull final Context context,
                              @NonNull final LocaleList systemLocaleList,
                              @NonNull final StylesHelper stylesHelper,
                              @NonNull final CoverStorage coverStorage) {
        super(context);
        this.systemLocaleList = systemLocaleList;
        this.stylesHelper = stylesHelper;
        this.coverStorage = coverStorage;
    }

    @NonNull
    @Override
    public Bundle newBundle() {
        return BundleMock.create();
    }

    @NonNull
    @Override
    public LocaleList getSystemLocaleList() {
        return systemLocaleList;
    }


    @NonNull
    public StylesHelper getStylesHelper() {
        return stylesHelper;
    }

    @NonNull
    @Override
    public NetworkChecker getNetworkChecker() {
        return new TestNetworkChecker(true);
    }

    @NonNull
    @Override
    public CoverStorage getCoverStorage() {
        return coverStorage;
    }
}
