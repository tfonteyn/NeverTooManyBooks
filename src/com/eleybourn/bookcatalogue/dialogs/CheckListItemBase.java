package com.eleybourn.bookcatalogue.dialogs;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.dialogs.CheckListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @param <T> type of encapsulated item
 */
public abstract class CheckListItemBase<T> implements CheckListItem<T> {
    private boolean selected;
    private T item;

    protected CheckListItemBase() {
    }

    public CheckListItemBase(@NonNull final T item, final boolean selected) {
        this.item = item;
        this.selected = selected;
    }

    @NonNull
    @Override
    public T getItem() {
        return item;
    }

    @Override
    public boolean getSelected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;
    }

    /**
     * extract a List with the *selected* items from a List with the encapsulated items
     */
    @Override
    @NonNull
    public ArrayList<T> extractList(@NonNull final List<? extends CheckListItem> list) {
        ArrayList<T> result = new ArrayList<>();
        for (CheckListItem<?> entry : list) {
            if (entry.getSelected()) {
                //noinspection unchecked
                result.add((T) entry.getItem());
            }
        }
        return result;
    }
}
