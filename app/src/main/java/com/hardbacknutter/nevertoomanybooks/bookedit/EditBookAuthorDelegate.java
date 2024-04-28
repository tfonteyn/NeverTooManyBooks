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

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Add/Edit a single Author from the book's author list.
 * <p>
 * Can already exist (i.e. have an id) or can be a previously added/new one (id==0).
 * <p>
 * {@link EditAction#Add}:
 * <ul>
 * <li>Used for list-dialogs needing to add a NEW item to the list</li>
 * <li>The new item is NOT stored in the database</li>
 * <li>Returns the new item</li>
 * </ul>
 * <p>
 * {@link EditAction#Edit}:
 * <ul>
 * <li>Used for list-dialogs needing to EDIT an existing item in the list</li>
 * <li>The modifications are NOT stored in the database</li>
 * <li>Returns the original untouched + a new copy with the modifications</li>
 * </ul>
 */
public class EditBookAuthorDelegate
        implements FlexDialog {

    private static final String TAG = "EditBookAuthorDelegate";

    /**
     * We create a list of all the Type checkboxes for easy handling.
     * The key is the Type.
     */
    private final SparseArray<CompoundButton> typeButtons = new SparseArray<>();

    @NonNull
    private final DialogFragment owner;

    /** Book View model. Must be in the Activity scope. */
    private final EditBookViewModel vm;
    /** Author View model. Fragment scope. */
    private final EditAuthorViewModel authorVm;

    /** View Binding. */
    private DialogEditBookAuthorContentBinding vb;

    /** Adding or Editing. */
    private final EditAction action;

    EditBookAuthorDelegate(@NonNull final DialogFragment owner,
                           @NonNull final Bundle args) {

        this.owner = owner;
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(owner.getActivity()).get(EditBookViewModel.class);
        authorVm = new ViewModelProvider(owner).get(EditAuthorViewModel.class);
        authorVm.init(args);
    }

    void onViewCreated(@NonNull final DialogEditBookAuthorContentBinding vb) {
        this.vb = vb;

        final Context context = vb.getRoot().getContext();

        final Author currentEdit = authorVm.getCurrentEdit();

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

        setupRealAuthorField(context);
        setupAuthorTypeField(currentEdit.getType());

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.familyName.requestFocus();
    }

    @Nullable
    String getToolbarSubtitle() {
        return vm.getBook().getTitle();
    }

    private void setupRealAuthorField(final Context context) {
        if (authorVm.useRealAuthorName()) {
            vb.lblRealAuthorHeader.setVisibility(View.VISIBLE);
            vb.lblRealAuthor.setVisibility(View.VISIBLE);

            final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                    context, R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    vm.getAllAuthorNames());
            vb.realAuthor.setText(authorVm.getCurrentRealAuthorName(), false);
            vb.realAuthor.setAdapter(realNameAdapter);
            autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        } else {
            vb.lblRealAuthorHeader.setVisibility(View.GONE);
            vb.lblRealAuthor.setVisibility(View.GONE);
        }
    }

    private void setupAuthorTypeField(@Author.Type final int currentType) {
        if (authorVm.useAuthorType()) {
            vb.btnUseAuthorType.setVisibility(View.VISIBLE);
            vb.btnUseAuthorType.setOnCheckedChangeListener((v, isChecked) -> {
                setTypeEnabled(isChecked);
                vb.authorTypeGroup.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            createTypeButtonList();

            if (currentType == Author.TYPE_UNKNOWN) {
                setTypeEnabled(false);
                vb.authorTypeGroup.setVisibility(View.GONE);
            } else {
                setTypeEnabled(true);
                vb.authorTypeGroup.setVisibility(View.VISIBLE);
                for (int i = 0; i < typeButtons.size(); i++) {
                    typeButtons.valueAt(i).setChecked((currentType & typeButtons.keyAt(i)) != 0);
                }
            }
        } else {
            vb.btnUseAuthorType.setVisibility(View.GONE);
            vb.authorTypeGroup.setVisibility(View.GONE);
        }
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
        typeButtons.put(Author.TYPE_STORYBOARD, vb.cbxAuthorTypeStoryboard);
        typeButtons.put(Author.TYPE_LETTERING, vb.cbxAuthorTypeLettering);

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
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges(false)) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    protected boolean saveChanges(final boolean createRealAuthorIfNeeded) {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Author currentEdit = authorVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            vb.lblFamilyName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // invalidate the type if needed
        if (!vb.btnUseAuthorType.isChecked()) {
            currentEdit.setType(Author.TYPE_UNKNOWN);
        }


        final Locale locale = ServiceLocator
                .getInstance().getLanguages()
                .toLocale(context, vm.getBook().getString(DBKey.LANGUAGE));

        // We let this call go ahead even if real-author is switched off by the user
        // so we can clean up as needed.
        if (!authorVm.validateAndSetRealAuthor(context, locale, createRealAuthorIfNeeded)) {
            warnThatRealAuthorMustBeValid(context);
            return false;
        }

        EditParcelableLauncher
                .setResult(owner, authorVm.getRequestKey(), action,
                           authorVm.getAuthor(), currentEdit);
        return true;
    }

    private void warnThatRealAuthorMustBeValid(@NonNull final Context context) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.vldt_real_author_must_be_valid)
                .setMessage(context.getString(R.string.confirm_create_real_author,
                                              authorVm.getCurrentRealAuthorName()))
                .setNegativeButton(R.string.action_edit, (d, w) -> vb.lblRealAuthor.setError(
                        context.getString(R.string.vldt_real_author_must_be_valid)))
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    if (saveChanges(true)) {
                        // finish the DialogFragment
                        owner.dismiss();
                    }
                })
                .create()
                .show();
    }

    void viewToModel() {
        final Author currentEdit = authorVm.getCurrentEdit();
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        if (authorVm.useRealAuthorName()) {
            authorVm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());
        }

        if (authorVm.useAuthorType()) {
            int type = Author.TYPE_UNKNOWN;
            for (int i = 0; i < typeButtons.size(); i++) {
                if (typeButtons.valueAt(i).isChecked()) {
                    type |= typeButtons.keyAt(i);
                }
            }
            currentEdit.setType(type);
        }
    }

}
