package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.os.Parcelable;

/**
 *
 * @param <T> type of item in the checklist
 */
public interface CheckListItem<T>
        extends Parcelable {

    /** the item we're encapsulating. */
    T getItem();

    /** label to use in a {@link CheckListEditorDialogFragment.CheckListEditorDialog}. */
    String getLabel();

    boolean isSelected();

    void setSelected(boolean selected);
}
