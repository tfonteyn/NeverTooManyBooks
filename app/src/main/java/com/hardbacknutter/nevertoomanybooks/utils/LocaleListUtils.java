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

package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.res.Resources;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;

// TODO: check existing locales for a match of the language (disregard country)
//  and if present, use that one.
//  That would ensure the country as preferred by the user is also used.
public final class LocaleListUtils {

    private LocaleListUtils() {
    }

    public static List<Locale> asList(@NonNull final Context context) {
        final LocaleList localeList = context.getResources().getConfiguration().getLocales();
        return asList(localeList);
    }

    public static List<Locale> asList(@NonNull final LocaleList localeList) {
        final LinkedHashSet<Locale> locales = new LinkedHashSet<>();
        for (int i = 0; i < localeList.size(); i++) {
            locales.add(localeList.get(i));
        }
        return new ArrayList<>(locales);
    }

    /**
     * Return the device Locale.
     * <p>
     * When running a JUnit test, this method will always return {@code Locale.US}.
     *
     * @return Locale
     */
    @NonNull
    public static Locale getSystemLocale() {
        // While running JUnit tests we cannot get access or mock Resources.getSystem(),
        // ... so we need to cheat.
        if (BuildConfig.DEBUG && TestFlags.isJUnit) {
            return Locale.US;
        }

        return Resources.getSystem().getConfiguration().getLocales().get(0);
    }

    @NonNull
    static List<Locale> getSystemLocales() {
        // While running JUnit tests we cannot get access or mock Resources.getSystem(),
        // ... so we need to cheat.
        if (BuildConfig.DEBUG && TestFlags.isJUnit) {
            return List.of(Locale.US);
        }

        return asList(Resources.getSystem().getConfiguration().getLocales());
    }
}
