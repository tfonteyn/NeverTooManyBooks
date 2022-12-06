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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtTextWatcher;

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
    private static final String SIS_REAL_AUTHOR_NAME = TAG + ":ran";

    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /**
     * We create a list of all the Type checkboxes for easy handling.
     * The key is the Type.
     */
    private final SparseArray<CompoundButton> typeButtons = new SparseArray<>();

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** View model. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookAuthorBinding vb;

    /** Displayed for info only. */
    @Nullable
    private String bookTitle;

    /** The Author we're editing. */
    private Author author;
    /** Current edit. */
    private Author currentEdit;
    /**
     * The 'currentEdit' does not hold the real-author-id during the edit-phase.
     * We just keep the name until the user clicks "save".
     */
    @Nullable
    private String currentRealAuthorName;

    /** Adding or Editing. */
    private EditAction action;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookAuthorDialogFragment() {
        super(R.layout.dialog_edit_book_author);
        setForceFullscreen();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);
        author = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR), DBKey.FK_AUTHOR);
        bookTitle = args.getString(DBKey.TITLE);

        if (savedInstanceState == null) {
            currentEdit = new Author(author.getFamilyName(),
                                     author.getGivenNames(),
                                     author.isComplete());
            currentEdit.setType(author.getType());

            if (author.getRealAuthorId() != 0) {
                final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();
                final Author realAuthor = authorDao.getById(author.getRealAuthorId());
                // this should never be null... flw
                if (realAuthor != null) {
                    currentRealAuthorName = realAuthor.getFormattedName(false);
                }
            }
        } else {
            //noinspection ConstantConditions
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_AUTHOR);
            currentRealAuthorName = savedInstanceState.getString(SIS_REAL_AUTHOR_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookAuthorBinding.bind(view);
        vb.toolbar.setSubtitle(bookTitle);

        final Context context = getContext();
        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAllAuthorFamilyNames());

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                vm.getAllAuthorGivenNames());

        vb.familyName.setText(currentEdit.getFamilyName());
        vb.familyName.setAdapter(familyNameAdapter);
        vb.givenNames.setText(currentEdit.getGivenNames());
        vb.givenNames.setAdapter(givenNameAdapter);
        vb.cbxIsComplete.setChecked(currentEdit.isComplete());
        vb.realAuthor.setText(currentRealAuthorName, false);

        final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_FORMATTED));
        vb.realAuthor.setAdapter(realNameAdapter);
        vb.realAuthor.addTextChangedListener((ExtTextWatcher) s -> vb.lblRealAuthor.setError(null));
        vb.realAuthor.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                vb.lblRealAuthor.setError(null);
            }
        });

        final boolean useAuthorType = GlobalFieldVisibility.isUsed(DBKey.AUTHOR_TYPE__BITMASK);
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

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_save) {
            return buttonPanel.findViewById(R.id.btn_positive);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    protected boolean saveChanges() {
        viewToModel();

        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            showError(vb.lblFamilyName, R.string.vldt_non_blank_required);
            return false;
        }

        final Context context = getContext();
        final AuthorDao dao = ServiceLocator.getInstance().getAuthorDao();
        //URGENT: this should be real book locale!
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        // If we have a pseudonym set, it must be a valid/existing author.
        if (currentRealAuthorName != null && !currentRealAuthorName.isBlank()) {
            final Author realAuthor = Author.from(currentRealAuthorName);
            //noinspection ConstantConditions
            dao.fixId(context, realAuthor, false, bookLocale);
            if (realAuthor.getId() == 0) {
                vb.lblRealAuthor.setError(getString(R.string.err_real_author_must_be_valid));
                return false;
            }
            currentEdit.setRealAuthorId(realAuthor.getId());
        }

        // invalidate the type if needed
        if (!vb.btnUseAuthorType.isChecked()) {
            currentEdit.setType(Author.TYPE_UNKNOWN);
        }

        if (action == EditAction.Add) {
            Launcher.setResult(this, requestKey, currentEdit);
        } else {
            Launcher.setResult(this, requestKey, author, currentEdit);
        }
        return true;
    }

    private void viewToModel() {
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        currentRealAuthorName = vb.realAuthor.getText().toString().trim();

        int type = Author.TYPE_UNKNOWN;
        for (int i = 0; i < typeButtons.size(); i++) {
            if (typeButtons.valueAt(i).isChecked()) {
                type |= typeButtons.keyAt(i);
            }
        }
        currentEdit.setType(type);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_AUTHOR, currentEdit);
        outState.putString(SIS_REAL_AUTHOR_NAME, currentRealAuthorName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            extends EditLauncher<Author> {

        @Override
        public void launch(@NonNull final String bookTitle,
                           @NonNull final EditAction action,
                           @NonNull final Author author) {
            super.launch(new EditBookAuthorDialogFragment(),
                         bookTitle, action, DBKey.FK_AUTHOR, author);
        }
    }
}
