package com.eleybourn.bookcatalogue.dialogs;

import android.support.annotation.NonNull;

import java.util.List;

public interface CheckListItem<T> {
    /** the item we're encapsulating */
    T getItem();

    /** label to use in a {@link CheckListEditorDialog} */
    String getLabel();

    void setSelected(final boolean selected);
    boolean getSelected();
}
