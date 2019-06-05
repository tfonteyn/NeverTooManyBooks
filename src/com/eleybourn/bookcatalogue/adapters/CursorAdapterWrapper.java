package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A fairly simple {@link RecyclerView.Adapter} wrapper around {@link CursorAdapter}.
 * Not all methods are exposed, just enough to have it working for most usages.
 * <p>
 * The {@link RecyclerView.ViewHolder} as used here is oblivious about the real view members.
 */
public class CursorAdapterWrapper
        extends RecyclerView.Adapter<CursorAdapterWrapper.Holder> {

    /** The wrapper adapter. */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    protected final CursorAdapter mCursorAdapter;

    /** The context that was used to create the CursorAdapter. */
    @NonNull
    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context       MUST be the same context as used to create the cursorAdapter.
     * @param cursorAdapter Adapter to wrap.
     */
    public CursorAdapterWrapper(@NonNull final Context context,
                                @NonNull final CursorAdapter cursorAdapter) {
        mContext = context;
        mCursorAdapter = cursorAdapter;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {

        View itemView = mCursorAdapter.newView(mContext, mCursorAdapter.getCursor(), parent);
        return new Holder(itemView);
    }

    @Override
    @CallSuper
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        mCursorAdapter.getCursor().moveToPosition(position);
        mCursorAdapter.bindView(holder.itemView, mContext, mCursorAdapter.getCursor());
    }

    @Override
    public int getItemCount() {
        return mCursorAdapter.getCount();
    }

    @Override
    public int getItemViewType(final int position) {
        return mCursorAdapter.getItemViewType(position);
    }

    @Override
    public long getItemId(final int position) {
        return mCursorAdapter.getItemId(position);
    }

    static class Holder
            extends RecyclerView.ViewHolder {

        Holder(@NonNull final View itemView) {
            super(itemView);
        }
    }
}
