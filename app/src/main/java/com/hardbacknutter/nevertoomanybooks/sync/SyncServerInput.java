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

package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Objects;

public class SyncServerInput {

    private static final String TAG = "SyncServerInput";
    private static final String BKEY_SERVER_ID = TAG + ":id";

    @NonNull
    private final SyncServerId syncServerId;

    public SyncServerInput(@NonNull final SyncServerId syncServerId) {
        this.syncServerId = syncServerId;
    }

    @NonNull
    public static SyncServerInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final SyncServerId syncServer = Objects.requireNonNull(
                args.getParcelable(BKEY_SERVER_ID), BKEY_SERVER_ID);

        return new SyncServerInput(syncServer);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_SERVER_ID, syncServerId);

        return args;
    }

    @NonNull
    SyncServerId getSyncServerId() {
        return syncServerId;
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncServerInput{"
               + "syncServerId=" + syncServerId
               + '}';
    }
}
