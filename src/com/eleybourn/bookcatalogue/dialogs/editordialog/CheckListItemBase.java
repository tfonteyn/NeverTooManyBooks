package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The main reason that you need to extend this is because each type of encapsulated item
 * will have its own way of storing a label (to display next to the checkbox).
 * Using .toString is not really a nice solution, hence... extends this class
 * and implement: String {@link CheckListItem#getLabel()}
 *
 * @param <T> type of encapsulated item
 */
public abstract class CheckListItemBase<T> implements CheckListItem<T>, Parcelable {
    private boolean selected;
    protected T item;

    protected CheckListItemBase() {
    }

    protected CheckListItemBase(final @NonNull T item, final boolean selected) {
        this.item = item;
        this.selected = selected;
    }

    /**
     * Subclass must handle the {@link #item}
     */
    protected CheckListItemBase(Parcel in) {
        selected = in.readByte() != 0;
    }

    /**
     * Subclass must handle the {@link #item}
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (selected ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public T getItem() {
        return item;
    }

    /** label to use in a {@link CheckListEditorDialogFragment.CheckListEditorDialog} */
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
