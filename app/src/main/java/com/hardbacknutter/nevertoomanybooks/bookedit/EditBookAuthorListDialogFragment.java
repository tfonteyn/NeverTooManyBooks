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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.SoftwareKeyboardControllerCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.core.widgets.drapdropswipe.StartDragListener;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorListBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexToolbar;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EntityFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.settings.MenuMode;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BaseDragDropRecyclerViewAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.BindableViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.CheckableDragDropViewHolder;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuButton;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuLauncher;
import com.hardbacknutter.nevertoomanybooks.widgets.popupmenu.ExtMenuPopupWindow;

/**
 * Edit the list of Authors of a Book.
 * <p>
 * This is a plain fullscreen DialogFragment.
 * Displayed on top of edit-book fragment(s) which run inside a ViewPager.
 * Row edits use a Dialog/BottomSheet displayed on top of this fragment.
 */
public class EditBookAuthorListDialogFragment
        extends DialogFragment
        implements FlexToolbar {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookAuthorListDlg";
    private static final String RK_MENU = TAG + ":rk:menu";

    /** Book View model. Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookAuthorListBinding vb;
    /** the rows. */
    private List<Author> authorList;
    /** React to list changes. */
    private final SimpleAdapterDataObserver adapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    vm.getBook().setStage(EntityStage.Stage.Dirty);
                    vm.updateAuthors(authorList);
                }
            };
    /** The adapter for the list itself. */
    private AuthorListAdapter adapter;
    private EditParcelableLauncher<Author> editLauncher;
    private ExtMenuLauncher menuLauncher;

    /** Drag and drop support for the list view. */
    private ItemTouchHelper itemTouchHelper;

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookAuthorListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        editLauncher = EditParcelableLauncher.create(DBKey.FK_AUTHOR,
                                                     this::add, this::processChanges);
        editLauncher.registerForFragmentResult(fm, this);

        menuLauncher = new ExtMenuLauncher(RK_MENU, this::onMenuItemSelected);
        menuLauncher.registerForFragmentResult(fm, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogEditBookAuthorListBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = new Dialog(requireContext(), R.style.Theme_App_FullScreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsListenerBuilder.apply(vb.authorList);

        initToolbar(vb.toolbar);
        vb.toolbar.setSubtitle(vm.getBook().getTitle());

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllAuthorNames());
        vb.author.setAdapter(nameAdapter);
        TilUtil.autoRemoveError(vb.author, vb.lblAuthor);

        // soft-keyboards 'done' button act as a shortcut to add the author
        vb.author.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd(false);
                return true;
            }
            return false;
        });

        initListView();

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        itemTouchHelper.attachToRecyclerView(vb.authorList);
    }

    private void initListView() {
        final Context context = getContext();

        authorList = vm.getBook().getAuthors();

        //noinspection DataFlowIssue
        adapter = new AuthorListAdapter(context, authorList, vm.getStyle(),
                                        vh -> itemTouchHelper.startDrag(vh));
        adapter.setOnRowClickListener((v, position) -> editEntry(position));
        adapter.setOnRowShowMenuListener(
                ExtMenuButton.getPreferredMode(context),
                (v, position) -> {
                    final Menu menu = MenuUtils.createEditDeleteContextMenu(v.getContext());
                    //noinspection DataFlowIssue
                    final MenuMode menuMode = MenuMode.getMode(getActivity(), menu);
                    if (menuMode.isPopup()) {
                        new ExtMenuPopupWindow(v.getContext())
                                .setListener(this::onMenuItemSelected)
                                .setPosition(position)
                                .setMenu(menu, true)
                                .show(v, menuMode);
                    } else {
                        menuLauncher.launch(getActivity(), position, null, null,
                                            menu, true);
                    }
                });

        adapter.registerAdapterDataObserver(adapterDataObserver);
        vb.authorList.setAdapter(adapter);
        vb.authorList.setHasFixedSize(true);
    }

    private void editEntry(final int position) {
        //noinspection DataFlowIssue
        editLauncher.launch(getActivity(), EditAction.Edit, authorList.get(position));
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
            // simply remove and refresh
            authorList.remove(position);
            adapter.notifyItemRemoved(position);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        adapter.unregisterAdapterDataObserver(adapterDataObserver);
        super.onDestroyView();
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            // Fullscreen only;
            // R.id.btn_add
            // R.id.btn_add_details
            onAdd(button.getId() == R.id.btn_add_details);
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     *
     * @param withDetails {@code true} to use the detailed dialog to add an Author
     *                    {@code false} to just add the Author name as-is
     */
    private void onAdd(final boolean withDetails) {
        // clear any previous error
        vb.lblAuthor.setError(null);

        final String name = vb.author.getText().toString().trim();
        if (name.isBlank()) {
            vb.lblAuthor.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Author author = Author.from(name);
        if (withDetails) {
            //noinspection DataFlowIssue
            editLauncher.launch(getActivity(), EditAction.Add, author);
        } else {
            add(author);
        }
    }

    /**
     * Add the given Author to the list, providing it's not already there.
     *
     * @param author to add
     */
    private void add(@NonNull final Author author) {
        // see if it already exists
        //noinspection DataFlowIssue
        vm.fixId(getContext(), author);

        // and check it's not already in the list.
        if (authorList.contains(author)) {
            vb.lblAuthor.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            authorList.add(author);
            adapter.notifyItemInserted(authorList.size() - 1);
            vb.authorList.scrollToPosition(adapter.getItemCount() - 1);

            // clear the form for next entry
            vb.author.setText("");
            vb.author.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!vb.author.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only.
            //noinspection DataFlowIssue
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                vb.author.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        // The list itself is already saved by the adapterDataObserver
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void processChanges(@NonNull final Author original,
                                @NonNull final Author modified) {

        final Context context = getContext();

        // The name was not changed OR
        // the name was modified but not used by any other books.
        //noinspection DataFlowIssue
        if (original.getFamilyName().equals(modified.getFamilyName())
            && original.getGivenNames().equals(modified.getGivenNames())
            || vm.isSingleUsage(context, original)) {

            original.copyFrom(modified, true);
            adapter.notifyDataSetChanged();

        } else {
            // Object was modified and it's used in more than one place.
            // We need to ask the user if they want to make the changes globally.
            StandardDialogs.confirmScopeForChange(
                    context, context.getString(R.string.lbl_author),
                    //TODO: if the names are the same, we should probably state
                    // that some other attribute was changed
                    original.getLabel(context), modified.getLabel(context),
                    () -> changeForAllBooks(original, modified),
                    () -> changeForThisBook(original, modified));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForAllBooks(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // This change is done in the database right NOW!
        try {
            //noinspection DataFlowIssue
            vm.changeForAllBooks(getContext(), original, modified);
            adapter.notifyDataSetChanged();
        } catch (@NonNull final DaoWriteException e) {
            ErrorDialog.show(getContext(), TAG, e);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void changeForThisBook(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // treat the new data as a new Author; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Author. That's ok, it will get
        // garbage collected from the database sooner or later.
        try {
            //noinspection DataFlowIssue
            vm.changeForThisBook(getContext(), original, modified);
            adapter.notifyDataSetChanged();
        } catch (@NonNull final DaoWriteException e) {
            ErrorDialog.show(getContext(), TAG, e);
        }

        //FIXME: updated author(s): Book gets them, but TocEntries remain using old set
        //
        // A TocEntry is unique based on author and title_od.
        // Updating the in-memory TOC list and/or the TocEntries stored in the database
        // with the new author:
        // .
        // The problem is two-fold:
        // If we simply create a new TocEntry?
        // - old one not used anywhere else ? ok, just delete it
        // - old one present in other books ? replace ? leave as-is ?
        // but it's the SAME story (text), now existing with two different authors.
        // - update the TocEntry as-is... i.e. in the database?
        // .. more headaches....
        // .
        // SOLUTION one of:
        // - just ASK the user with a "mod toc" or "no"
        // - don't bother, assume this won't be needed very often
        //   and let the user do it manually
    }

    @Override
    @CallSuper
    public void onDismiss(@NonNull final DialogInterface dialog) {
        // Depending on how we close the dialog, the onscreen keyboard sometimes stays up.
        final View view = getView();
        if (view != null) {
            // dismiss it manually
            hideKeyboard(view);
        }
        super.onDismiss(dialog);
    }

    /**
     * Hide the keyboard.
     *
     * @param v a View from which we can get the window token.
     */
    private void hideKeyboard(@NonNull final View v) {
        new SoftwareKeyboardControllerCompat(v).hide();
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends CheckableDragDropViewHolder
            implements BindableViewHolder<Author> {

        @NonNull
        private final TextView authorView;
        @NonNull
        private final FieldFormatter<Author> formatter;

        Holder(@NonNull final View itemView,
               @NonNull final FieldFormatter<Author> formatter) {
            super(itemView);
            authorView = itemView.findViewById(R.id.row_author);
            this.formatter = formatter;
        }

        @Override
        public void onBind(@NonNull final Author author) {
            formatter.apply(author, authorView);
            final int dc = author.isComplete() ? R.drawable.done_all_24px : 0;
            authorView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, dc, 0);
        }
    }

    private static class AuthorListAdapter
            extends BaseDragDropRecyclerViewAdapter<Author, Holder> {

        @NonNull
        private final FieldFormatter<Author> formatter;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param style             to use
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final Style style,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
            formatter = new EntityFormatter<>(Details.Full, style);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            final Holder holder = new Holder(view, formatter);
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
}
