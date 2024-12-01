/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ViewBookOnWebsiteHandler;
import com.hardbacknutter.nevertoomanybooks.utils.MenuHandler;

public final class MenuHandlerFactory {

    private MenuHandlerFactory() {
    }

    /**
     * Create the list of handlers.
     *
     * @param context Current context
     *
     * @return unmodifiable list
     */
    @NonNull
    public static List<MenuHandler> create(@NonNull final Context context) {
        final List<MenuHandler> list = new ArrayList<>();

        list.add(new ViewBookOnWebsiteHandler());

        //noinspection DataFlowIssue
        Site.Type.Data
                .getSites()
                .stream()
                .map(Site::getEngineId)
                .map(EngineId::getConfig)
                .filter(Objects::nonNull)
                .map(config -> config.createShoppingMenuHandler(context))
                .flatMap(Optional::stream)
                .forEach(list::add);

        return list;
    }
}
