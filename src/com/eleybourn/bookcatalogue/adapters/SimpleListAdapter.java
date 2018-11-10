/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.adapters;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.List;
import java.util.Objects;

/**
 * TODO: RecyclerView
 * If you are considering using array adapter with a ListView, consider using
 * {@link android.support.v7.widget.RecyclerView} instead.
 * RecyclerView offers similar features with better performance and more flexibility than
 * ListView provides.
 * See the
 * <a href="https://developer.android.com/guide/topics/ui/layout/recyclerview.html">
 * Recycler View</a> guide.</p>
 *
 * {@link ArrayAdapter} to manage rows of an arbitrary type with row movement via clicking
 * on predefined sub-views, if present.
 *
 * The layout must have the top id of:
 * <pre>
 *    SLA_ROW  {@link SimpleListAdapterRowActionListener<T>#onRowClick}, unless SLA_ROW_DETAILS is defined.
 * </pre>
 *
 * The layout can optionally contain these "@+id/"  which will trigger the listed methods
 * <pre>
 *    SLA_ROW_DETAILS     {@link SimpleListAdapterRowActionListener<T>#onRowClick};
 *    SLA_ROW_UP          {@link SimpleListAdapterRowActionListener<T>#onRowUp}
 *    SLA_ROW_DOWN        {@link SimpleListAdapterRowActionListener<T>#onRowDown}
 *    SLA_ROW_DELETE      {@link SimpleListAdapterRowActionListener<T>#onRowDelete}
 * </pre>
 *
 * SLA_ROW is the complete row, SLA_ROW_DETAIL is a child of SLA_ROW.
 * So you should never have a SLA_ROW_DETAIL without an enclosing SLA_ROW element
 *
 * ids.xml has these predefined:
 * <pre>
 *     <item name="SLA_ROW" type="id" />
 *     <item name="SLA_ROW_DETAILS" type="id" />
 *     <item name="SLA_ROW_UP" type="id"/>
 *     <item name="SLA_ROW_DOWN" type="id"/>
 *     <item name="SLA_ROW_DELETE" type="id"/>
 *     <item name="SLA_ROW_POSITION" type="id"/>
 *     <item name="SLA_ROW_POSITION_TAG" type="id" />
 * 	</pre>
 *
 * @author Philip Warner
 */
public abstract class SimpleListAdapter<T> extends ArrayAdapter<T> {
    @LayoutRes
    private final int mRowViewId;
    @NonNull
    private final List<T> mList;


    @Nullable
    private final View.OnClickListener mRowClickListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            try {
                int pos = getViewRow(v);
                T item = getItem(pos);
                if (item != null) {
                    onRowClick(v, item, pos);
                }
            } catch (Exception e) {
                Logger.error(e);
            }
        }
    };
    @Nullable
    private final View.OnLongClickListener mRowLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(final @NonNull View v) {
            try {
                int pos = getViewRow(v);
                T item = getItem(pos);
                return item != null && onRowLongClick(v, item, pos);
            } catch (Exception e) {
                Logger.error(e);
            }
            return false;
        }
    };
    @Nullable
    private final View.OnClickListener mRowDeleteListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            try {
                int pos = getViewRow(v);
                T old = getItem(pos);
                if (old != null && onRowDelete(v, old, pos)) {
                    remove(old);
                    notifyDataSetChanged();
                    onListChanged();
                }
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.error(e);
            }
        }
    };
    @Nullable
    private final View.OnClickListener mRowDownListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            int pos = getViewRow(v);
            if (pos == (getCount() - 1))
                return;
            T old = getItem(pos);
            if (old == null) {
                return;
            }
            try {
                onRowDown(v, old, pos);

                mList.set(pos, getItem(pos + 1));
                mList.set(pos + 1, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.error(e);
            }
        }
    };
    @Nullable
    private final View.OnClickListener mRowUpListener = new View.OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {
            int pos = getViewRow(v);
            if (pos == 0)
                return;
            T old = getItem(pos - 1);
            if (old == null) {
                return;
            }
            try {
                onRowUp(v, old, pos);

                mList.set(pos - 1, getItem(pos));
                mList.set(pos, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.error(e);
            }

        }
    };

    // Options fields to (slightly) optimize lookups and prevent looking for fields that are not there.
    private boolean mCheckedFields = false;
    private boolean mHasPosition = false;
    private boolean mHasUp = false;
    private boolean mHasDown = false;
    private boolean mHasDelete = false;

    protected SimpleListAdapter(final @NonNull Context context,
                                final @LayoutRes int rowViewId,
                                final @NonNull List<T> list) {
        super(context, rowViewId, list);
        mRowViewId = rowViewId;
        mList = list;
    }

    @NonNull
    @Override
    public View getView(final int position, @Nullable View convertView, final @NonNull ViewGroup parent) {
        final T item = this.getItem(position);

        // Get the view; if not defined, load it.
        if (convertView == null) {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // If possible, ask the object for the view ID
            @LayoutRes
            int layout;
            if (item instanceof ViewProvider) {
                layout = ((ViewProvider) item).getViewId();
            } else {
                layout = mRowViewId;
            }
            //noinspection ConstantConditions
            convertView = vi.inflate(layout, null);
        }

        // Save this views position
        ViewTagger.setTag(convertView, R.id.SLA_ROW_POSITION_TAG, position);

        // If we use a TouchListView, then don't enable the whole row, so buttons keep working
        View row = convertView.findViewById(R.id.SLA_ROW_DETAILS);
        if (row == null) {
            // but if we did not define a details row subview, try row anyhow
            row = convertView.findViewById(R.id.SLA_ROW);
        }

        if (row != null) {
            row.setOnClickListener(mRowClickListener);
            row.setOnLongClickListener(mRowLongClickListener);
            row.setFocusable(false);
        }

        // If the object is not null, do some processing
        if (item != null) {
            // Try to set position value
            if (mHasPosition || !mCheckedFields) {
                TextView pt = convertView.findViewById(R.id.SLA_ROW_POSITION);
                if (pt != null) {
                    mHasPosition = true;
                    String text = Integer.toString(position + 1);
                    pt.setText(text);
                }
            }

            // Try to set the UP handler
            if (mHasUp || !mCheckedFields) {
                View up = convertView.findViewById(R.id.SLA_ROW_UP);
                if (up != null) {
                    up.setOnClickListener(mRowUpListener);
                    mHasUp = true;
                }
            }

            // Try to set the DOWN handler
            if (mHasDown || !mCheckedFields) {
                View dn = convertView.findViewById(R.id.SLA_ROW_DOWN);
                if (dn != null) {
                    dn.setOnClickListener(mRowDownListener);
                    mHasDown = true;
                }
            }

            // Try to set the DELETE handler
            if (mHasDelete || !mCheckedFields) {
                View del = convertView.findViewById(R.id.SLA_ROW_DELETE);
                if (del != null) {
                    del.setOnClickListener(mRowDeleteListener);
                    mHasDelete = true;
                }
            }

            // Ask the subclass to set other fields.
            try {
                onGetView(convertView, item);
            } catch (Exception e) {
                Logger.error(e);
            }
            convertView.setBackgroundResource(android.R.drawable.list_selector_background);

            mCheckedFields = true;
        }
        return convertView;
    }

    /**
     * Called by {@link #getView} to allow children to setup extra fields.
     */
    protected abstract void onGetView(final View convertView, final T item);

    /**
     * Find the first ancestor that has the ID SLA_ROW. This will be the complete row View.
     * Use the TAG on that to get the physical row number.
     *
     * @param view View to search from
     *
     * @return The row view.
     */
    @NonNull
    public Integer getViewRow(@NonNull View view) {
        while (view.getId() != R.id.SLA_ROW) {
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) {
                throw new RuntimeException("Could not find row view in view ancestors");
            }
            view = (View) parent;
        }
        Object o = ViewTagger.getTag(view, R.id.SLA_ROW_POSITION_TAG);
        Objects.requireNonNull(o, "A view with the tag R.id.SLA_ROW was found, but it is not the view for the row");
        return (Integer) o;
    }

    public void onRowDown(@NonNull final View target, @NonNull final T item, final int position) {
        // do nothing
    }

    public void onRowUp(@NonNull final View target, @NonNull final T item, final int position) {
        // do nothing
    }

    /**
     * @return <tt>true</tt>if delete is allowed to happen
     */
    public boolean onRowDelete(@NonNull final View target, @NonNull final T item, final int position) {
        return true;
    }

    /**
     * Called when an otherwise inactive part of the row is clicked.
     *
     * @param target The view clicked
     * @param item   The object associated with this row
     */
    public void onRowClick(@NonNull final View target, @NonNull final T item, final int position) {
    }

    /**
     * Called when an otherwise inactive part of the row is long clicked.
     *
     * @param target The view clicked
     * @param item   The object associated with this row
     *
     * @return <tt>true</tt>if handled
     */
    public boolean onRowLongClick(@NonNull final View target, @NonNull final T item, final int position) {
        return false;
    }

    public void onListChanged() {
    }

    /**
     * Interface to allow underlying objects to determine their view ID.
     */
    public interface ViewProvider {
        @SuppressWarnings("SameReturnValue")
        int getViewId();
    }
}