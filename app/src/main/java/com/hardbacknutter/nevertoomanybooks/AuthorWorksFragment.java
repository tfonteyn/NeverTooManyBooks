/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.DisplayBookLauncher;
import com.hardbacknutter.nevertoomanybooks.bookdetails.AuthorWorksAdapter;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.covers.ImageHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAuthorWorksBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.Tip;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.author.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.DateFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.localsearch.SearchFtsFragment;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.settings.FastScrollerMode;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * Display all {@link TocEntry}'s for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 * <p>
 * <strong>Note:</strong> when an item is clicked, we start a <strong>NEW</strong> Activity.
 * Doing a 'back' will then get the user back here.
 * This is intentionally different from the behaviour of {@link SearchFtsFragment}.
 */
public class AuthorWorksFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "AuthorWorksFragment";
    /** Optional. Show the TOC. Defaults to {@code true}. */
    static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    static final String BKEY_WITH_BOOKS = TAG + ":books";
    private static final String RK_MENU = TAG + ":rk:menu";
    /** The Fragment ViewModel. */
    private AuthorWorksViewModel vm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, vm.createResultIntent());
                    getActivity().finish();
                }
            };
    /** Display a Book. */
    private DisplayBookLauncher displayBookLauncher;
    private ExtMenuLauncher menuLauncher;
    /** The Adapter. */
    private AuthorWorksAdapter adapter;
    /** View Binding. */
    private FragmentAuthorWorksBinding vb;
    @Nullable
    private ProgressDelegate progressDelegate;

    private Menu rowMenu;
    private TextView nameView;
    private ImageView pictureView;
    private TextView birthDateView;
    private TextView bookshelfView;
    private TextView deathDateView;
    private EditParcelableLauncher<Author> editAuthorLauncher;

    /**
     * Delegate to handle cover replacement, rotation, etc.
     * MUST keep a strong reference.
     *
     * @noinspection FieldCanBeLocal
     */
    private ImageHandler imageHandler;
    private FieldFormatter<String> dff;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), requireArguments());

        final FragmentManager fm = getChildFragmentManager();

        displayBookLauncher = new DisplayBookLauncher(this, o ->
                o.ifPresent(data -> vm.setDataModified(data)));

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);

        editAuthorLauncher = new EditParcelableLauncher<>(
                DBKey.FK_AUTHOR,
                EditAuthorDialogFragment::new,
                EditAuthorBottomSheet::new);
        editAuthorLauncher.registerForFragmentResult(fm, this);
        editAuthorLauncher.setOnEditInPlaceListener(author -> vm.onAuthorUpdate(author));

        final Resources res = getContext().getResources();
        dff = new DateFieldFormatter(res.getConfiguration().getLocales().get(0), false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAuthorWorksBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Allow edge-to-edge for the root view, but apply margin insets to the list itself.
        InsetsListenerBuilder.apply(vb.authorWorks);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner());
        nameView = toolbar.findViewById(R.id.name);
        birthDateView = toolbar.findViewById(R.id.birth_date);
        deathDateView = toolbar.findViewById(R.id.death_date);
        bookshelfView = toolbar.findViewById(R.id.bookshelf);

        setupImageView(toolbar);

        vm.onAuthor().observe(getViewLifecycleOwner(), this::onAuthorUpdate);
        vm.getOnBookshelf().observe(getViewLifecycleOwner(), s -> bookshelfView.setText(s));

        vm.onResolverFinished().observe(getViewLifecycleOwner(), this::onResolverFinished);
        vm.onResolverCancelled().observe(getViewLifecycleOwner(), this::onResolverCancelled);
        vm.onResolverFailure().observe(getViewLifecycleOwner(), this::onResolverFailure);

        vb.authorWorks.setHasFixedSize(true);

        final Context context = getContext();
        //noinspection DataFlowIssue
        FastScrollerMode.create(context).attach(vb.authorWorks);

        adapter = new AuthorWorksAdapter(context, vm.getStyle(), List.of(vm.getAuthor()),
                                         vm.getWorks());

        // click -> get the book(s) for that entry and display.
        adapter.setOnRowClickListener(
                (v, position) -> displayBookLauncher.launch(
                        this,
                        vm.getWorks().get(position),
                        vm.getStyle(),
                        vm.isAllBookshelves()));

        final Resources res = getResources();
        rowMenu = MenuUtils.create(context);
        rowMenu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                    R.string.action_delete)
               .setIcon(R.drawable.delete_24px);

        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (anchor, position) -> {
                    final MenuMode menuMode = MenuMode.getMode(getActivity(), rowMenu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(anchor.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(rowMenu, true)
                                .show(anchor, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), null, null, position, rowMenu, true);
                    }
                }
        );

        vb.authorWorks.setAdapter(adapter);

        if (savedInstanceState == null) {
            TipManager.getInstance().show(context, Tip.AUTHORS_WORKS);
        }
    }

    private void setupImageView(@NonNull final Toolbar toolbar) {
        pictureView = toolbar.findViewById(R.id.picture);
        final CircularProgressIndicator progressView =
                toolbar.findViewById(R.id.cover_operation_progress_bar);
        final Resources res = getResources();
        final int width = res.getDimensionPixelSize(R.dimen.author_detail_picture_width);
        final int height = res.getDimensionPixelSize(R.dimen.author_detail_picture_height);
        imageHandler = new ImageHandler
                .Builder(this, 0, width, height)
                .setImageOwner(() -> vm.getAuthor())
                .setOnReloadImage(cIdx -> imageHandler.onBindView(pictureView))
                .setProgressIndicator(progressView)
                .setPlaceholderDrawable(R.drawable.person_24px)
                .build();
        imageHandler.onBindView(pictureView);
        imageHandler.attachOnClickListeners(getChildFragmentManager(), pictureView);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void updateAuthorBySearch() {
        final Context context = getContext();

        // The sids we have for the author
        final List<String> sidKeys = vm.getAuthor()
                                       .getIdentifiers()
                                       .stream()
                                       .map(Identifier.Value::getKey)
                                       .collect(Collectors.toList());
        //noinspection DataFlowIssue
        final List<EngineId> enabledEngines = AuthorResolverFactory.getEngines(context, sidKeys);
        final CharSequence[] options = enabledEngines
                .stream()
                .map(engineId -> engineId.getName(context))
                .toArray(CharSequence[]::new);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lbl_select_website)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setSingleChoiceItems(options, -1, (d, w) -> {
                    d.dismiss();
                    if (w >= 0) {
                        // There will be no progress messages as reading the data itself
                        // is very fast, but connection can take a long time,
                        // so bring up the progress dialog now
                        closeProgressDialog();
                        //noinspection DataFlowIssue
                        progressDelegate = new ProgressDelegate(getProgressFrame())
                                .setTitle(R.string.pt_fetch_author_info)
                                .setMessage(R.string.progress_msg_please_wait)
                                .setPreventSleep(true)
                                .setIndeterminate(true)
                                .setOnCancelListener(v -> vm.cancelResolverTask())
                                .show(() -> getActivity().getWindow());

                        vm.resolve(context, enabledEngines.get(w));
                    }
                })
                .create()
                .show();
    }


    private void onResolverFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e, getString(R.string.error_unexpected),
                             (d, w) -> getActivity().finish());
        });
    }

    private void onResolverCancelled(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.process(resolved -> {
            if (resolved != null) {
                onResolverFinished(resolved);
            } else {
                //noinspection DataFlowIssue
                Snackbar.make(getView(), getString(R.string.cancelled), Snackbar.LENGTH_SHORT)
                        .show();
            }
        });
    }

    private void onResolverFinished(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();

        message.process(this::onResolverFinished);
    }

    private void onResolverFinished(@NonNull final Boolean resolved) {
        if (resolved) {
            onAuthorUpdate(vm.getAuthor());
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.action_done, Snackbar.LENGTH_SHORT)
                    .show();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.warning_no_extra_info_found, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private void onAuthorUpdate(@NonNull final Author author) {
        final Context context = getContext();

        //noinspection DataFlowIssue
        nameView.setText(Html.fromHtml(author.getLabel(context, Details.Full, vm.getStyle()), 0));

        imageHandler.onBindView(pictureView);

        // Don't hide when empty, we want the layout to be static
        birthDateView.setText(author.getBirthDate()
                                    .map(d -> getString(R.string.name_colon_value,
                                                        getString(R.string.lbl_date_born),
                                                        dff.format(getContext(), d)))
                                    .orElse(null));

        // Don't hide when empty, we want the layout to be static
        deathDateView.setText(author.getDeathDate()
                                    .map(d -> getString(R.string.name_colon_value,
                                                        getString(R.string.lbl_date_died),
                                                        dff.format(getContext(), d)))
                                    .orElse(null));
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

        final AuthorWork work = vm.getWorks().get(position);

        if (menuItemId == R.id.MENU_DELETE) {
            deleteWork(position, work);
            return true;
        }
        return false;
    }

    private void deleteWork(final int position,
                            @NonNull final AuthorWork work) {
        Author primaryAuthor = work.getPrimaryAuthor();
        // Sanity check
        if (primaryAuthor == null) {
            //noinspection DataFlowIssue
            primaryAuthor = Author.createUnknownAuthor(getContext());
        }
        switch (work.getWorkType()) {
            case TocEntry: {
                //noinspection DataFlowIssue
                StandardDialogs.deleteTocEntry(
                        getContext(),
                        (TocEntry) work, () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            case Book:
            case BookLight: {
                //noinspection DataFlowIssue
                StandardDialogs.deleteBook(
                        getContext(),
                        work.getLabel(getContext(), Details.AutoSelect, vm.getStyle()),
                        Collections.singletonList(primaryAuthor), () -> {
                            vm.delete(getContext(), work);
                            adapter.notifyItemRemoved(position);
                        });
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(work));
        }
    }


    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater inflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            inflater.inflate(R.menu.author_works, menu);

            final Context context = getContext();

            //noinspection DataFlowIssue
            MenuUtils.customizeMenuGroupTitle(context, menu,
                                              R.id.sm_title_author_works_sort);
            MenuUtils.customizeMenuGroupTitle(context, menu,
                                              R.id.sm_title_author_works_filter);

            vm.getMenuHandlers().forEach(
                    h -> h.onCreateMenu(context, menu, inflater, vm.getAuthor()));
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            // show if we got here with a specific bookshelf selected.
            // hide if the bookshelf was set to Bookshelf.ALL_BOOKS.
            menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES)
                .setVisible(vm.getBookshelfId() != Bookshelf.ALL_BOOKS)
                .setChecked(vm.isAllBookshelves());

            //noinspection DataFlowIssue
            vm.getMenuHandlers().forEach(
                    h -> h.onPrepareMenu(getContext(), menu, vm.getAuthor()));

        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_AUTHOR_EDIT) {
                //noinspection DataFlowIssue
                editAuthorLauncher.editInPlace(getContext(), vm.getAuthor());
                return true;

            } else if (menuItemId == R.id.MENU_UPDATE_ITEM_BY_SEARCH) {
                updateAuthorBySearch();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_SORT_TITLE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.TITLE_OB);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_SORT_FIRST_PUBLICATION_DATE) {
                menuItem.setChecked(true);
                vm.setOrderByColumn(DBKey.FIRST_PUBLICATION_DATE);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_ALL) {
                menuItem.setChecked(true);
                vm.setFilter(true, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_TOC) {
                menuItem.setChecked(true);
                vm.setFilter(true, false);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_FILTER_BOOKS) {
                menuItem.setChecked(true);
                vm.setFilter(false, true);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;

            } else if (menuItemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
                final boolean checked = !menuItem.isChecked();
                menuItem.setChecked(checked);
                //noinspection DataFlowIssue
                vm.setAllBookshelves(getContext(), checked);
                vm.reloadWorkList();
                adapter.notifyDataSetChanged();
                return true;
            }

            //noinspection DataFlowIssue
            return vm.getMenuHandlers()
                     .stream()
                     .anyMatch(h -> h.onMenuItemSelected(
                             getContext(), menuItemId, vm.getAuthor()));
        }
    }
}
