/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
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
import com.hardbacknutter.nevertoomanybooks.booklist.ShowContextMenu;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditTocEntryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;

/**
 * The ISFDB direct interaction should be seen as temporary as this class
 * should not have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a
 * single book, and hence a single TOC. The interaction here with ISFDB allows
 * the user to reject the first (book)TOC found, and get the next one (etc...).
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookTocFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_TOC = TAG + ":rk:" + EditTocEntryDialogFragment.TAG;
    /** FragmentResultListener request key. */
    private static final String RK_CONFIRM_TOC = TAG + ":rk:" + ConfirmTocDialogFragment.TAG;
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
    private final List<Edition> isfdbEditions = new ArrayList<>();

    /** the rows. A reference to the parcelled list in the Book. */
    private List<TocEntry> tocEntryList;

    /** View Binding. */
    private FragmentEditBookTocBinding vb;

    /** The adapter for the list. */
    private TocListEditAdapter adapter;

    /** Listen for the results of the entry edit-dialog. */
    private final EditTocEntryDialogFragment.Launcher editTocEntryLauncher =
            new EditTocEntryDialogFragment.Launcher(RK_EDIT_TOC, this::onEntryUpdated);

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /** Handles the ISFDB lookup tasks. */
    private EditBookTocViewModel editTocVm;

    private final ConfirmTocDialogFragment.Launcher confirmTocResultsLauncher =
            new ConfirmTocDialogFragment.Launcher(RK_CONFIRM_TOC) {
                @Override
                public void onResult(@NonNull final Book.ContentType contentType,
                                     @NonNull final List<TocEntry> tocEntries) {
                    onIsfdbDataConfirmed(contentType, tocEntries);
                }

                @Override
                public void searchNextEdition() {
                    searchIsfdb();
                }
            };

    private ExtPopupMenu contextMenu;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Toc;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editTocEntryLauncher.registerForFragmentResult(fm, this);
        confirmTocResultsLauncher.registerForFragmentResult(fm, this);
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

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_add_24);
        //noinspection DataFlowIssue
        fab.setOnClickListener(v -> editEntry(
                new TocEntry(vm.getPrimaryAuthor(getContext()), ""), POS_NEW_ENTRY));

        getToolbar().addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                     Lifecycle.State.RESUMED);

        //noinspection DataFlowIssue
        vm.initFields(getContext(), FragmentId.Toc, FieldGroup.Toc);

        initEditTocViewModel();

        contextMenu = MenuUtils.createEditDeleteContextMenu(getContext());
        initListView();

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.tocList);
    }

    private void initEditTocViewModel() {
        editTocVm = new ViewModelProvider(this).get(EditBookTocViewModel.class);
        editTocVm.onIsfdbEditions().observe(getViewLifecycleOwner(), this::onIsfdbEditions);
        editTocVm.onIsfdbBook().observe(getViewLifecycleOwner(), this::onIsfdbBook);

        editTocVm.onIsfdbEditionsCancelled().observe(getViewLifecycleOwner(), message ->
                message.process(ignored -> Snackbar
                        .make(vb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        editTocVm.onIsfdbEditionsFailure().observe(getViewLifecycleOwner(), message ->
                message.process(e -> Snackbar
                        .make(vb.getRoot(), R.string.warning_no_editions,
                              Snackbar.LENGTH_LONG).show()));

        editTocVm.onIsfdbBookCancelled().observe(getViewLifecycleOwner(), message ->
                message.process(ignored -> Snackbar
                        .make(vb.getRoot(), R.string.cancelled,
                              Snackbar.LENGTH_LONG).show()));
        editTocVm.onIsfdbBookFailure().observe(getViewLifecycleOwner(), message ->
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
                ShowContextMenu.getPreferredMode(context),
                (v, position) -> contextMenu
                        .showAsDropDown(v, menuItem -> onMenuItemSelected(menuItem, position)));

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
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_EDIT) {
            editEntry(tocEntryList.get(position), position);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
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
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_remove_toc_entry,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context)))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
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
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.action_delete)
                        .setMessage(context.getString(R.string.confirm_scope_for_delete,
                                                      tocEntry.getTitle(),
                                                      tocEntry.getPrimaryAuthor()
                                                              .getLabel(context),
                                                      context.getString(R.string.btn_all_books)))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
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
        editTocEntryLauncher.launch(vm.getBook(), position, tocEntry, vm.isAnthology());
    }

    /**
     * We got one or more editions from ISFDB.
     * <p>
     * Stores the urls locally as the user might want to try the next in line.
     *
     * @param message list of editions
     */
    private void onIsfdbEditions(@NonNull final LiveDataEvent<List<Edition>> message) {
        message.process(editionList -> {
            isfdbEditions.clear();
            isfdbEditions.addAll(editionList);
            searchIsfdb();
        });
    }

    private void onIsfdbBook(@NonNull final LiveDataEvent<Book> message) {
        message.process(bookData -> {

            final Book book = vm.getBook();

            // update the book with Series information (if any) that was gathered from the TOC
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
            // update the book with the first publication date that was gathered from the TOC
            if (!book.getFirstPublicationDate().isPresent()) {
                final PartialDate bookFirstPublication = bookData.getFirstPublicationDate();
                if (bookFirstPublication.isPresent()) {
                    book.setFirstPublicationDate(bookFirstPublication);
                }
            }

            // finally the TOC itself:  display it for the user to approve
            // If there are more editions, the neutral button will allow to fetch the next one.
            confirmTocResultsLauncher.launch(bookData.getToc(),
                                             bookData.getLong(DBKey.BOOK_CONTENT_TYPE),
                                             !isfdbEditions.isEmpty());
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onIsfdbDataConfirmed(@NonNull final Book.ContentType contentType,
                                      @NonNull final Collection<TocEntry> tocEntries) {
        if (contentType != Book.ContentType.Book) {
            final Field<Long, View> typeField = vm.requireField(R.id.book_type);
            typeField.setValue(contentType.getId());
        }

        // append the new data
        // can create duplicates if the user mixes manual input with automatic (or 2 x automatic...)
        // They will get weeded out when saved to the DAO
        tocEntryList.addAll(tocEntries);
        adapter.notifyDataSetChanged();
    }

    private void searchIsfdb() {
        if (isfdbEditions.isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            editTocVm.searchEdition(isfdbEditions.get(0));
            isfdbEditions.remove(0);
        }
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "ConfirmTocDialogFrag";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";
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
            requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                BKEY_REQUEST_KEY);
            tocEntries = Objects.requireNonNull(args.getParcelableArrayList(Book.BKEY_TOC_LIST),
                                                Book.BKEY_TOC_LIST);

            bookContentType = Book.ContentType.getType(args.getInt(DBKey.BOOK_CONTENT_TYPE));
            hasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final View rootView = getLayoutInflater()
                    .inflate(R.layout.dialog_toc_confirm, null, false);
            final TextView contentView = rootView.findViewById(R.id.content);

            final boolean hasToc = tocEntries != null && !tocEntries.isEmpty();
            if (hasToc) {
                //noinspection DataFlowIssue
                final StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(tocEntries.stream()
                                                  .map(entry -> entry.getLabel(getContext()))
                                                  .collect(Collectors.joining(", ")));
                contentView.setText(message);

            } else {
                contentView.setText(R.string.error_auto_toc_population_failed);
            }

            //noinspection DataFlowIssue
            final AlertDialog dialog =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_baseline_warning_24)
                            .setView(rootView)
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
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

        public abstract static class Launcher
                implements FragmentResultListener {

            private static final String SEARCH_NEXT_EDITION = "searchNextEdition";
            private static final String TOC_BIT_MASK = "tocBitMask";
            private static final String TOC_LIST = "tocEntries";
            @NonNull
            private final String requestKey;
            private FragmentManager fragmentManager;

            protected Launcher(@NonNull final String requestKey) {
                this.requestKey = requestKey;
            }

            static void setResult(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey,
                                  @NonNull final Book.ContentType bookContentType,
                                  @NonNull final List<TocEntry> tocEntries) {
                final Bundle result = new Bundle(2);
                result.putLong(TOC_BIT_MASK, bookContentType.getId());
                result.putParcelableArrayList(TOC_LIST, new ArrayList<>(tocEntries));
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            static void searchNextEdition(@NonNull final Fragment fragment,
                                          @NonNull final String requestKey) {
                final Bundle result = new Bundle(1);
                result.putBoolean(SEARCH_NEXT_EDITION, true);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            /**
             * Launch the dialog.
             *
             * @param toc              the list of TocEntry's
             * @param bookContentType  the {@link Book.ContentType} ordinal
             * @param hasOtherEditions flag
             */
            public void launch(@NonNull final List<TocEntry> toc,
                               final long bookContentType,
                               final boolean hasOtherEditions) {

                final Bundle args = new Bundle(4);
                args.putParcelableArrayList(Book.BKEY_TOC_LIST, new ArrayList<>(toc));
                args.putString(BKEY_REQUEST_KEY, requestKey);
                args.putLong(DBKey.BOOK_CONTENT_TYPE, bookContentType);
                args.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);

                final DialogFragment fragment = new ConfirmTocDialogFragment();
                fragment.setArguments(args);
                fragment.show(fragmentManager, TAG);
            }

            public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                                  @NonNull final LifecycleOwner lifecycleOwner) {
                this.fragmentManager = fragmentManager;
                this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner,
                                                               this);
            }

            @Override
            public void onFragmentResult(@NonNull final String requestKey,
                                         @NonNull final Bundle result) {
                if (result.getBoolean(SEARCH_NEXT_EDITION)) {
                    searchNextEdition();
                } else {
                    onResult(Book.ContentType.getType(result.getLong(TOC_BIT_MASK)),
                             Objects.requireNonNull(result.getParcelableArrayList(TOC_LIST),
                                                    TOC_LIST));
                }
            }

            /**
             * Callback handler.
             *
             * @param contentType bit flags
             * @param tocEntries  the list of entries
             */
            public abstract void onResult(@NonNull Book.ContentType contentType,
                                          @NonNull List<TocEntry> tocEntries);

            /**
             * Callback handler.
             */
            public abstract void searchNextEdition();
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

            final Holder holder = new Holder(
                    RowEditTocEntryBinding.inflate(getLayoutInflater(), parent, false));

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

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(R.id.MENU_POPULATE_TOC_FROM_ISFDB, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0,
                     R.string.option_isfdb_menu_populate_toc);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {

            if (menuItem.getItemId() == R.id.MENU_POPULATE_TOC_FROM_ISFDB) {
                final Book book = vm.getBook();
                final long isfdbId = book.getLong(DBKey.SID_ISFDB);
                if (isfdbId != 0) {
                    Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    editTocVm.searchBook(isfdbId);
                    return true;
                }

                final String isbnStr = book.getString(DBKey.BOOK_ISBN);
                if (!isbnStr.isEmpty()) {
                    final ISBN isbn = new ISBN(isbnStr, true);
                    if (isbn.isValid(true)) {
                        Snackbar.make(vb.getRoot(), R.string.progress_msg_connecting,
                                      Snackbar.LENGTH_LONG).show();
                        editTocVm.searchByIsbn(isbn);
                        return true;
                    }
                }
                Snackbar.make(vb.getRoot(), R.string.warning_requires_isbn,
                              Snackbar.LENGTH_LONG).show();
                return true;
            }
            return false;
        }
    }
}
