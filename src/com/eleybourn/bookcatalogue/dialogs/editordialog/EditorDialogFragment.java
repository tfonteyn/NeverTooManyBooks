package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.datamanager.Fields;

/**
 * You can only have one Listener implemented on your Fragment, but you can use
 * the {@link #mDestinationFieldId} to determine for which {@link Fields.Field}
 * the result is meant.
 * <p>
 * TODO: an alternative is not to use a listener, but to call {@link #onActivityResult}
 * on the calling fragment.
 *
 * @param <LT> listener type implemented by the Fragment that called the editor,
 *             e.g. the {@link UniqueId#BKEY_CALLER_TAG}
 */
public class EditorDialogFragment<LT>
        extends DialogFragment {

    /** the title for the dialog box. */
    @StringRes
    int mTitleId;
    /** identifier of the field this dialog is bound to. */
    @IdRes
    int mDestinationFieldId;

    /** the fragment TAG who called us. */
    private String mCallerTag;

    /**
     * @return the Fragment that implemented the results listener, casted to that listener type.
     */
    LT getFragmentListener() {
        //noinspection unchecked
        return (LT) requireFragmentManager().findFragmentByTag(mCallerTag);
    }

    void readBaseArgs(@SuppressWarnings("unused") @Nullable final Bundle savedInstanceState) {
        Bundle args = requireArguments();
        mCallerTag = args.getString(UniqueId.BKEY_CALLER_TAG);
        mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
        mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
    }
}
