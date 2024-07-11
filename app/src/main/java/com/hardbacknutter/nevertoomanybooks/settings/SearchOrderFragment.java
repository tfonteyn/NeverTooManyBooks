/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditSearchsiteBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;

/**
 * Handles the order of sites to search, and the individual site being enabled or not.
 * <p>
 * Persistence is handled in {@link SearchAdminFragment} / {@link SearchAdminViewModel}.
 */
public class SearchOrderFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "SearchOrderFragment";
    private static final String BKEY_TYPE = TAG + ":type";

    private SearchSiteListAdapter listAdapter;
    private ItemTouchHelper itemTouchHelper;

    /* The View model. */
    private SearchAdminViewModel vm;

    /** View Binding. */
    private FragmentEditSearchOrderBinding vb;

    /** The type of list we're handling in this fragment (tab). */
    private Site.Type type;

    /**
     * Constructor.
     *
     * @param type of the list to edit
     *
     * @return instance
     */
    @NonNull
    public static SearchOrderFragment create(@NonNull final Site.Type type) {
        final SearchOrderFragment fragment = new SearchOrderFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        type = Objects.requireNonNull(requireArguments().getParcelable(BKEY_TYPE), BKEY_TYPE);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SearchAdminViewModel.class);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditSearchOrderBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        vb.siteList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.siteList.setHasFixedSize(true);

        listAdapter = new SearchSiteListAdapter(getContext(), vm.getList(type),
                                                vh -> itemTouchHelper.startDrag(vh));
        vb.siteList.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.siteList);

        //noinspection DataFlowIssue
        vm.onSiteListUpdated().observe(getActivity(), updatedType -> {
            // is it ours?
            if (updatedType == type) {
                listAdapter.notifyDataSetChanged();
            }
        });

        if (savedInstanceState == null) {
            TipManager.getInstance().display(getContext(), R.string.tip_configure_sites, null);
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder {

        @NonNull
        private final RowEditSearchsiteBinding vb;

        Holder(@NonNull final RowEditSearchsiteBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@NonNull final Site site) {
            final Context context = itemView.getContext();
            vb.websiteName.setText(site.getEngineId().getName(context));

            setChecked(site.isActive());

            // only show the info for Data lists. Irrelevant for others.
            if (site.getType() == Site.Type.Data) {
                final EngineId engineId = site.getEngineId();
                // do not list SearchEngine.CoverByEdition, it's irrelevant to the user.
                final Collection<String> capabilities = new ArrayList<>();
                if (engineId.supports(SearchEngine.SearchBy.Isbn)) {
                    capabilities.add(context.getString(R.string.lbl_isbn));
                }
                if (engineId.supports(SearchEngine.SearchBy.Barcode)) {
                    capabilities.add(context.getString(R.string.lbl_barcode));
                }
                if (engineId.supports(SearchEngine.SearchBy.ExternalId)) {
                    capabilities.add(context.getString(R.string.lbl_tab_lbl_ext_id));
                }
                if (engineId.supports(SearchEngine.SearchBy.Text)) {
                    capabilities.add(context.getString(android.R.string.search_go));
                }
                vb.capabilities.setText(context.getString(R.string.brackets,
                                                          String.join(", ", capabilities)));
                vb.capabilities.setVisibility(View.VISIBLE);

                vb.info.setText(site.getEngineId().getInfoResId());
                vb.info.setVisibility(View.VISIBLE);
            } else {
                vb.capabilities.setVisibility(View.GONE);
                vb.info.setVisibility(View.GONE);
            }
        }
    }

    private static class SearchSiteListAdapter
            extends BaseDragDropRecyclerViewAdapter<Site, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param sites             to use
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final Context context,
                              @NonNull final List<Site> sites,
                              @NonNull final StartDragListener dragStartListener) {
            super(context, sites, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditSearchsiteBinding vb =
                    RowEditSearchsiteBinding.inflate(getLayoutInflater(), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            holder.setOnItemCheckChangedListener(position -> {
                final Site site = getItem(position);
                site.setActive(!site.isActive());
                notifyItemChanged(position);
                return site.isActive();
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }
}
