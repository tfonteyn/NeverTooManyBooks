package com.eleybourn.bookcatalogue.adapters;

import android.support.annotation.NonNull;
import android.view.View;

/**
 * @param <T>
 *
 * @see SimpleListAdapter<T>
 */
public interface SimpleListAdapterRowActionListener<T> {

    void onRowDown(final @NonNull View target, final @NonNull T item, final int position);

    void onRowUp(final @NonNull View target, final @NonNull T item, final int position);

    /**
     * @return <tt>true</tt>if delete is allowed to happen
     */
    boolean onRowDelete(final @NonNull View target, final @NonNull T item, final int position);

    /**
     * Called when an otherwise inactive part of the row is clicked.
     *
     * @param target The view clicked
     * @param item   The object associated with this row
     */
    void onRowClick(final @NonNull View target, final @NonNull T item, final int position);

    /**
     * Called when an otherwise inactive part of the row is long clicked.
     *
     * @param target The view clicked
     * @param item   The object associated with this row
     *
     * @return <tt>true</tt>if handled
     */
    boolean onRowLongClick(final @NonNull View target, final @NonNull T item, final int position);

    void onListChanged();

    /**
     * Call to set up the row view.
     *
     * @param convertView The target row view object
     * @param item        The object (or type T) from which to draw values.
     */
    void onGetView(final @NonNull View convertView, final @NonNull T item);
}
