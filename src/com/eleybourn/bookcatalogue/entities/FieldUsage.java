package com.eleybourn.bookcatalogue.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * How to handle a data field when updating the entity it belongs to.
 * e.g. skip it, overwrite the value, etc...
 */
public class FieldUsage {

    /** a key, usually from {@link UniqueId}. */
    @NonNull
    public final String fieldId;
    /** is the field a list type. */
    private final boolean mIsList;
    /** label to show to the user. */
    @StringRes
    private final int mNameStringId;
    /** how to use this field. */
    @NonNull
    public Usage usage;


    /**
     * Constructor.
     *
     * @param fieldId      key
     * @param nameStringId label to show to the user.
     * @param usage        how to use this field.
     * @param isList       {@code true} if this field is a list type.
     */
    public FieldUsage(@NonNull final String fieldId,
                      @StringRes final int nameStringId,
                      @NonNull final Usage usage,
                      final boolean isList) {
        this.fieldId = fieldId;
        mNameStringId = nameStringId;
        this.usage = usage;
        mIsList = isList;
    }

    public boolean isWanted() {
        return usage != Usage.Skip;
    }

    public boolean isList() {
        return mIsList;
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
     * if (isList): Skip -> CopyIfBlank -> Merge -> Overwrite -> Skip
     * else       : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        usage = usage.nextState(mIsList);
    }

    public enum Usage {
        Skip, CopyIfBlank, Merge, Overwrite;

        @NonNull
        Usage nextState(final boolean isList) {
            switch (this) {
                case Skip:
                    return CopyIfBlank;
                case CopyIfBlank:
                    if (isList) {
                        return Merge;
                    } else {
                        return Overwrite;
                    }
                case Merge:
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
                case Merge:
                    return R.string.lbl_field_usage_add_extra;
                case Overwrite:
                    return R.string.lbl_field_usage_overwrite;
                default:
                    return R.string.lbl_field_usage_skip;
            }
        }
    }
}
