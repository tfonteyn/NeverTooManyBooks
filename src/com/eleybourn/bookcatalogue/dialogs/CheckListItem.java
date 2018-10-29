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

    /**
     * extract a List with the actual items from a List with the encapsulated items.
     * It's up to the implementation to include all items, or only the selected ones.
     */
    List<T> extractList(@NonNull final List<? extends CheckListItem> list);
}
