package com.eleybourn.bookcatalogue.dialogs.entities;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;

/**
 * Dialog to add a new TOCEntry, or edit an existing one.
 * <p>
 * Show with the {@link Fragment#getChildFragmentManager()}
 * <p>
 * Uses {@link Fragment#getParentFragment()} for sending results back.
 */
public class EditTocEntryDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditTocEntryDialogFragment";

    private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";

    private AutoCompleteTextView mAuthorTextView;
    private EditText mTitleTextView;
    private EditText mPubDateTextView;

    private boolean mHasMultipleAuthors;
    private TocEntry mTocEntry;

    private DAO mDb;

    private WeakReference<EditTocEntryResults> mListener;

    /**
     * Constructor.
     *
     * @return the instance
     */
    public static EditTocEntryDialogFragment newInstance(@NonNull final TocEntry tocEntry,
                                                         final boolean hasMultipleAuthors) {
        EditTocEntryDialogFragment frag = new EditTocEntryDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final EditTocEntryResults listener) {
        mListener = new WeakReference<>(listener);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        @SuppressWarnings("ConstantConditions")
        final View root = getActivity().getLayoutInflater()
                                       .inflate(R.layout.dialog_edit_book_toc, null);

        mAuthorTextView = root.findViewById(R.id.author);
        mTitleTextView = root.findViewById(R.id.title);
        mPubDateTextView = root.findViewById(R.id.first_publication);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;

        mTocEntry = args.getParcelable(BKEY_TOC_ENTRY);
        mHasMultipleAuthors = args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);

        mDb = new DAO();

        if (mHasMultipleAuthors) {
            @SuppressWarnings("ConstantConditions")
            ArrayAdapter<String> authorAdapter =
                    new ArrayAdapter<>(getContext(),
                                       android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorsFormattedName());
            mAuthorTextView.setAdapter(authorAdapter);

            mAuthorTextView.setText(mTocEntry.getAuthor().getLabel());
            mAuthorTextView.setVisibility(View.VISIBLE);
        } else {
            mAuthorTextView.setVisibility(View.GONE);
        }

        mTitleTextView.setText(mTocEntry.getTitle());
        mPubDateTextView.setText(mTocEntry.getFirstPublication());

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, this::onConfirm)
                .create();
    }

    @Override
    public void onPause() {
        getFields();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    private void getFields() {
        mTocEntry.setTitle(mTitleTextView.getText().toString().trim());
        mTocEntry.setFirstPublication(mPubDateTextView.getText().toString().trim());
        if (mHasMultipleAuthors) {
            mTocEntry.setAuthor(Author.fromString(mAuthorTextView.getText().toString().trim()));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
        outState.putParcelable(BKEY_TOC_ENTRY, mTocEntry);
    }

    private void onConfirm(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                           @SuppressWarnings("unused") final int which) {
        getFields();
        if (mListener.get() != null) {
            mListener.get().addOrUpdateEntry(mTocEntry);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onConfirm",
                             "WeakReference to listener was dead");
            }
        }
    }

    public interface EditTocEntryResults {

        void addOrUpdateEntry(@NonNull TocEntry tocEntry);
    }
}
