package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Importing data can give a more detailed reason of failure.
 * String ID and args are stored for later retrieval.
 * The messages will be shown to the user, hence the need for a String resource.
 */
public class ImportException
        extends Exception
        implements FormattedMessageException {

    private static final long serialVersionUID = 5475630910712428400L;

    @StringRes
    private final int mStringId;
    /** Args to pass to format function. */
    @NonNull
    private final Object[] mArgs;

    public ImportException(@StringRes final int stringId,
                           @NonNull final Object... args) {
        mStringId = stringId;
        mArgs = args;
    }

    @NonNull
    @Override
    public String getFormattedMessage(@NonNull final Context context) {
        return context.getString(mStringId, mArgs);
    }
}
