/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;

/**
 * How to handle a data field when updating the entity it belongs to.
 * e.g. skip it, overwrite the value, etc...
 */
public class FieldUsage {

    /** a key, usually from {@link UniqueId}. */
    @NonNull
    public final String fieldId;
    /** Is the field capable of appending extra data. */
    private final boolean mAllowAppend;
    /** label to show to the user. */
    @StringRes
    private final int mNameStringId;
    /** how to use this field. */
    @NonNull
    public Usage usage;

    /**
     * Constructor.
     *
     * @param nameStringId label to show to the user.
     * @param usage        how to use this field.
     * @param allowAppend  {@code true} if this field is capable of appending extra data.
     * @param fieldId      key
     */
    public FieldUsage(@StringRes final int nameStringId,
                      @NonNull final Usage usage,
                      final boolean allowAppend,
                      @NonNull final String fieldId) {
        this.fieldId = fieldId;
        mNameStringId = nameStringId;
        this.usage = usage;
        mAllowAppend = allowAppend;
    }

    public boolean isWanted() {
        return usage != Usage.Skip;
    }

    public boolean canAppend() {
        return mAllowAppend;
    }

    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(mNameStringId);
    }

    @NonNull
    public String getUsageInfo(@NonNull final Context context) {
        return context.getString(usage.getStringId());
    }

    /**
     * Cycle to the next Usage stage.
     * <p>
     * if (canAppend): Skip -> CopyIfBlank -> Append -> Overwrite -> Skip
     * else       : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        usage = usage.nextState(mAllowAppend);
    }

    public enum Usage {
        Skip, CopyIfBlank, Append, Overwrite;

        @NonNull
        Usage nextState(final boolean allowAppend) {
            switch (this) {
                case Skip:
                    return CopyIfBlank;
                case CopyIfBlank:
                    if (allowAppend) {
                        return Append;
                    } else {
                        return Overwrite;
                    }
                case Append:
                    return Overwrite;

                //case Overwrite:
                default:
                    return Skip;
            }
        }

        @StringRes
        int getStringId() {
            switch (this) {
                case CopyIfBlank:
                    return R.string.lbl_field_usage_copy_if_blank;
                case Append:
                    return R.string.lbl_field_usage_append;
                case Overwrite:
                    return R.string.lbl_field_usage_overwrite;
                default:
                    return R.string.lbl_field_usage_skip;
            }
        }
    }
}
