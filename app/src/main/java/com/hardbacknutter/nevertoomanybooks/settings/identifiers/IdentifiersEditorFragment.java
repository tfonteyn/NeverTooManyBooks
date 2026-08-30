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

package com.hardbacknutter.nevertoomanybooks.settings.identifiers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsOutput;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditIdentifiersBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditIdentifierBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.identifier.EditIdentifierBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.identifier.EditIdentifierDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.HtmlFormatter;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.OnRowClickListener;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RowViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

import org.acra.ACRA;

@Keep
public class IdentifiersEditorFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "IdentifiersEditorFrag";
    static final String BKEY_ENTITY_TYPE = TAG + ":et";

    private static final String RK_MENU = TAG + ":rk:menu";
    private static final int POS_NEW_ENTRY = -1;

    private IdentifiersEditorViewModel vm;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    new SettingsOutput(false, vm.isModified())
                            .finishActivityAndSend(getActivity());
                }
            };

    /** View Binding. */
    private FragmentEditIdentifiersBinding vb;
    private IdentifierAdapter adapter;
    private ExtMenuLauncher menuLauncher;
    private EditInPlaceParcelableLauncher<Identifier> editLauncher;

    /**
     * Constructor.
     *
     * @param entityType of the list to edit
     *
     * @return instance
     */
    @NonNull
    public static IdentifiersEditorFragment create(@NonNull final
                                                   Identifier.EntityType entityType) {
        final IdentifiersEditorFragment fragment = new IdentifiersEditorFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(BKEY_ENTITY_TYPE, entityType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(IdentifiersEditorViewModel.class);
        vm.init(requireArguments());

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = new EditInPlaceParcelableLauncher<>(
                DBKey.FK_IDENTIFIER,
                EditIdentifierDialogFragment::new,
                EditIdentifierBottomSheet::new);
        editLauncher.setListener(this::onEditEntryDone);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditIdentifiersBinding.inflate(getLayoutInflater(), container, false);
        return vb.getRoot();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.list);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_identifiers);

        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        final String info = getString(R.string.info_identifier_editor,
                                      getString(R.string.github_help_url));
        vb.info.setText(HtmlFormatter.linkify(info));
        vb.info.setMovementMethod(LinkMovementMethod.getInstance());

        //noinspection DataFlowIssue
        vb.list.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        vb.list.setHasFixedSize(true);

        adapter = new IdentifierAdapter(vm.getIdentifiers());

        // reminder: do not allow single-click.... we need to be able to tap the site-url
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(),
                (v, position) -> {
                    if (position == RecyclerView.NO_POSITION) {
                        return;
                    }
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    menuLauncher.launch(v, null, null, position, menu);
                });

        vb.list.setAdapter(adapter);

        // refresh/restore
        vm.onUpdate().observe(getViewLifecycleOwner(), aVoid -> adapter.notifyDataSetChanged());
    }

    /**
     * Menu selection listener.
     *
     * @param position   in the list
     * @param menuItemId The menu item that was invoked.
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(final int position,
                                       @IdRes final int menuItemId) {

        if (menuItemId == R.id.MENU_EDIT) {
            editEntry(position);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;
        }
        return false;
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param position the position of the item; use {@link #POS_NEW_ENTRY} for a new entry.
     */
    private void editEntry(final int position) {
        final Identifier identifier;
        if (position == POS_NEW_ENTRY) {
            identifier = new Identifier("", vm.getEntityType());
        } else {
            identifier = vm.getIdentifiers().get(position);
        }
        //noinspection DataFlowIssue
        editLauncher.edit(getContext(), identifier);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onEditEntryDone(@NonNull final Identifier identifier) {
        // we're lazy...
        vm.refreshList();
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     *
     * @throws IllegalArgumentException (debug)
     */
    private void deleteEntry(final int position) {
        final Identifier identifier = vm.getIdentifiers().get(position);

        final Context context = getContext();
        @SuppressWarnings("DataFlowIssue")
        final Resources res = context.getResources();

        final int count = vm.count(identifier);
        final String msg;
        switch (identifier.getEntityType()) {
            case Book: {
                msg = context.getString(
                        R.string.confirm_delete_identifier_from_x_books,
                        identifier.getName(),
                        res.getQuantityString(R.plurals.n_books_from, count, count));
                break;
            }
            case Author: {
                msg = context.getString(
                        R.string.confirm_delete_identifier_from_x_authors,
                        identifier.getName(),
                        res.getQuantityString(R.plurals.n_authors_from, count, count));
                break;
            }
            case Series: {
                msg = context.getString(
                        R.string.confirm_delete_identifier_from_x_series,
                        identifier.getName(),
                        res.getQuantityString(R.plurals.n_series_from, count, count));
                break;
            }
            default:
                throw new IllegalArgumentException(identifier.getEntityType().toString());
        }

        StandardDialogs.delete(context, () -> {
            vm.delete(identifier);
            adapter.notifyItemRemoved(position);
        }, msg);
    }

    private static class Holder
            extends RowViewHolder {

        @NonNull
        private final RowEditIdentifierBinding vb;
        @NonNull
        private final HtmlFormatter<String> htmlFormatter;

        Holder(@NonNull final RowEditIdentifierBinding vb,
               @NonNull final HtmlFormatter<String> htmlFormatter) {
            super(vb.getRoot());
            this.vb = vb;
            this.htmlFormatter = htmlFormatter;
        }

        void onBind(@NonNull final Identifier identifier) {
            vb.key.setText(identifier.getKey());
            vb.name.setText(identifier.getName());

            final String siteUrl = identifier.getSiteUrl();
            if (siteUrl != null && !siteUrl.isEmpty()) {
                htmlFormatter.apply(siteUrl, vb.siteUrl);
                vb.siteUrl.setVisibility(View.VISIBLE);
            } else {
                vb.siteUrl.setVisibility(View.GONE);
            }
        }
    }

    private static class IdentifierAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final List<Identifier> identifiers;
        private final HtmlFormatter<String> htmlFormatter;
        @Nullable
        private OnRowClickListener rowClickListener;
        @Nullable
        private OnRowClickListener rowShowMenuListener;
        @Nullable
        private ExtMenuButton contextMenuMode;

        IdentifierAdapter(@NonNull final List<Identifier> identifiers) {
            this.identifiers = identifiers;

            htmlFormatter = new HtmlFormatter<String>()
                    .setEnableLinks(true);
        }

        /**
         * Set the {@link OnRowClickListener} for a click on a row.
         * This listener will be propagated to all row ViewHolders.
         *
         * @param listener to set
         */
        void setOnRowClickListener(@Nullable final OnRowClickListener listener) {
            this.rowClickListener = listener;
        }

        /**
         * Set the {@link OnRowClickListener} for showing the context menu on a row.
         * This listener will be propagated to all row ViewHolders.
         *
         * @param contextMenuMode how to show context menus
         * @param listener        to receive clicks
         */
        void setOnRowShowMenuListener(@NonNull final ExtMenuButton contextMenuMode,
                                      @Nullable final OnRowClickListener listener) {
            this.rowShowMenuListener = listener;
            this.contextMenuMode = contextMenuMode;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final RowEditIdentifierBinding vb = RowEditIdentifierBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            final Holder holder = new Holder(vb, htmlFormatter);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            holder.onBind(identifiers.get(position));
        }

        @Override
        public int getItemCount() {
            return identifiers.size();
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {
        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {

            menu.add(Menu.NONE, R.id.MENU_ACTION_ADD, 0, R.string.action_add)
                .setIcon(R.drawable.add_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.action_restore_default_identifiers);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_ACTION_ADD) {
                editEntry(POS_NEW_ENTRY);
                return true;

            } else if (itemId == R.id.MENU_RESET) {
                // we should move this to IdentifiersAdminFragment
                // but oh well...
                // we should also warn the user that this is ALL entity-types
                // but oh well...
                // In reality, there will be a miniscule number of users using this editor.
                try {
                    //noinspection DataFlowIssue
                    vm.restoreBuiltin(getContext());
                } catch (@NonNull final DaoWriteException e) {
                    ACRA.getErrorReporter().handleException(e, false);
                }
                return true;
            }
            return false;
        }
    }
}
