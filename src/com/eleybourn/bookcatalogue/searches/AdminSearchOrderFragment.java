package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
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
public class AdminSearchOrderFragment extends Fragment {

    public static final String TAG = "AdminSearchOrderFragment";

    private ListView mListView;
    private ArrayList<SearchSites.Site> mList;
    private SearchSiteListAdapter mAdapter;

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

        mAdapter = new SearchSiteListAdapter(requireContext(), R.layout.row_edit_searchsite, mList);
        //noinspection ConstantConditions
        mListView = getView().findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);

        // Do not add handler for 'onDrop' from the TouchListView; we'll get what we need when we're ready to save.
        //((TouchListView) mListView).setOnDropListener(this);

        Tracker.exitOnActivityCreated(this);
    }

    @Nullable
    public ArrayList<SearchSites.Site> getList() {
        if (mListView != null) {
            // walk the list, and use the position of the item as the site.priority
            ArrayList<SearchSites.Site> newList = new ArrayList<>(mList);
            for (int row = 0; row < mListView.getChildCount(); row++) {
                // get the current position of each site, and store that back into the site object.
                View child = mListView.getChildAt(row);
                int pos = mAdapter.getViewRow(child);
                SearchSites.Site site = mAdapter.getItem(pos);
                //noinspection ConstantConditions
                site.priority = row;
                newList.set(site.priority, site);
            }
            mList = newList;
        }
        return mList;
    }

    private class SearchSiteListAdapter extends SimpleListAdapter<SearchSites.Site> {

        SearchSiteListAdapter(final @NonNull Context context,
                              final int rowViewId,
                              final @NonNull List<SearchSites.Site> list) {
            super(context, rowViewId, list);
        }

        @Override
        public void onGetView(final @NonNull View target, final @NonNull SearchSites.Site site) {
            Holder holder = ViewTagger.getTag(target, R.id.TAG_HOLDER);
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.name = target.findViewById(R.id.name);
                holder.checkable = target.findViewById(R.id.row_enabled);
                // Tag the parts that need it
                ViewTagger.setTag(target, R.id.TAG_HOLDER, holder);
                ViewTagger.setTag(holder.checkable, R.id.TAG_HOLDER, holder);

                // Handle a click on the CheckedTextView
                holder.checkable.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(@NonNull View v) {
                        Holder h = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);
                        boolean newStatus = !h.site.enabled;
                        h.site.enabled = newStatus;
                        h.checkable.setChecked(newStatus);
                    }
                });
            }

            // Setup the variant fields in the holder
            holder.site = site;
            holder.name.setText(site.name);
            holder.checkable.setChecked(site.enabled);
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
