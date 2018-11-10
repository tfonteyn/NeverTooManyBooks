package com.eleybourn.bookcatalogue.dialogs.editordialog;

public interface CheckListItem<T> {
    /** the item we're encapsulating */
    T getItem();

    /** label to use in a {@link CheckListEditorDialog} */
    String getLabel();

    void setSelected(final boolean selected);
    boolean getSelected();
}
