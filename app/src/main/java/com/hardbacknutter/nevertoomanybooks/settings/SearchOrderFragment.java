/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
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
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

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
     * The list we're handling in this fragment (tab).
     * Single-list mode: the list as passed in.
     * All-list mode: a local <strong>deep-copy</strong> of the {@link #type} list.
     */
    private ArrayList<Site> siteList;

    @NonNull
    public static Fragment create(@NonNull final Site.Type type) {
        final Fragment fragment = new SearchOrderFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
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

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);
        toolbar.setTitle(R.string.lbl_settings);
        toolbar.setSubtitle(R.string.lbl_websites);

        type = Objects.requireNonNull(requireArguments().getParcelable(BKEY_TYPE), BKEY_TYPE);
        siteList = vm.getList(type);

        //noinspection ConstantConditions
        vb.siteList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.siteList.setHasFixedSize(true);

        listAdapter = new SearchSiteListAdapter(getContext(), siteList,
                                                vh -> itemTouchHelper.startDrag(vh));
        vb.siteList.setAdapter(listAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(listAdapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.siteList);
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView infoView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.website_name);
            infoView = itemView.findViewById(R.id.website_search_capabilities);
        }
    }

    private static class SearchSiteListAdapter
            extends RecyclerViewAdapterBase<Site, Holder> {

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
            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_searchsite, parent, false);
            final Holder holder = new Holder(view);
            //noinspection ConstantConditions
            holder.checkableButton.setOnClickListener(v -> onItemCheckChanged(holder));
            return holder;
        }

        void onItemCheckChanged(@NonNull final Holder holder) {
            final Site site = getItem(holder.getBindingAdapterPosition());
            site.setActive(!site.isActive());
            //noinspection ConstantConditions
            holder.checkableButton.setChecked(site.isActive());
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            @NonNull
            final Context context = getContext();

            final Site site = getItem(position);

            holder.nameView.setText(site.getEngineId().getName(context));

            //noinspection ConstantConditions
            holder.checkableButton.setChecked(site.isActive());

            // only show the info for Data lists. Irrelevant for others.
            if (site.getType() == Site.Type.Data) {
                final SearchEngine searchEngine = site.getSearchEngine();
                // do not list SearchEngine.CoverByIsbn, it's irrelevant to the user.
                final Collection<String> info = new ArrayList<>();
                if (searchEngine instanceof SearchEngine.ByIsbn) {
                    info.add(context.getString(R.string.lbl_isbn));
                }
                if (searchEngine instanceof SearchEngine.ByBarcode) {
                    info.add(context.getString(R.string.lbl_barcode));
                }
                if (searchEngine instanceof SearchEngine.ByExternalId) {
                    info.add(context.getString(R.string.lbl_tab_lbl_ext_id));
                }
                if (searchEngine instanceof SearchEngine.ByText) {
                    info.add(context.getString(android.R.string.search_go));
                }
                holder.infoView.setText(context.getString(R.string.brackets,
                                                          String.join(", ", info)));

                holder.infoView.setVisibility(View.VISIBLE);
            } else {
                holder.infoView.setVisibility(View.GONE);
            }
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.action_reset_to_default)
                .setIcon(R.drawable.ic_baseline_undo_24);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_RESET) {
                // Reset the global/original list for the type.
                //noinspection ConstantConditions
                type.resetList(getContext());
                // and replace the content of the local list with the (new) defaults.
                siteList.clear();
                siteList.addAll(type.getSites());
                listAdapter.notifyDataSetChanged();
                return true;
            }
            return false;
        }
    }
}
