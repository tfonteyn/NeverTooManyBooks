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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.tocentry;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.EditTextField;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
class EditTocEntryDelegate
        implements FlexDialogDelegate {

    private final EditTocEntryViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    /** View Binding. */
    private DialogEditBookTocContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    /**
     * Constructor.
     * <p>
     * Class output: {@link EditTocEntryLauncher.Output}.
     *
     * @param owner hosting Fragment
     * @param args all arguments
     */
    EditTocEntryDelegate(@NonNull final DialogFragment owner,
                         @NonNull final EditTocEntryInput args) {
        this.owner = owner;
        requestKey = args.getRequestKey();

        vm = new ViewModelProvider(owner).get(EditTocEntryViewModel.class);
        //noinspection DataFlowIssue
        vm.init(owner.getContext(), args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditBookTocContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_book_toc, container, false);
        vb = DialogEditBookTocContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            final String bookTitle = vm.getBookTitle();
            // Only override the default if we have a book title
            if (bookTitle != null) {
                toolbar.setTitle(bookTitle);
            }
        }

        final Context context = vb.getRoot().getContext();
        final TocEntry currentEdit = vm.getCurrentEdit();

        initTitle(currentEdit);
        initPubYear(currentEdit);

        if (vm.isAnthology()) {
            initAuthor(context);
            vb.lblAuthor.setVisibility(View.VISIBLE);
            vb.author.setVisibility(View.VISIBLE);

            vb.author.requestFocus();

        } else {
            vb.lblAuthor.setVisibility(View.GONE);
            vb.author.setVisibility(View.GONE);

            vb.title.requestFocus();
        }
    }

    private void initTitle(@NonNull final TocEntry tocEntry) {
        //ENHANCE: should we provide a AuthorWorksAdapter to aid manually adding TOC titles?
        // What about the publication year?
        vb.title.setText(tocEntry.getTitle());
        EditTextField.Capitalization.Title.apply(vb.title);
        ExtClearTextEndIconDelegate.attach(vb.lblTitle, null);
        TilUtil.autoRemoveError(vb.title, vb.lblTitle);
    }

    private void initPubYear(@NonNull final TocEntry tocEntry) {
        tocEntry.getFirstPublicationDate().getYear()
                   .ifPresent(integer -> vb.firstPublication.setText(String.valueOf(integer)));
        ExtClearTextEndIconDelegate.attach(vb.lblFirstPublication, null);
    }

    private void initAuthor(@NonNull final Context context) {
        final ExtArrayAdapter<String> authorAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAuthorNames(DBKey.AUTHOR.FORMATTED_FULL_NAME));

        vb.author.setAdapter(authorAdapter);
        vb.author.setText(vm.getCurrentAuthorName());
        vb.author.selectAll();
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        if (vm.getCurrentEdit().getTitle().isEmpty()) {
            vb.lblTitle.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified(context)) {
            return true;
        }

        vm.copyChanges();

        new EditTocEntryLauncher.Output(vm.getOriginal(), vm.getEditPosition())
                .send(owner, requestKey);
        return true;
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        //noinspection DataFlowIssue
        vm.setTitle(vb.title.getText().toString().strip());
        //noinspection DataFlowIssue
        vm.setFirstPublicationDate(vb.firstPublication.getText().toString().strip());

        if (vm.isAnthology()) {
            vm.setCurrentAuthorName(vb.author.getText().toString().strip());
        }
    }
}
