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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;

/**
 * A {@link LocaleResolver} which forcefully sets the {@code .}
 * as the decimal separator.
 * <p>
 * Pass this to {@link BookParserHelper#setMoneyParserLocaleResolver(LocaleResolver)}
 * if a site uses {@code .} while the site Locale uses {@code ,}.
 * <p>
 * We simply use override the site Locale with {@link Locale#UK}.
 */
public final class LocaleResolverMoneyDecimalPoint
        implements LocaleResolver {

    /** Singleton. */
    public static final LocaleResolver INSTANCE = new LocaleResolverMoneyDecimalPoint();

    @NonNull
    @Override
    public List<Locale> resolveLocales(@NonNull final Context context,
                                       @NonNull final Locale siteLocale) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        return LocaleListUtils.asList(Locale.UK, userLocales);
    }
}
