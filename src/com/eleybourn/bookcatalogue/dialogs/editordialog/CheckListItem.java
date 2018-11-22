package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.os.Parcelable;

public interface CheckListItem<T>  extends Parcelable {
    /** the item we're encapsulating */
    T getItem();

    /** label to use in a {@link CheckListEditorDialogFragment.CheckListEditorDialog} */
    String getLabel();

    void setSelected(final boolean selected);
    boolean getSelected();
}
