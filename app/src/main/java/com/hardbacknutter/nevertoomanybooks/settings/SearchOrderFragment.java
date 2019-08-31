/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

public class SearchOrderFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "SearchOrderFragment";

    private ArrayList<Site> mList;
    private SearchSiteListAdapter mListAdapter;
    private RecyclerView mListView;
    private ItemTouchHelper mItemTouchHelper;

    private SearchAdminActivity mActivity;
    private final SimpleAdapterDataObserver mAdapterDataObserver = new SimpleAdapterDataObserver() {
        @Override
        public void onChanged() {
            mActivity.setDirty(true);
        }
    };

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mActivity = (SearchAdminActivity) context;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_search_order, container, false);
        mListView = view.findViewById(android.R.id.list);
        return view;
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mList = requireArguments().getParcelableArrayList(SearchSites.BKEY_SEARCH_SITES);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        mListAdapter = new SearchSiteListAdapter(getContext(), mList,
                                                 vh -> mItemTouchHelper.startDrag(vh));
        // any change done in the adapter will set the data 'dirty'
        // if changing the list externally, make sure to always notify the adapter.
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);
    }

    /**
     * Get the list if this fragment has been displayed.
     *
     * @return the list or or {@code null} if not loaded
     */
    @Nullable
    public ArrayList<Site> getList() {
        // have we been brought to the front ?
        if (mListView != null) {
            // walk the list, and use the position of the item as the site.priority
            for (int row = 0; row < mList.size(); row++) {
                mList.get(row).setPriority(row);
            }
        }
        return mList;
    }

    /**
     * Replace the current list.
     *
     * @param list new list to display.
     */
    public void setList(@NonNull final ArrayList<Site> list) {
        mList.clear();
        mList.addAll(list);
        mListAdapter.notifyDataSetChanged();
    }

    private static class SearchSiteListAdapter
            extends RecyclerViewAdapterBase<Site, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of sites
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final List<Site> items,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = getLayoutInflater()
                                .inflate(R.layout.row_edit_searchsite, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            Site site = getItem(position);

            holder.nameView.setText(site.getName());
            //noinspection ConstantConditions
            holder.mCheckableButton.setChecked(site.isEnabled());

            // Set the click listener for the 'enable' site checkable
            holder.mCheckableButton.setOnClickListener(v -> {
                site.setEnabled(!site.isEnabled());
                holder.mCheckableButton.setChecked(site.isEnabled());
            });
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView nameView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
        }
    }
}
