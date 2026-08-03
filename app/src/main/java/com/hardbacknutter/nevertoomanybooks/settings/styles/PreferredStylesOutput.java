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

package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ContractOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class PreferredStylesOutput
        implements ContractOutput {

    private static final String TAG = "PreferredStylesOutput";
    private static final String BKEY_MODIFIED = TAG + ":m";
    @Nullable
    private final String styleUuid;
    private final boolean modified;

    /**
     * Constructor.
     *
     * @param styleUuid Return the currently selected style UUID, so the caller can apply it.
     *                  This is independent of any modification to this or another style,
     *                  or the order of the styles.
     * @param modified  flag indicating if <strong>anything at all</strong> was modified.
     *                  This is independent of the returned style.
     */
    PreferredStylesOutput(@Nullable final String styleUuid,
                          final boolean modified) {
        this.styleUuid = styleUuid;
        this.modified = modified;
    }

    @NonNull
    static PreferredStylesOutput fromBundle(@NonNull final Bundle args) {
        final String uuid = args.getString(Style.BKEY_UUID);
        final boolean modified = args.getBoolean(BKEY_MODIFIED, false);
        return new PreferredStylesOutput(uuid, modified);
    }

    @Override
    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putString(Style.BKEY_UUID, styleUuid);
        args.putBoolean(BKEY_MODIFIED, modified);

        return args;
    }

    /**
     * Get the UUID.
     *
     * @return {@link Optional} with a non-blank UUID
     */
    @NonNull
    public Optional<String> getSelectedStyleUuid() {
        if (styleUuid == null || styleUuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(styleUuid);
    }

    public boolean isModified() {
        return modified;
    }

    @Override
    @NonNull
    public String toString() {
        return "PreferredStylesOutput{"
               + "styleUuid='" + styleUuid + '\''
               + ", modified=" + modified
               + '}';
    }
}
