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

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

public class RegistrationOutput {

    private static final String TAG = "RegistrationOutput";
    private static final String BKEY_ENGINE_ID = TAG + ":engineId";

    @NonNull
    private final EngineId engineId;

    RegistrationOutput(@NonNull final EngineId engineId) {
        this.engineId = engineId;
    }

    /**
     * Constructor.
     *
     * @param args to read
     *
     * @return instance
     */
    @NonNull
    public static RegistrationOutput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final EngineId engineId = Objects.requireNonNull(args.getParcelable(BKEY_ENGINE_ID));
        return new RegistrationOutput(engineId);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_ENGINE_ID, engineId);
        return args;
    }

    /**
     * Get the engine id we're registering.
     *
     * @return id
     */
    @NonNull
    public EngineId getEngineId() {
        return engineId;
    }
}
