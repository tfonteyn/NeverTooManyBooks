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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Handles the order of sites to search, and the individual site being enabled or not.
 * <p>
 * Persistence is handled in {@link SearchAdminModel}.
 */
public class SearchOrderFragment
        extends Fragment {

    @SuppressWarnings("FieldCanBeLocal")
    private SearchSiteListAdapter mListAdapter;
    private RecyclerView mListView;
    private ItemTouchHelper mItemTouchHelper;

    /** The list we're handling in this fragment (tab). */
    @Nullable
    private SearchSites.ListType mOurListType;

    private SearchAdminModel mModel;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Mandatory
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_search_order, container, false);
        mListView = view.findViewById(R.id.siteList);
        return view;
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mOurListType = requireArguments().getParcelable(SearchAdminModel.BKEY_LIST_TYPE);
        Objects.requireNonNull(mOurListType);

        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(SearchAdminModel.class);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        mListAdapter = new SearchSiteListAdapter(getContext(),
                                                 mModel.getList(getContext(), mOurListType),
                                                 vh -> mItemTouchHelper.startDrag(vh));
        mListView.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mListView);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.btn_reset)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_RESET:
                //noinspection ConstantConditions
                mModel.resetList(getContext(), mOurListType);
                mListAdapter.notifyDataSetChanged();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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
