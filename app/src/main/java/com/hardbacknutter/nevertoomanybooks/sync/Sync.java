/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoHandler;

/**
 * A convenience class where all the 'isEnabled' methods are duplicated.
 * First attempt to abstract the sync sites.
 */
public final class Sync {

    private Sync() {
    }

    public static boolean isEnabled(@NonNull final SharedPreferences global,
                                    @NonNull final Site site) {
        switch (site) {
            case Calibre:
                return CalibreHandler.isSyncEnabled(global);
            case Goodreads:
                return GoodreadsHandler.isSyncEnabled(global);
            case StripInfo:
                return StripInfoHandler.isSyncEnabled(global);

            default:
                throw new IllegalArgumentException();
        }
    }

    public static boolean isAnyEnabled(@NonNull final SharedPreferences global) {
        return CalibreHandler.isSyncEnabled(global)
               || GoodreadsHandler.isSyncEnabled(global)
               || StripInfoHandler.isSyncEnabled(global);
    }

    public enum Site {
        Calibre,
        Goodreads,
        StripInfo;

        public boolean isEnabled(@NonNull final SharedPreferences global) {
            switch (this) {
                case Calibre:
                    return CalibreHandler.isSyncEnabled(global);
                case Goodreads:
                    return GoodreadsHandler.isSyncEnabled(global);
                case StripInfo:
                    return StripInfoHandler.isSyncEnabled(global);

                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
