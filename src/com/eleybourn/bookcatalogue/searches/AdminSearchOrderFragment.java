package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

/**
 * Ideally should use {@link EditObjectListActivity} but that needs to be converted
 * to a Fragment first.
 */
public class AdminSearchOrderFragment
        extends Fragment
        implements TouchListView.OnDropListener {

    /** Fragment manager tag. */
    public static final String TAG = AdminSearchOrderFragment.class.getSimpleName();

    private ListView mListView;
    private ArrayList<SearchSites.Site> mList;
    private SearchSiteListAdapter mListAdapter;
    private boolean mIsDirty;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_order, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = requireArguments();
        mList = args.getParcelableArrayList(SearchSites.BKEY_SEARCH_SITES);

        mListAdapter = new SearchSiteListAdapter(requireContext(), mList);
        mListView = requireView().findViewById(android.R.id.list);
        mListView.setAdapter(mListAdapter);

        // Add handler for 'onDrop' from the TouchListView
        ((TouchListView) mListView).setOnDropListener(this);
    }

    /**
     * Handle drop events; This is a simplified version of {@link EditObjectListActivity#onDrop}.
     * <p>
     * Lists here are only 5 items or less....
     */
    @Override
    @CallSuper
    public void onDrop(final int fromPosition,
                       final int toPosition) {
        // Check if nothing to do; also avoids the nasty case where list size == 1
        if (fromPosition == toPosition) {
            return;
        }

        // update the list
        SearchSites.Site item = mListAdapter.getItem(fromPosition);
        mListAdapter.remove(item);
        mListAdapter.insert(item, toPosition);
        mListAdapter.notifyDataSetChanged();

        mIsDirty = true;
    }

    @Nullable
    public ArrayList<SearchSites.Site> getList() {
        // have we been brought to the front ?
        if (mListView != null) {
            // walk the list, and use the position of the item as the site.priority
            for (int row = 0; row < mList.size(); row++) {
                mList.get(row).setPriority(row);
            }
        }
        return mList;
    }

    public boolean isDirty() {
        // have we been brought to the front and are we dirty ?
        return mListView != null && mIsDirty;
    }

    private class SearchSiteListAdapter
            extends ArrayAdapter<SearchSites.Site> {

        /**
         * Constructor.
         *
         * @param context the caller context
         * @param list    of sites
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final List<SearchSites.Site> list) {
            super(context, 0, list);
        }

        @NonNull
        @Override
        public View getView(final int position,
                            @Nullable View convertView,
                            @NonNull final ViewGroup parent) {
            Holder holder;
            if (convertView != null) {
                // Recycling: just get the holder
                holder = (Holder) convertView.getTag();
            } else {
                // Not recycling, get a new View and make the holder for it.
                convertView = LayoutInflater.from(getContext())
                                            .inflate(R.layout.row_edit_searchsite, parent, false);

                holder = new Holder(convertView);
                holder.checkableView.setTag(holder);
            }

            // Setup the variant fields in the holder
            holder.site = getItem(position);
            //noinspection ConstantConditions
            holder.nameView.setText(holder.site.getName());
            holder.checkableView.setChecked(holder.site.isEnabled());

            return convertView;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder {

        @NonNull
        final CheckedTextView checkableView;
        @NonNull
        final View rowDetailsView;
        @NonNull
        final TextView nameView;
        SearchSites.Site site;

        public Holder(@NonNull final View rowView) {
            rowDetailsView = rowView.findViewById(R.id.TLV_ROW_DETAILS);
            nameView = rowView.findViewById(R.id.name);
            checkableView = rowView.findViewById(R.id.TLV_ROW_CHECKABLE);
            // Set the click listener for the 'enable' site checkable
            checkableView.setOnClickListener(v -> {
                Holder h = (Holder) v.getTag();
                h.site.setEnabled(!h.site.isEnabled());
                h.checkableView.setChecked(h.site.isEnabled());
                // no need to update the list, item itself is updated
                //onListChanged();
                mIsDirty = true;
            });

            rowView.setTag(this);
        }
    }
}
