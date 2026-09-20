/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.searchsites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.tabs.TabLayout;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.ViewUtil;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditSearchOrderBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditSearchsiteBinding;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.RegistrationOutput;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsInput;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;

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

    private static final String RK_MENU = TAG + ":rk:menu";
    /** The {@link #type} MUST be appended. */
    private String rkRegistration = TAG + ":rk:";

    private SearchSiteListAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    /* The View model. */
    private SearchAdminViewModel vm;

    /** View Binding. */
    private FragmentEditSearchOrderBinding vb;
    @Nullable
    private TabLayout tabPanel;

    private ExtMenuLauncher menuLauncher;

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

        //noinspection deprecation
        type = Objects.requireNonNull(requireArguments().getParcelable(BKEY_TYPE), BKEY_TYPE);

        rkRegistration += type.name();

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SearchAdminViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
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
        // Insets are applied to the parent fragment (ViewPager2)

        // Listen for registration results
        getParentFragmentManager().setFragmentResultListener(rkRegistration,
                                                             getViewLifecycleOwner(),
                                                             this::onRegistrationDone);

        //noinspection DataFlowIssue
        tabPanel = getActivity().findViewById(R.id.tab_panel);

        //noinspection DataFlowIssue
        vb.siteList.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.siteList.setHasFixedSize(true);

        adapter = new SearchSiteListAdapter(vm.getList(type),
                                            this::onSiteActivated,
                                            vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(),
                (v, position) -> {
                    if (position == RecyclerView.NO_POSITION) {
                        return;
                    }
                    final Menu menu = MenuUtils.create(v.getContext());
                    menu.add(Menu.NONE, R.id.MENU_SETTINGS, 0, R.string.lbl_settings)
                        .setIcon(R.drawable.settings_24px);

                    menuLauncher.launch(v, null, null, position, menu);
                });

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);

        vb.siteList.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.siteList);

        vm.onSiteListUpdated().observe(getActivity(), updatedType -> {
            // is it ours?
            if (updatedType == type) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private boolean onSiteActivated(@NonNull final Site site,
                                    final boolean active) {
        final EngineId engineId = site.getEngineId();
        if (active && engineId.supports(SearchEngine.UserRegistration.class)) {

            final Context context = getContext();
            //noinspection DataFlowIssue
            final SearchEngine.UserRegistration searchEngine =
                    engineId.createSearchEngine(context);

            if (searchEngine.getRegistrationKey().isEmpty()) {
                @StringRes
                final int msgId;
                @DrawableRes
                final int iconId;

                final boolean required = searchEngine.isRegistrationRequired();
                if (required) {
                    msgId = R.string.confirm_registration_required;
                    iconId = R.drawable.warning_24px;
                } else {
                    msgId = R.string.confirm_registration_optional;
                    iconId = R.drawable.info_24px;
                }

                final String siteName = engineId.getName(context);
                final String message = getString(msgId, siteName,
                                                 getString(R.string.lbl_credentials));
                new MaterialAlertDialogBuilder(context)
                        .setIcon(iconId)
                        .setTitle(siteName)
                        .setMessage(message)
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.action_more_ellipsis, (d, w)
                                -> startRegistration(searchEngine))
                        .create()
                        .show();
                return false;
            }
        }
        return true;
    }

    private void startRegistration(@NonNull final SearchEngine.UserRegistration searchEngine) {
        if (tabPanel != null) {
            ViewUtil.setViewAndChildrenEnabled(tabPanel, false);
        }
        @SuppressWarnings("DataFlowIssue")
        final Fragment fragment = searchEngine
                .createRegistrationFragment(getContext(), rkRegistration);
        final String fragmentTag = searchEngine.getEngineId().name();
        getParentFragmentManager().beginTransaction()
                                  .setReorderingAllowed(true)
                                  .addToBackStack(fragmentTag)
                                  .replace(R.id.content_frame, fragment, fragmentTag)
                                  .commit();
    }

    private void onRegistrationDone(@NonNull final String requestKey,
                                    @NonNull final Bundle args) {

        if (tabPanel != null) {
            ViewUtil.setViewAndChildrenEnabled(tabPanel, true);
        }

        final RegistrationOutput registration = RegistrationOutput.fromBundle(args);
        final EngineId engineId = registration.getEngineId();

        //noinspection DataFlowIssue
        final SearchEngine.UserRegistration searchEngine =
                engineId.createSearchEngine(getContext());

        // Activate the site if registration was done/updated,
        // OR if it was optional, and the user did not care about any limitations.
        if (searchEngine.getRegistrationKey().isPresent()
            || !searchEngine.isRegistrationRequired()) {

            int index = 0;
            for (final Site site : vm.getList(type)) {
                if (site.getEngineId() == engineId) {
                    site.setActive(true);
                    adapter.notifyItemChanged(index);
                    break;
                }
                index++;
            }
        }
    }

    /**
     * Called for toolbar and list adapter context menu.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     *
     * @throws IllegalStateException (debug) if the PreferenceFragment fails to instantiate
     */
    @SuppressLint("Range")
    private boolean onMenuItemSelected(@IntRange(from = RecyclerView.NO_POSITION) final int position,
                                       @IdRes final int menuItemId) {

        // should never be the case.... flw
        if (position == RecyclerView.NO_POSITION) {
            return false;
        }

        if (menuItemId == R.id.MENU_SETTINGS) {
            final Site site = vm.getList(type).get(position);
            return openSiteSettings(site, null);
        }

        return false;
    }

    private boolean openSiteSettings(@NonNull final Site site,
                                     @Nullable final String autoScrollToKey) {

        final EngineId engineId = site.getEngineId();
        final Class<? extends Fragment> pfc = engineId.getPreferenceFragmentClass();
        // sanity check
        if (pfc == null) {
            return false;
        }

        if (tabPanel != null) {
            tabPanel.setVisibility(View.GONE);
        }

        final Fragment fragment;
        try {
            fragment = pfc.getConstructor().newInstance();
        } catch (@NonNull final IllegalAccessException
                                | NoSuchMethodException
                                | InstantiationException
                                | InvocationTargetException
                                | java.lang.InstantiationException e) {
            throw new IllegalStateException(e);
        }

        if (autoScrollToKey != null) {
            final SettingsInput args = new SettingsInput()
                    .setAutoScrollKey(autoScrollToKey, true);
            fragment.setArguments(args.toBundle());
        }

        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(engineId.name())
                .replace(R.id.content_frame, fragment, engineId.name())
                .commit();
        return true;
    }

    @FunctionalInterface
    private interface SiteActivatedCallback {
        boolean onSiteActivated(@NonNull Site site,
                                boolean active);
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
                if (engineId.supports(SearchEngine.SearchBy.Issn)) {
                    capabilities.add(context.getString(R.string.lbl_issn));
                }
                if (engineId.supports(SearchEngine.SearchBy.Barcode)) {
                    capabilities.add(context.getString(R.string.lbl_barcode));
                }
                if (engineId.supports(SearchEngine.SearchBy.ExternalId)) {
                    capabilities.add(context.getString(R.string.lbl_tab_lbl_ext_id));
                }
                if (engineId.supports(SearchEngine.SearchBy.Text)) {
                    capabilities.add(context.getString(R.string.action_search));
                }
                vb.capabilities.setText(context.getString(R.string.brackets,
                                                          String.join(", ", capabilities)));
                vb.capabilities.setVisibility(View.VISIBLE);

                vb.info.setText(site.getEngineId().getInfo(context));
                vb.info.setVisibility(View.VISIBLE);
            } else {
                vb.capabilities.setVisibility(View.GONE);
                vb.info.setVisibility(View.GONE);
            }
        }
    }

    private static class SearchSiteListAdapter
            extends BaseDragDropRecyclerViewAdapter<Site, Holder> {

        @NonNull
        private final SiteActivatedCallback siteActivatedCallback;

        /**
         * Constructor.
         *
         * @param sites                 to use
         * @param siteActivatedCallback callback when the enable/disable checkbox is tapped
         * @param dragStartListener     Listener to handle the user moving rows up and down
         */
        SearchSiteListAdapter(@NonNull final List<Site> sites,
                              @NonNull final SiteActivatedCallback siteActivatedCallback,
                              @NonNull final StartDragListener dragStartListener) {
            super(sites, dragStartListener);
            this.siteActivatedCallback = siteActivatedCallback;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditSearchsiteBinding vb = RowEditSearchsiteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            final Holder holder = new Holder(vb);

            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);

            holder.setOnItemCheckChangedListener(position -> {
                final Site site = getItem(position);
                final boolean newState = !site.isActive();

                if (siteActivatedCallback.onSiteActivated(site, newState)) {
                    site.setActive(newState);
                    notifyItemChanged(position);
                }
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
