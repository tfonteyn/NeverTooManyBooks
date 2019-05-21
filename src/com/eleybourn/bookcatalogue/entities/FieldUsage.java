package com.eleybourn.bookcatalogue.entities;

import android.content.res.Resources;

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


    public FieldUsage(@NonNull final String fieldId,
                      @StringRes final int nameStringId,
                      @NonNull final Usage usage,
                      final boolean isList) {
        this.fieldId = fieldId;
        mNameStringId = nameStringId;
        this.usage = usage;
        mIsList = isList;
    }

    public boolean isSelected() {
        return (usage != Usage.Skip);
    }

    public String getLabel(@NonNull final Resources resources) {
        return resources.getString(mNameStringId);
    }

    public String getUsageInfo(@NonNull final Resources resources) {
        return resources.getString(usage.getStringId());
    }

    /**
     * Cycle to the next Usage stage.
     * <p>
     * if (isList): Skip -> CopyIfBlank -> AddExtra -> Overwrite -> Skip
     * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        usage = usage.nextState(mIsList);
    }

    public enum Usage {
        Skip, CopyIfBlank, AddExtra, Overwrite;

        @NonNull
        public Usage nextState(final boolean isList) {
            switch (this) {
                case Skip:
                    return CopyIfBlank;
                case CopyIfBlank:
                    if (isList) {
                        return AddExtra;
                    } else {
                        return Overwrite;
                    }
                case AddExtra:
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
                case AddExtra:
                    return R.string.lbl_field_usage_add_extra;
                case Overwrite:
                    return R.string.lbl_field_usage_overwrite;
                default:
                    return R.string.usage_skip;
            }
        }
    }
}
