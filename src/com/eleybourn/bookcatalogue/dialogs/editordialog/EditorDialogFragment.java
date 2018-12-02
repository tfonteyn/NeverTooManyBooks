package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * You can of course only have one Listener implemented on your Fragment, but you can use
 * the {@link #mDestinationFieldId} to determine for which {@link Fields.Field} the result is meant.
 *
 * @param <LT> listener type implemented by the Fragment that called the editor,
 *            e.g. the {@link UniqueId#BKEY_CALLER_ID}
 */
public class EditorDialogFragment<LT> extends DialogFragment {

    @StringRes
    protected int mTitleId;
    @IdRes
    protected int mDestinationFieldId;

    /** the fragment TAG who called us */
    private String mCallerTag;

    /**
     *
     * @return the Fragment that implemented the results listener, casted to that listener type.
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    protected LT getFragmentListener() {
        return (LT) getFragmentManager().findFragmentByTag(mCallerTag);
    }

    public void initStandardArgs(final @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mCallerTag = savedInstanceState.getString(UniqueId.BKEY_CALLER_ID);
            mTitleId = savedInstanceState.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mCallerTag = args.getString(UniqueId.BKEY_CALLER_ID);
            mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putString(UniqueId.BKEY_CALLER_ID, mCallerTag);
        outState.putInt(UniqueId.BKEY_DIALOG_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);

        super.onSaveInstanceState(outState);
    }
}
