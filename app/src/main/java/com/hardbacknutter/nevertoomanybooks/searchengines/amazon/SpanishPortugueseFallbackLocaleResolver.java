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

package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.LocaleListResolver;

/**
 * The Amazon site for Portugal redirects to Spain.
 * <p>
 * When we detect a Spanish language Amazon site, we simply add the Portuguese Locale
 * <strong>after</strong> the Spanish one.
 * <p>
 * This is mainly/only used for date parsing (month names).
 */
class SpanishPortugueseFallbackLocaleResolver
        implements LocaleListResolver {

    public static final LocaleListResolver INSTANCE = new SpanishPortugueseFallbackLocaleResolver();

    private static final String SPANISH = "es";
    // "pt" and "pt_BR" use the same spelling for month names
    private static final Locale PORTUGUESE = new Locale("pt");

    @NonNull
    @Override
    public List<Locale> resolveLocales(@NonNull final Context context,
                                       @NonNull final Locale targetLocale) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = new ArrayList<>(
                LocaleListUtils.asList(targetLocale, userLocales));

        if (SPANISH.equals(targetLocale.getLanguage())) {
            allLocales.add(1, PORTUGUESE);
        }

        return allLocales;
    }
}
