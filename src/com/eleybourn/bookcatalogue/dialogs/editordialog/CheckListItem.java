package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.content.Context;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 *
 * TOMF: this is overkill... I must have been tired. Redo using {@link com.eleybourn.bookcatalogue.entities.Entity}
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
     * @param context caller context
     *
     * @return the label to use in a {@link CheckListEditorDialogFragment}.
     */
    String getLabel(@NonNull final Context context);
}
