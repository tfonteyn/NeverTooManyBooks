package com.eleybourn.bookcatalogue.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;

/**
 * Note that the exception message can/will be shown to the end-user.
 */
public class AuthorizationException
        extends Exception
        implements FormattedMessageException {

    private static final long serialVersionUID = 4153245540785393862L;

    /** Args to pass to format function. */
    @StringRes
    private final int mSite;

    public AuthorizationException(@StringRes final int site) {
        mSite = site;
    }

    public AuthorizationException(@StringRes final int site,
                                  @NonNull final Throwable inner) {
        super(inner);
        mSite = site;
    }

    /**
     * Use {@link #getFormattedMessage} directly if possible.
     */
    @Override
    public String getLocalizedMessage() {
        return getFormattedMessage(App.getAppContext());
    }

    /**
     * Preferred way of getting the message.
     */
    @NonNull
    @Override
    public String getFormattedMessage(@NonNull final Context context) {
        return context.getString(R.string.error_authorization_failed, context.getString(mSite));
    }
}
