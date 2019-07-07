package com.eleybourn.bookcatalogue.dialogs;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 *
 * TODO: this is overkill... Redo using {@link com.eleybourn.bookcatalogue.entities.Entity}
 *
 * @param <T> - type of item in the checklist
 */
public interface CheckListItem<T>
        extends Parcelable {

    /**
     * @return the item we're encapsulating.
     */
    T getItem();

    boolean isChecked();

    void setChecked(boolean selected);

    /**
     * @param context Current context, for accessing resources.
     *
     * @return the label to use in a {@link CheckListDialogFragment}.
     */
    String getLabel(@NonNull Context context);
}
