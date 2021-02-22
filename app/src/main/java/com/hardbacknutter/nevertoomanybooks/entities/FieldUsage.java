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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * How to handle a data field when updating the entity it belongs to.
 * e.g. skip it, overwrite the value, etc...
 */
public final class FieldUsage {

    @NonNull
    public final String fieldId;

    /** label to show to the user. */
    @StringRes
    private final int mLabelResId;
    /** Default usage at creation time. */
    @NonNull
    private final Usage mDefValue;
    /** Is the field capable of appending extra data. */
    private final boolean mCanAppend;
    /** how to use this field. */
    @NonNull
    private Usage mUsage;

    /**
     * private Constructor; use factory methods instead.
     *
     * @param fieldId    key
     * @param labelResId Field label resource id
     * @param usage      how to use this field.
     * @param defValue   default usage
     * @param canAppend  {@code true} if this field is capable of appending extra data.
     */
    private FieldUsage(@NonNull final String fieldId,
                       @StringRes final int labelResId,
                       @NonNull final Usage usage,
                       @NonNull final Usage defValue,
                       final boolean canAppend) {
        this.fieldId = fieldId;
        mLabelResId = labelResId;
        mUsage = usage;
        mDefValue = defValue;
        mCanAppend = canAppend;
    }

    /**
     * Constructor for a <strong>simple</strong> field.
     * <p>
     * The fieldId is used as the preference key.
     *
     * @param fieldId   Field name
     * @param nameResId Field label resource id
     * @param global    Global preferences
     * @param defValue  default Usage for this field
     *
     * @return new instance
     */
    public static FieldUsage create(@NonNull final String fieldId,
                                    @StringRes final int nameResId,
                                    @NonNull final SharedPreferences global,
                                    @NonNull final Usage defValue) {
        final Usage initialValue = Usage.read(global, fieldId, defValue);
        return new FieldUsage(fieldId, nameResId, initialValue, defValue, false);
    }

    /**
     * Constructor for a <strong>list</strong> field.
     * <p>
     * The default usage for a list field is always {@link Usage#Append}.
     *
     * @param fieldId     Field name
     * @param nameResId   Field label resource id
     * @param global Global preferences
     *
     * @return new instance
     */
    public static FieldUsage createListField(@NonNull final String fieldId,
                                             @StringRes final int nameResId,
                                             @NonNull final SharedPreferences global) {
        final Usage initialValue = Usage.read(global, fieldId, Usage.Append);
        return new FieldUsage(fieldId, nameResId, initialValue, Usage.Append, true);
    }

    public void reset() {
        mUsage = mDefValue;
    }

    /**
     * Constructor for a related field depending on this field.
     *
     * @param fieldId   key
     * @param nameResId Field label resource id
     *
     * @return a FieldUsage record for the given field.
     */
    public FieldUsage createRelatedField(@NonNull final String fieldId,
                                         @StringRes final int nameResId) {
        return new FieldUsage(fieldId, nameResId, mUsage, mDefValue, mCanAppend);
    }

    public boolean isWanted() {
        return mUsage != Usage.Skip;
    }

    @NonNull
    public Usage getUsage() {
        return mUsage;
    }

    public void setUsage(@NonNull final Usage usage) {
        mUsage = usage;
    }

    /**
     * Get the label for the field.
     *
     * @return label resource id
     */
    @StringRes
    public int getLabelResId() {
        return mLabelResId;
    }

    /**
     * Get the label for the currently selected usage.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    public String getUsageLabel(@NonNull final Context context) {
        return context.getString(mUsage.getLabelId());
    }

    /**
     * Cycle to the next Usage stage.
     * <p>
     * if (canAppend): Skip -> CopyIfBlank -> Append -> Overwrite -> Skip
     * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        mUsage = mUsage.nextState(mCanAppend);
    }

    @Override
    @NonNull
    public String toString() {
        return "FieldUsage{"
               + "fieldId=`" + fieldId + '`'
               + ", mCanAppend=" + mCanAppend
               + ", mNameStringId=" + mLabelResId
               + ", mDefValue=" + mDefValue
               + ", mUsage=" + mUsage
               + '}';
    }

    public enum Usage {
        // NEVER change the order, we store the ordinal in SharedPreferences.
        Skip, CopyIfBlank, Append, Overwrite;

        private static final String PREFS_PREFIX_FIELD_USAGE = "fields.update.usage.";

        public static Usage read(@NonNull final SharedPreferences global,
                                 @NonNull final String key,
                                 @NonNull final Usage defValue) {
            final int ordinal = global.getInt(PREFS_PREFIX_FIELD_USAGE + key, -1);
            if (ordinal != -1) {
                return values()[ordinal];
            } else {
                return defValue;
            }
        }

        public void write(@NonNull final SharedPreferences.Editor ed,
                          @NonNull final String key) {
            ed.putInt(PREFS_PREFIX_FIELD_USAGE + key, ordinal());
        }

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

                case Overwrite:
                    return Skip;
            }
            return Skip;
        }

        /**
         * Return the user readable label id.
         *
         * @return string id
         */
        @StringRes
        int getLabelId() {
            switch (this) {
                case CopyIfBlank:
                    return R.string.lbl_field_usage_copy_if_blank;
                case Append:
                    return R.string.lbl_field_usage_append;
                case Overwrite:
                    return R.string.lbl_field_usage_overwrite;
                case Skip:
                    return R.string.action_skip;
            }
            return R.string.action_skip;
        }
    }
}
