package com.eleybourn.bookcatalogue.widgets;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Really dumb implementation that simple delegates everything to {@link #onChanged()}.
 */
public abstract class SimpleAdapterDataObserver
        extends RecyclerView.AdapterDataObserver {

    public void onItemRangeChanged(int positionStart, int itemCount) {
        onChanged();
    }

    public void onItemRangeInserted(int positionStart, int itemCount) {
        onChanged();
    }

    public void onItemRangeRemoved(int positionStart, int itemCount) {
        onChanged();
    }

    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        onChanged();
    }
}
