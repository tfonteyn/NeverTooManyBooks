package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The main reason that you need to extend this is because each type of encapsulated item
 * will have its own way of storing a label (to display next to the checkbox).
 * Using .toString is not really a nice solution, hence... extends this class
 * and implement: String {@link CheckListItem#getLabel()}
 *
 * BUT.. not abstract, so the {@link #extractList(List)} method can be used
 *
 * @param <T> type of encapsulated item
 */
public class CheckListItemBase<T> implements CheckListItem<T> {
    private boolean selected;
    private T item;

    public CheckListItemBase() {
    }

    protected CheckListItemBase(final @NonNull T item, final boolean selected) {
        this.item = item;
        this.selected = selected;
    }

    @NonNull
    @Override
    public T getItem() {
        return item;
    }

    /** label to use in a {@link CheckListEditorDialog} */
    @Override
    public String getLabel() {
        throw new java.lang.UnsupportedOperationException("must be overridden");
    }

    @Override
    public boolean getSelected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;
    }

    @NonNull
    public ArrayList<T> extractList(final @NonNull List<CheckListItem<T>> list) {
        ArrayList<T> result = new ArrayList<>();
        for (CheckListItem<T> entry : list) {
            if (entry.getSelected()) {
                result.add(entry.getItem());
            }
        }
        return result;
    }
}
