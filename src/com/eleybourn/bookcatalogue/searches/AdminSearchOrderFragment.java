package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ideally should use {@link EditObjectListActivity} but that needs to be converted to a Fragment first.
 */
public class AdminSearchOrderFragment extends Fragment
        implements TouchListView.OnDropListener {

    public static final String TAG = "AdminSearchOrderFragment";

    private ListView mListView;
    private ArrayList<SearchSites.Site> mList;
    private SearchSiteListAdapter mListAdapter;

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_order, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        Objects.requireNonNull(args);
        mList = args.getParcelableArrayList(SearchSites.BKEY_SEARCH_SITES);

        mListAdapter = new SearchSiteListAdapter(requireContext(), mList);
        //noinspection ConstantConditions
        mListView = getView().findViewById(android.R.id.list);
        mListView.setAdapter(mListAdapter);

        // Do not add handler for 'onDrop' from the TouchListView; we'll get what we need when we're ready to save.
        ((TouchListView) mListView).setOnDropListener(this);

        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Handle drop events; This is a simplified version of {@link EditObjectListActivity#onDrop}
     *
     * Lists here are 5 items or so....
     */
    @Override
    @CallSuper
    public void onDrop(final int fromPosition, final int toPosition) {
        // Check if nothing to do; also avoids the nasty case where list size == 1
        if (fromPosition == toPosition) {
            return;
        }

        // update the list
        SearchSites.Site item = mListAdapter.getItem(fromPosition);
        mListAdapter.remove(item);
        mListAdapter.insert(item, toPosition);
        mListAdapter.notifyDataSetChanged();
    }

    @Nullable
    public ArrayList<SearchSites.Site> getList() {
        // have we been brought to the front ?
        if (mListView != null) {
            // walk the list, and use the position of the item as the site.priority
            for (int row = 0; row < mList.size(); row++) {
                mList.get(row).priority = row;
            }
        }
        return mList;
    }

    private class SearchSiteListAdapter extends ArrayAdapter<SearchSites.Site> {

        SearchSiteListAdapter(final @NonNull Context context,
                              final @NonNull List<SearchSites.Site> list) {
            super(context, 0, list);
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull final ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                // Not recycling, get a new View and make the holder for it.
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.row_edit_searchsite, parent, false);

                holder = new Holder();
                holder.name = convertView.findViewById(R.id.name);
                holder.checkable = convertView.findViewById(R.id.row_check);
                // Tag the parts that need it
                ViewTagger.setTag(convertView, holder);
                ViewTagger.setTag(holder.checkable, holder);

                // Set the click listener for the 'enable' site checkable
                holder.checkable.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(@NonNull View v) {
                        Holder h = ViewTagger.getTagOrThrow(v);
                        h.site.enabled = !h.site.enabled;
                        h.checkable.setChecked(h.site.enabled);
                        // no need to update the list, item itself is updated
                        //onListChanged();
                    }
                });
            } else {
                // Recycling: just get the holder
                holder = ViewTagger.getTagOrThrow(convertView);
            }

            // Setup the variant fields in the holder
            holder.site = getItem(position);
            //noinspection ConstantConditions
            holder.name.setText(holder.site.name);
            holder.checkable.setChecked(holder.site.enabled);

            return convertView;
        }
    }

    /**
     * Holder pattern for each row.
     */
    private class Holder {
        SearchSites.Site site;
        CheckedTextView checkable;
        TextView name;
    }
}
