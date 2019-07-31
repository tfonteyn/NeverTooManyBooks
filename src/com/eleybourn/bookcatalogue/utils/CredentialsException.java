package com.eleybourn.bookcatalogue.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;

/**
 * Thrown when for some reason a website rejects our requests.
 * This could be due to Authentication and/or Authorization (Goodreads OAuth).
 * Maybe this should be split in two classes.
 *
 * Note that the exception message can/will be shown to the end-user.
 */
public class CredentialsException
        extends Exception
        implements FormattedMessageException {

    private static final long serialVersionUID = 4153245540785393862L;

    /** Args to pass to format function. */
    @StringRes
    private final int mSite;

    /**
     * Constructor.
     *
     * @param site String resource id with the name of the site.
     */
    public CredentialsException(@StringRes final int site) {
        mSite = site;
    }

    /**
     * Constructor.
     *
     * @param site  String resource id with the name of the site.
     * @param cause the cause
     */
    public CredentialsException(@StringRes final int site,
                                @NonNull final Throwable cause) {
        super(cause);
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
        return context.getString(R.string.error_site_authentication_failed, context.getString(mSite));
    }
}
