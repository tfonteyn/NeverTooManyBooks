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

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ContractOutput;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public final class EditStyleOutput
        implements ContractOutput {

    private static final String TAG = "EditStyleOutput";
    private static final String BKEY_MODIFIED = TAG + ":m";
    private static final String BKEY_TEMPLATE_UUID = TAG + ":template";

    /** The uuid which was passed into the {@link EditStyleInput} for editing. */
    @NonNull
    private final String templateUuid;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    private final boolean modified;

    /**
     * Either a new UUID if we cloned a style, or the UUID of the style we edited.
     */
    @Nullable
    private final String styleUuid;

    /**
     * Constructor.
     *
     * @param templateUuid uuid of the original style we cloned (different from current)
     *                     or edited (same as current).
     * @param modified     flag; whether the style was modified (either created ot updated)
     * @param styleUuid    uuid of the modified (or newly created) style
     */
    EditStyleOutput(@NonNull final String templateUuid,
                    final boolean modified,
                    @Nullable final String styleUuid) {
        this.templateUuid = templateUuid;
        this.modified = modified;
        this.styleUuid = styleUuid;
    }

    @NonNull
    static EditStyleOutput fromBundle(@NonNull final Bundle args) {
        final String templateUuid = Objects.requireNonNull(
                args.getString(BKEY_TEMPLATE_UUID), BKEY_TEMPLATE_UUID);
        final boolean modified = args.getBoolean(BKEY_MODIFIED, false);
        final String styleUuid = args.getString(Style.BKEY_UUID);

        return new EditStyleOutput(templateUuid, modified, styleUuid);
    }

    @Override
    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putString(BKEY_TEMPLATE_UUID, templateUuid);
        args.putBoolean(BKEY_MODIFIED, modified);
        args.putString(Style.BKEY_UUID, styleUuid);
        return args;
    }

    @NonNull
    String getTemplateUuid() {
        return templateUuid;
    }

    boolean isModified() {
        return modified;
    }

    /**
     * Get the UUID.
     *
     * @return {@link Optional} with a non-blank UUID
     */
    @NonNull
    public Optional<String> getStyleUuid() {
        if (styleUuid == null || styleUuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(styleUuid);
    }

    @Override
    @NonNull
    public String toString() {
        return "EditStyleOutput{"
               + "templateUuid='" + templateUuid + '\''
               + ", modified=" + modified
               + ", styleUuid='" + styleUuid + '\''
               + '}';
    }
}
