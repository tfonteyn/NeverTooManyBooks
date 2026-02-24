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

package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

public final class MapperFactory {

    private MapperFactory() {
    }

    /**
     * Constructor.
     *
     * @param context Current context
     *
     * @return list of active mappers
     */
    @NonNull
    public static Collection<Mapper> create(@NonNull final Context context) {
        final Collection<Mapper> mappers = new ArrayList<>();

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        ColorMapper.create(userLocale).ifPresent(mappers::add);
        FormatMapper.create(userLocale).ifPresent(mappers::add);

        mappers.add(new TagMapper(userLocale));

        return mappers;
    }
}
