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
package com.hardbacknutter.nevertoomanybooks;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

public class RecreateViewModel
        extends ViewModel {

    @Nullable
    private Recreate recreate;

    public void onCreate() {
        if (recreate == null) {
            recreate = Recreate.No;
        }
    }

    public void setRecreationRequired() {
        this.recreate = Recreate.Required;
    }

    boolean isRecreating() {
        return recreate == Recreate.Pending;
    }

    public boolean isRecreationRequired() {
        if (recreate == Recreate.Required) {
            recreate = Recreate.Pending;
            return true;

        } else {
            recreate = Recreate.No;
        }

        return false;
    }

    private enum Recreate {
        /** Situation normal. */
        No,
        /** Activity is in need of recreating. */
        Required,
        /** A recreate() action has been triggered. */
        Pending
    }

}
