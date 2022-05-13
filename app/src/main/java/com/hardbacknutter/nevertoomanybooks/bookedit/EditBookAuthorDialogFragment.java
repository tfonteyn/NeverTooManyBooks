/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

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
    private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    private EditBookViewModel mVm;

    /** Displayed for info only. */
    @Nullable
    private String mBookTitle;

    /** View Binding. */
    private DialogEditBookAuthorBinding mVb;

    /** The Author we're editing. */
    private Author mAuthor;

    /** Current edit. */
    private Author mCurrentEdit;

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
        mVm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             BKEY_REQUEST_KEY);
        mAuthor = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR),
                                         DBKey.FK_AUTHOR);
        mBookTitle = args.getString(DBKey.TITLE);

        if (savedInstanceState == null) {
            mCurrentEdit = new Author(mAuthor.getFamilyName(),
                                      mAuthor.getGivenNames(),
                                      mAuthor.isComplete());
            mCurrentEdit.setType(mAuthor.getType());
        } else {
            //noinspection ConstantConditions
            mCurrentEdit = savedInstanceState.getParcelable(DBKey.FK_AUTHOR);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditBookAuthorBinding.bind(view);
        mVb.toolbar.setSubtitle(mBookTitle);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAllAuthorFamilyNames());

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAllAuthorGivenNames());

        mVb.familyName.setText(mCurrentEdit.getFamilyName());
        mVb.familyName.setAdapter(familyNameAdapter);
        mVb.givenNames.setText(mCurrentEdit.getGivenNames());
        mVb.givenNames.setAdapter(givenNameAdapter);
        mVb.cbxIsComplete.setChecked(mCurrentEdit.isComplete());

        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        final boolean useAuthorType =
                DBKey.isUsed(global, DBKey.BOOK_AUTHOR_TYPE_BITMASK);
        mVb.authorTypeGroup.setVisibility(useAuthorType ? View.VISIBLE : View.GONE);
        if (useAuthorType) {
            mVb.btnUseAuthorType.setOnCheckedChangeListener(
                    (v, isChecked) -> setTypeEnabled(isChecked));

            // NEWTHINGS: author type: add a button to the layout
            mTypeButtons.put(Author.TYPE_WRITER, mVb.cbxAuthorTypeWriter);
            mTypeButtons.put(Author.TYPE_CONTRIBUTOR, mVb.cbxAuthorTypeContributor);
            mTypeButtons.put(Author.TYPE_INTRODUCTION, mVb.cbxAuthorTypeIntro);
            mTypeButtons.put(Author.TYPE_TRANSLATOR, mVb.cbxAuthorTypeTranslator);
            mTypeButtons.put(Author.TYPE_EDITOR, mVb.cbxAuthorTypeEditor);
            mTypeButtons.put(Author.TYPE_NARRATOR, mVb.cbxAuthorTypeNarrator);

            mTypeButtons.put(Author.TYPE_ARTIST, mVb.cbxAuthorTypeArtist);
            mTypeButtons.put(Author.TYPE_INKING, mVb.cbxAuthorTypeInking);
            mTypeButtons.put(Author.TYPE_COLORIST, mVb.cbxAuthorTypeColorist);

            mTypeButtons.put(Author.TYPE_COVER_ARTIST, mVb.cbxAuthorTypeCoverArtist);
            mTypeButtons.put(Author.TYPE_COVER_INKING, mVb.cbxAuthorTypeCoverInking);
            mTypeButtons.put(Author.TYPE_COVER_COLORIST, mVb.cbxAuthorTypeCoverColorist);

            if (mCurrentEdit.getType() == Author.TYPE_UNKNOWN) {
                setTypeEnabled(false);
            } else {
                setTypeEnabled(true);
                for (int i = 0; i < mTypeButtons.size(); i++) {
                    mTypeButtons.valueAt(i).setChecked((mCurrentEdit.getType()
                                                        & mTypeButtons.keyAt(i)) != 0);
                }
            }
        }
    }

    /**
     * Enable or disable the type related fields.
     *
     * @param enable Flag
     */
    private void setTypeEnabled(final boolean enable) {
        // don't bother changing the 'checked' status, we'll ignore them anyhow.
        // and this is more user friendly if they flip the switch more than once.
        mVb.btnUseAuthorType.setChecked(enable);
        for (int i = 0; i < mTypeButtons.size(); i++) {
            mTypeButtons.valueAt(i).setEnabled(enable);
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    protected boolean saveChanges() {
        viewToModel();

        // basic check only, we're doing more extensive checks later on.
        if (mCurrentEdit.getFamilyName().isEmpty()) {
            showError(mVb.lblFamilyName, R.string.vldt_non_blank_required);
            return false;
        }

        // invalidate the type if needed
        if (!mVb.btnUseAuthorType.isChecked()) {
            mCurrentEdit.setType(Author.TYPE_UNKNOWN);
        }

        Launcher.setResult(this, mRequestKey, mAuthor, mCurrentEdit);
        return true;
    }

    private void viewToModel() {
        mCurrentEdit.setName(mVb.familyName.getText().toString().trim(),
                             mVb.givenNames.getText().toString().trim());
        mCurrentEdit.setComplete(mVb.cbxIsComplete.isChecked());

        int type = Author.TYPE_UNKNOWN;
        for (int i = 0; i < mTypeButtons.size(); i++) {
            if (mTypeButtons.valueAt(i).isChecked()) {
                type |= mTypeButtons.keyAt(i);
            }
        }
        mCurrentEdit.setType(type);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_AUTHOR, mCurrentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private static final String ORIGINAL = "original";
        private static final String MODIFIED = "modified";
        private String mRequestKey;
        private FragmentManager mFragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final Author original,
                              @NonNull final Author modified) {
            final Bundle result = new Bundle(2);
            result.putParcelable(ORIGINAL, original);
            result.putParcelable(MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            mFragmentManager = fragmentManager;
            mRequestKey = requestKey;
            mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param bookTitle displayed for info only
         * @param author    to edit
         */
        void launch(@NonNull final String bookTitle,
                    @NonNull final Author author) {
            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(DBKey.TITLE, bookTitle);
            args.putParcelable(DBKey.FK_AUTHOR, author);

            final DialogFragment frag = new EditBookAuthorDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(ORIGINAL), ORIGINAL),
                     Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
        }

        /**
         * Callback handler.
         *
         * @param original the original item
         * @param modified the modified item
         */
        public abstract void onResult(@NonNull Author original,
                                      @NonNull Author modified);
    }
}
