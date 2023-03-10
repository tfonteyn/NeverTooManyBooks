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

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ExtArrayAdapter;

/**
 * Edit a single Author from the book's author list.
 * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
 * <p>
 * Must be a public static class to be properly recreated from instance state.
 */
public class EditBookAuthorDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditAuthorForBookDialog";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /**
     * We create a list of all the Type checkboxes for easy handling.
     * The key is the Type.
     */
    private final SparseArray<CompoundButton> typeButtons = new SparseArray<>();

    /** Book View model. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** Author View model. Fragment scope. */
    private EditAuthorViewModel authorVm;

    /** View Binding. */
    private DialogEditBookAuthorContentBinding vb;

    /** Adding or Editing. */
    private EditAction action;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookAuthorDialogFragment() {
        super(R.layout.dialog_edit_book_author, R.layout.dialog_edit_book_author_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
        authorVm = new ViewModelProvider(this).get(EditAuthorViewModel.class);
        authorVm.init(requireArguments());

        final Bundle args = requireArguments();
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookAuthorContentBinding.bind(view.findViewById(R.id.dialog_content));
        // always fullscreen; title is fixed, no buttonPanel
        setSubtitle(vm.getBook().getTitle());

        final Context context = getContext();
        final Author currentEdit = authorVm.getCurrentEdit();

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAllAuthorFamilyNames());
        vb.familyName.setText(currentEdit.getFamilyName());
        vb.familyName.setAdapter(familyNameAdapter);
        autoRemoveError(vb.familyName, vb.lblFamilyName);

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAllAuthorGivenNames());
        vb.givenNames.setText(currentEdit.getGivenNames());
        vb.givenNames.setAdapter(givenNameAdapter);

        final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAllAuthorNames());
        vb.realAuthor.setText(authorVm.getCurrentRealAuthorName(), false);
        vb.realAuthor.setAdapter(realNameAdapter);
        autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        final boolean useAuthorType = GlobalFieldVisibility
                .isUsed(context, DBKey.AUTHOR_TYPE__BITMASK);
        if (useAuthorType) {
            vb.btnUseAuthorType.setVisibility(View.VISIBLE);
            vb.btnUseAuthorType.setOnCheckedChangeListener((v, isChecked) -> {
                setTypeEnabled(isChecked);
                vb.authorTypeGroup.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            createTypeButtonList();

            if (currentEdit.getType() == Author.TYPE_UNKNOWN) {
                setTypeEnabled(false);
                vb.authorTypeGroup.setVisibility(View.GONE);
            } else {
                setTypeEnabled(true);
                vb.authorTypeGroup.setVisibility(View.VISIBLE);
                for (int i = 0; i < typeButtons.size(); i++) {
                    typeButtons.valueAt(i).setChecked((currentEdit.getType()
                                                       & typeButtons.keyAt(i)) != 0);
                }
            }
        } else {
            vb.btnUseAuthorType.setVisibility(View.GONE);
            vb.authorTypeGroup.setVisibility(View.GONE);
        }

        vb.familyName.requestFocus();
    }

    private void createTypeButtonList() {
        // NEWTHINGS: author type: add a button to the layout
        typeButtons.put(Author.TYPE_WRITER, vb.cbxAuthorTypeWriter);
        typeButtons.put(Author.TYPE_CONTRIBUTOR, vb.cbxAuthorTypeContributor);
        typeButtons.put(Author.TYPE_INTRODUCTION, vb.cbxAuthorTypeIntro);
        typeButtons.put(Author.TYPE_TRANSLATOR, vb.cbxAuthorTypeTranslator);
        typeButtons.put(Author.TYPE_EDITOR, vb.cbxAuthorTypeEditor);
        typeButtons.put(Author.TYPE_NARRATOR, vb.cbxAuthorTypeNarrator);

        typeButtons.put(Author.TYPE_ARTIST, vb.cbxAuthorTypeArtist);
        typeButtons.put(Author.TYPE_INKING, vb.cbxAuthorTypeInking);
        typeButtons.put(Author.TYPE_COLORIST, vb.cbxAuthorTypeColorist);

        typeButtons.put(Author.TYPE_COVER_ARTIST, vb.cbxAuthorTypeCoverArtist);
        typeButtons.put(Author.TYPE_COVER_INKING, vb.cbxAuthorTypeCoverInking);
        typeButtons.put(Author.TYPE_COVER_COLORIST, vb.cbxAuthorTypeCoverColorist);
    }

    /**
     * Enable or disable the type related fields.
     *
     * @param enable Flag
     */
    private void setTypeEnabled(final boolean enable) {
        // don't bother changing the 'checked' status, we'll ignore them anyhow.
        // and this is more user friendly if they flip the switch more than once.
        vb.btnUseAuthorType.setChecked(enable);
        for (int i = 0; i < typeButtons.size(); i++) {
            typeButtons.valueAt(i).setEnabled(enable);
        }
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges(false)) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    protected boolean saveChanges(final boolean createRealAuthorIfNeeded) {
        viewToModel();

        final Author currentEdit = authorVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            vb.lblFamilyName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // invalidate the type if needed
        if (!vb.btnUseAuthorType.isChecked()) {
            currentEdit.setType(Author.TYPE_UNKNOWN);
        }

        final Context context = getContext();
        //noinspection ConstantConditions
        final Locale bookLocale = ServiceLocator
                .getInstance().getLanguages()
                .toLocale(context, vm.getBook().getString(DBKey.LANGUAGE));

        if (!authorVm.validateAndSetRealAuthor(context, bookLocale, createRealAuthorIfNeeded)) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.err_real_author_must_be_valid)
                    .setMessage(context.getString(R.string.confirm_create_real_author,
                                                  authorVm.getCurrentRealAuthorName()))
                    .setNegativeButton(R.string.action_edit, (d, w) -> vb.lblRealAuthor.setError(
                            getString(R.string.err_real_author_must_be_valid)))
                    .setPositiveButton(R.string.action_create, (d, w) -> {
                        if (saveChanges(true)) {
                            // finish the DialogFragment
                            dismiss();
                        }
                    })
                    .create()
                    .show();
            return false;
        }

        if (action == EditAction.Add) {
            Launcher.setResult(this, authorVm.getRequestKey(),
                               currentEdit);
        } else {
            Launcher.setResult(this, authorVm.getRequestKey(),
                               authorVm.getAuthor(), currentEdit);
        }
        return true;
    }

    private void viewToModel() {
        final Author currentEdit = authorVm.getCurrentEdit();
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        authorVm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());

        int type = Author.TYPE_UNKNOWN;
        for (int i = 0; i < typeButtons.size(); i++) {
            if (typeButtons.valueAt(i).isChecked()) {
                type |= typeButtons.keyAt(i);
            }
        }
        currentEdit.setType(type);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            extends EditLauncher<Author> {

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKeyValue,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            super.registerForFragmentResult(fragmentManager,
                                            BKEY_REQUEST_KEY,
                                            requestKeyValue,
                                            lifecycleOwner);
        }

        @Override
        public void launch(@NonNull final EditAction action,
                           @NonNull final Author author) {
            super.launch(new EditBookAuthorDialogFragment(),
                         action, DBKey.FK_AUTHOR, author);
        }
    }
}
