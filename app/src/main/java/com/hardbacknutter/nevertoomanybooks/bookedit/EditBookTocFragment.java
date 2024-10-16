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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogTocConfirmBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTocEntryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.AltEditionIsfdb;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * The ISFDB direct interaction should be seen as temporary as this class
 * should not have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a
 * single book, and hence a single TOC. The interaction here with ISFDB allows
 * the user to reject the first (book)TOC found, and get the next one (etc...).
 * <p>
 * 2024-04-27: not converting {@link ConfirmTocDialogFragment} to
 * a BottomSheet or BaseFFDialogFragment.
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookTocFragment";
    private static final String RK_MENU = TAG + ":rk:menu";

    /** FragmentResultListener request key. */
    private static final String RK_CONFIRM_TOC = TAG + ":rk:toc";
    private static final int POS_NEW_ENTRY = -1;

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @NonNull
    private final List<AltEditionIsfdb> isfdbEditions = new ArrayList<>();

    /** the rows. A reference to the parcelled list in the Book. */
    private List<TocEntry> tocEntryList;

    /** View Binding. */
    private FragmentEditBookTocBinding vb;

    /** The adapter for the list. */
    private TocListEditAdapter adapter;

    /** Listen for the results of the entry edit-dialog. */
    private EditTocEntryLauncher editTocEntryLauncher;
    private ExtMenuLauncher menuLauncher;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /** Handles the ISFDB lookup tasks. */
    private IsfdbTocSearchViewModel isfdbTocSearchVm;

    private ConfirmTocDialogFragment.Launcher confirmTocResultsLauncher;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Toc;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editTocEntryLauncher = new EditTocEntryLauncher(this::onEntryUpdated);
        editTocEntryLauncher.registerForFragmentResult(fm, this);

        confirmTocResultsLauncher = new ConfirmTocDialogFragment.Launcher(
                RK_CONFIRM_TOC,
                this::onIsfdbDataConfirmed, this::searchIsfdbNextEdition);
        confirmTocResultsLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditBookTocBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.apply(vb.tocList);

        initFab();

        getToolbar().addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                     Lifecycle.State.RESUMED);

        //noinspection DataFlowIssue
        vm.initFields(getContext(), FragmentId.Toc, FieldGroup.Toc);

        initIsfdbTocSearchViewModel();

        initListView();

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.tocList);
    }

    private void initFab() {
        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.add_24px);
        fab.setOnClickListener(v -> {
            Author tocAuthor = vm.getBook().getPrimaryAuthor();
            if (tocAuthor == null) {
                //noinspection DataFlowIssue
                tocAuthor = Author.createUnknownAuthor(getContext());
            }
            editEntry(new TocEntry(tocAuthor, ""), POS_NEW_ENTRY);
        });
    }

    private void initIsfdbTocSearchViewModel() {
        isfdbTocSearchVm = new ViewModelProvider(this).get(IsfdbTocSearchViewModel.class);
        isfdbTocSearchVm.onIsfdbEditions().observe(getViewLifecycleOwner(), this::onIsfdbEditions);
        isfdbTocSearchVm.onIsfdbBook().observe(getViewLifecycleOwner(), this::onIsfdbBook);

        isfdbTocSearchVm.onIsfdbEditionsCancelled().observe(getViewLifecycleOwner(), message ->
                message.process(ignored -> Snackbar
                        .make(vb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        isfdbTocSearchVm.onIsfdbEditionsFailure().observe(getViewLifecycleOwner(), message ->
                message.process(e -> Snackbar
                        .make(vb.getRoot(), R.string.warning_no_editions,
                              Snackbar.LENGTH_LONG).show()));

        isfdbTocSearchVm.onIsfdbBookCancelled().observe(getViewLifecycleOwner(), message ->
                message.process(ignored -> Snackbar
                        .make(vb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        isfdbTocSearchVm.onIsfdbBookFailure().observe(getViewLifecycleOwner(), message ->
                message.process(e -> Snackbar
                        .make(vb.getRoot(), R.string.warning_book_not_found,
                              Snackbar.LENGTH_LONG).show()));
    }

    private void initListView() {
        final Context context = getContext();

        tocEntryList = vm.getBook().getToc();

        //noinspection DataFlowIssue
        adapter = new TocListEditAdapter(context, tocEntryList,
                                         vh -> itemTouchHelper.startDrag(vh));

        adapter.setOnRowClickListener(
                (v, position) -> editEntry(tocEntryList.get(position), position));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (v, position) -> {
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    //noinspection DataFlowIssue
                    final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(v.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setMenuOwner(position)
                                .setMenu(menu, true)
                                .show(v, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), null, null, position, menu, true);
                    }
                });

        adapter.registerAdapterDataObserver(adapterDataObserver);
        vb.tocList.setAdapter(adapter);


        vb.tocList.addItemDecoration(
                new MaterialDividerItemDecoration(context, RecyclerView.VERTICAL));
        vb.tocList.setHasFixedSize(true);
    }

    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    private void onEntryUpdated(@NonNull final TocEntry tocEntry,
                                final int position) {
        if (position == POS_NEW_ENTRY) {
            // see if it already exists
            //noinspection DataFlowIssue
            vm.fixId(getContext(), tocEntry);
            // and check it's not already in the list.
            if (tocEntryList.contains(tocEntry)) {
                Snackbar.make(vb.getRoot(), R.string.warning_already_in_list,
                              Snackbar.LENGTH_LONG).show();
            } else {
                // It's a new entry, add it to the end and scroll it into view
                tocEntryList.add(tocEntry);
                adapter.notifyItemInserted(tocEntryList.size() - 1);
                vb.tocList.scrollToPosition(adapter.getItemCount() - 1);
            }

        } else {
            // It's an existing entry in the list, find it and update with the new data
            final TocEntry original = tocEntryList.get(position);
            original.copyFrom(tocEntry);
            adapter.notifyItemChanged(position);
            vb.tocList.scrollToPosition(position);
        }
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        getFab().setVisibility(View.VISIBLE);

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);
        book.setToc(tocEntryList);
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
            editEntry(tocEntryList.get(position), position);
            return true;

        } else if (menuItemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;
        }
        return false;
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final TocEntry tocEntry = tocEntryList.get(position);
        if (tocEntry.getId() == 0) {
            // It's a newly added entry, not saved; just remove it from the list.
            tocEntryList.remove(tocEntry);
            adapter.notifyItemRemoved(position);

        } else {
            final Context context = getContext();
            if (tocEntry.getBookCount() == 1) {
                // The entry is saved, but only occurs in this single book.
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_remove_toc_entry,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context)))
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(R.string.action_delete, (d, w) -> {
                            // We don't actually delete anything here as the user must be
                            // able to cancel the edit. So just remove it from the list.
                            tocEntryList.remove(tocEntry);
                            adapter.notifyItemRemoved(position);
                        })
                        .create()
                        .show();

            } else {
                // The entry is saved and occurs in multiple books.
                // Offer deleting from this book only (i.e. 'remove') or an actual delete from
                // all books.
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_scope_for_delete,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context),
                                                      context.getString(R.string.btn_all_books)))
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                        .setNeutralButton(R.string.btn_this_book, (d, w) -> {
                            // We don't actually delete anything here as the user must be
                            // able to cancel the edit. So just remove it from the list.
                            tocEntryList.remove(tocEntry);
                            adapter.notifyItemRemoved(position);
                        })
                        .setPositiveButton(R.string.btn_all_books, (d, w) -> {
                            // This is a hard delete and done immediately.
                            if (vm.deleteTocEntry(context, tocEntry)) {
                                tocEntryList.remove(tocEntry);
                                adapter.notifyItemRemoved(position);
                            }
                        })
                        .create()
                        .show();
            }
        }
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param tocEntry to edit
     * @param position the position of the item; use {@link #POS_NEW_ENTRY} for a new entry.
     */
    private void editEntry(@NonNull final TocEntry tocEntry,
                           final int position) {
        //noinspection DataFlowIssue
        editTocEntryLauncher.launch(getActivity(), vm.getBook(), position,
                                    tocEntry, vm.isAnthology());
    }

    @SuppressLint({"NotifyDataSetChanged", "MethodOnlyUsedFromInnerClass"})
    private void updateWithPrimaryBookAuthor() {
        final Author tocAuthor = vm.getBook().getPrimaryAuthor();
        // Sanity/paranoia check, this should never be the case
        // as we disable the menu option in onPrepareMenu
        if (tocAuthor == null) {
            Snackbar.make(vb.getRoot(), R.string.bob_empty_author,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        final Context context = getContext();
        //noinspection DataFlowIssue
        final String message = context.getString(
                R.string.confirm_toc_list_update_with_books_main_author,
                tocAuthor.getLabel(context));

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.option_toc_list_update_with_main_author)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.ok, (d, w) -> {
                    tocEntryList.forEach(tocEntry -> tocEntry.setPrimaryAuthor(tocAuthor));
                    adapter.notifyDataSetChanged();
                })
                .create()
                .show();
    }

    /**
     * Search for the book (editions) on ISFDB.
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void searchIsfdb() {
        final Book book = vm.getBook();
        final long isfdbId = book.getLong(DBKey.SID_ISFDB);
        if (isfdbId != 0) {
            Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            isfdbTocSearchVm.searchBook(isfdbId);
            return;
        }

        final String isbnStr = book.getString(DBKey.BOOK_ISBN);
        if (!isbnStr.isEmpty()) {
            final ISBN isbn = new ISBN(isbnStr, true);
            if (isbn.isValid(true)) {
                Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                isfdbTocSearchVm.searchByIsbn(isbn);
                return;
            }
        }

        Snackbar.make(vb.getRoot(), R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    /**
     * Search for the next possible edition on ISFDB.
     */
    private void searchIsfdbNextEdition() {
        if (isfdbEditions.isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            isfdbTocSearchVm.searchEdition(isfdbEditions.get(0));
            isfdbEditions.remove(0);
        }
    }

    /**
     * We got one or more editions from ISFDB.
     * <p>
     * Stores the urls locally as the user might want to try the next in line,
     * and fetches the first one in the list.
     *
     * @param message list of editions
     */
    private void onIsfdbEditions(@NonNull final LiveDataEvent<List<AltEditionIsfdb>> message) {
        message.process(editionList -> {
            isfdbEditions.clear();
            isfdbEditions.addAll(editionList);
            searchIsfdbNextEdition();
        });
    }

    /**
     * We got the resulting ISFDB book for the edition we searched for.
     * Prompt the user to accept it, or to search for the next edition.
     *
     * @param message with the book data
     */
    private void onIsfdbBook(@NonNull final LiveDataEvent<Book> message) {
        message.process(bookData -> {

            final Book book = vm.getBook();

            // Copy any native id to the current book (just overwrite, ISFDB data is VERY accurate)
            book.copyExternalIdsFrom(bookData);

            // update the book with Series information (if any) which was gathered from the TOC
            final List<Series> series = bookData.getSeries();
            if (!series.isEmpty()) {
                final List<Series> inBook = book.getSeries();
                // add, weeding out duplicates
                for (final Series s : series) {
                    if (!inBook.contains(s)) {
                        inBook.add(s);
                    }
                }
                book.setSeries(inBook);
            }

            // if we don't have one yet,
            // update the book with the first publication date which was gathered from the TOC
            if (!book.getFirstPublicationDate().isPresent()) {
                final PartialDate bookFirstPublication = bookData.getFirstPublicationDate();
                if (bookFirstPublication.isPresent()) {
                    book.setFirstPublicationDate(bookFirstPublication);
                }
            }

            // finally the TOC itself:  display it for the user to approve
            // If there are more editions, the neutral button will allow to fetch the next one.
            //noinspection DataFlowIssue
            confirmTocResultsLauncher.launch(getActivity(), bookData.getToc(),
                                             bookData.getContentType(),
                                             !isfdbEditions.isEmpty());
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onIsfdbDataConfirmed(@NonNull final Book.ContentType contentType,
                                      @NonNull final Collection<TocEntry> tocEntries) {
        if (contentType != Book.ContentType.Book) {
            final Field<Long, View> typeField = vm.requireField(R.id.book_type);
            // Don't bother updating the book, that is done automatically when saving the book
            typeField.setValue(contentType.getId());
        }

        // append the new data
        // can create duplicates if the user mixes manual input with automatic (or 2 x automatic...)
        // They will get weeded out when saved to the DAO
        tocEntryList.addAll(tocEntries);
        adapter.notifyDataSetChanged();
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     * <p>
     * URGENT: needs converting to Dialog/BottomSheet
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "ConfirmTocDialogFrag";
        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";
        /** FragmentResultListener request key to use for our response. */
        private String requestKey;
        private boolean hasOtherEditions;
        private Book.ContentType bookContentType;
        private List<TocEntry> tocEntries;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                                DialogLauncher.BKEY_REQUEST_KEY);
            tocEntries = Objects.requireNonNull(args.getParcelableArrayList(Book.BKEY_TOC_LIST),
                                                Book.BKEY_TOC_LIST);

            bookContentType = Objects.requireNonNull(args.getParcelable(DBKey.BOOK_CONTENT_TYPE),
                                                     DBKey.BOOK_CONTENT_TYPE);
            hasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final DialogTocConfirmBinding vb = DialogTocConfirmBinding
                    .inflate(getLayoutInflater(), null, false);

            final boolean hasToc = tocEntries != null && !tocEntries.isEmpty();
            if (hasToc) {
                //noinspection DataFlowIssue
                final StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(tocEntries.stream()
                                                  .map(entry -> entry.getLabel(getContext()))
                                                  .collect(Collectors.joining(", ")));
                vb.tocList.setText(message);
            } else {
                vb.tocList.setText(R.string.error_auto_toc_population_failed);
            }

            //noinspection DataFlowIssue
            final AlertDialog dialog =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.warning_24px)
                            .setView(vb.getRoot())
                            .setNegativeButton(R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
                                 (d, which) -> Launcher.setResult(this, requestKey,
                                                                  bookContentType, tocEntries));
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (hasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_retry),
                                 (d, which) -> Launcher.searchNextEdition(this, requestKey));
            }

            return dialog;
        }

        public static class Launcher
                extends DialogLauncher {

            private static final String SEARCH_NEXT_EDITION = "searchNextEdition";
            private static final String BKEY_TOC_LIST = "tocEntries";
            @NonNull
            private final ResultListener resultListener;
            @NonNull
            private final OnSearchNextListener onSearchNextListener;

            /**
             * Constructor.
             *
             * @param requestKey           FragmentResultListener request key to use for our
             *                             response.
             * @param resultListener       listener
             * @param onSearchNextListener listener to trigger if the user wants a new/next search
             */
            Launcher(@NonNull final String requestKey,
                     @NonNull final ResultListener resultListener,
                     @NonNull final OnSearchNextListener onSearchNextListener) {
                super(requestKey,
                      ConfirmTocDialogFragment::new,
                      // ENHANCE: implement ConfirmTocBottomSheet
                      ConfirmTocDialogFragment::new);
                this.resultListener = resultListener;
                this.onSearchNextListener = onSearchNextListener;
            }

            /**
             * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
             *
             * @param fragment        the calling DialogFragment
             * @param requestKey      to use
             * @param bookContentType the type
             * @param tocEntries      the list of entries
             *
             * @see #onFragmentResult(String, Bundle)
             */
            @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
            static void setResult(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey,
                                  @NonNull final Book.ContentType bookContentType,
                                  @NonNull final List<TocEntry> tocEntries) {
                final Bundle result = new Bundle(2);
                result.putParcelable(DBKey.BOOK_CONTENT_TYPE, bookContentType);
                result.putParcelableArrayList(BKEY_TOC_LIST, new ArrayList<>(tocEntries));
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
            static void searchNextEdition(@NonNull final Fragment fragment,
                                          @NonNull final String requestKey) {
                final Bundle result = new Bundle(1);
                result.putBoolean(SEARCH_NEXT_EDITION, true);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            /**
             * Launch the dialog.
             *
             * @param context          preferably the {@code Activity}
             *                         but another UI {@code Context} will also do.
             * @param toc              the list of TocEntry's
             * @param bookContentType  the type
             * @param hasOtherEditions flag
             */
            public void launch(@NonNull final Context context,
                               @NonNull final List<TocEntry> toc,
                               @NonNull final Book.ContentType bookContentType,
                               final boolean hasOtherEditions) {

                final Bundle args = new Bundle(4);
                args.putParcelableArrayList(Book.BKEY_TOC_LIST, new ArrayList<>(toc));
                args.putParcelable(DBKey.BOOK_CONTENT_TYPE, bookContentType);
                args.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);

                showDialog(context, args);
            }

            @Override
            public void onFragmentResult(@NonNull final String requestKey,
                                         @NonNull final Bundle result) {
                if (result.getBoolean(SEARCH_NEXT_EDITION)) {
                    onSearchNextListener.searchNextEdition();
                } else {
                    resultListener.onResult(
                            Objects.requireNonNull(result.getParcelable(DBKey.BOOK_CONTENT_TYPE),
                                                   DBKey.BOOK_CONTENT_TYPE),
                            Objects.requireNonNull(result.getParcelableArrayList(BKEY_TOC_LIST),
                                                   BKEY_TOC_LIST));
                }
            }

            @FunctionalInterface
            public interface ResultListener {
                /**
                 * Callback handler.
                 *
                 * @param bookContentType the type
                 * @param tocEntries      the list of entries
                 */
                void onResult(@NonNull Book.ContentType bookContentType,
                              @NonNull List<TocEntry> tocEntries);
            }

            @FunctionalInterface
            public interface OnSearchNextListener {
                /**
                 * Callback handler.
                 */
                void searchNextEdition();
            }
        }
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder
            implements BindableViewHolder<TocEntry> {

        @NonNull
        private final RowEditTocEntryBinding vb;

        Holder(@NonNull final RowEditTocEntryBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        @Override
        public void onBind(@NonNull final TocEntry tocEntry) {
            final Context context = itemView.getContext();
            vb.title.setText(tocEntry.getTitle());
            vb.author.setText(tocEntry.getPrimaryAuthor().getLabel(context));

            final PartialDate date = tocEntry.getFirstPublicationDate();
            if (date.isPresent()) {
                vb.year.setVisibility(View.VISIBLE);
                // cut the date to just the year.
                vb.year.setText(context.getString(R.string.brackets,
                                                  String.valueOf(date.getYearValue())));
            } else {
                vb.year.setVisibility(View.GONE);
            }
        }
    }

    private static class TocListEditAdapter
            extends BaseDragDropRecyclerViewAdapter<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of {@link TocEntry}'s
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        TocListEditAdapter(@NonNull final Context context,
                           @NonNull final List<TocEntry> items,
                           @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowEditTocEntryBinding vb = RowEditTocEntryBinding.inflate(
                    getLayoutInflater(), parent, false);
            final Holder holder = new Holder(vb);
            holder.setOnRowClickListener(rowClickListener);
            holder.setOnRowLongClickListener(contextMenuMode, rowShowMenuListener);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);
            holder.onBind(getItem(position));
        }
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);

            menu.add(R.id.MENU_TOC_LIST_UPDATE_WITH_PRIMARY_AUTHOR,
                     R.id.MENU_TOC_LIST_UPDATE_WITH_PRIMARY_AUTHOR,
                     0,
                     R.string.option_toc_list_update_with_main_author);
            menu.add(R.id.MENU_POPULATE_TOC_FROM_ISFDB,
                     R.id.MENU_POPULATE_TOC_FROM_ISFDB,
                     0,
                     R.string.option_isfdb_menu_populate_toc);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final Author tocAuthor = vm.getBook().getPrimaryAuthor();
            menu.findItem(R.id.MENU_TOC_LIST_UPDATE_WITH_PRIMARY_AUTHOR)
                .setEnabled(tocAuthor != null);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {

            if (menuItem.getItemId() == R.id.MENU_TOC_LIST_UPDATE_WITH_PRIMARY_AUTHOR) {
                updateWithPrimaryBookAuthor();
                return true;

            } else if (menuItem.getItemId() == R.id.MENU_POPULATE_TOC_FROM_ISFDB) {
                searchIsfdb();
                return true;
            }
            return false;
        }
    }
}
