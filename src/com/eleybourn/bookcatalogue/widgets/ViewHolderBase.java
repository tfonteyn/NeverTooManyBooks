package com.eleybourn.bookcatalogue.widgets;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.eleybourn.bookcatalogue.R;

/**
 * Holder pattern for each row in a RecyclerView.
 * <p>
 * Mainly meant for {@link TouchRecyclerViewCFS} but not dependent on it.
 * <ul>Extends the original with support for:
 * <li>typed encapsulated item
 * <li>a 'delete' button
 * <li>a 'checkable' button
 * <li>rowDetailsView (long)click support.
 * </ul>
 * <ul>Uses pre-defined id's:
 * <li>R.id.TLV_ROW_DETAILS
 * <li>R.id.TLV_ROW_DELETE
 * <li>R.id.TLV_ROW_CHECKABLE
 * </ul>
 *
 * @param <T> type of the encapsulated item for the row.
 */
public class ViewHolderBase<T>
        extends RecyclerView.ViewHolder {

    /** The details part of the row (or the row itself). */
    @NonNull
    protected final View rowDetailsView;
    /** optional row delete button. */
    @Nullable
    protected final View mDeleteButton;
    /** optional row checkable button. */
    @Nullable
    protected final View mCheckableButton;
    /** The item that is represented by this row. */
    protected T item;

    public ViewHolderBase(@NonNull final View itemView) {
        super(itemView);

        // If we use a TouchListView, then don't enable the whole row, so buttons keep working
        View rd = itemView.findViewById(R.id.TLV_ROW_DETAILS);
        // but if we did not define a details row subview, use the row itself anyhow.
        rowDetailsView = rd != null ? rd : itemView;
        rowDetailsView.setFocusable(false);

        // optional
        mDeleteButton = itemView.findViewById(R.id.TLV_ROW_DELETE);
        mCheckableButton = itemView.findViewById(R.id.TLV_ROW_CHECKABLE);

    }

    public T getItem() {
        return item;
    }

    public void setItem(final T item) {
        this.item = item;
    }
}
