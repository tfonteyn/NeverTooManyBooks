package com.eleybourn.bookcatalogue.utils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;

/**
 * Note that the exception message can/will be shown to the end-user.
 */
public class AuthorizationException
        extends Exception {

    private static final long serialVersionUID = 1029975248988349668L;

    public AuthorizationException(@StringRes final int site) {
        super(App.getAppContext().getString(R.string.error_authorization_failed,
                                            App.getAppContext().getString(site)), null);
    }

    public AuthorizationException(@StringRes final int site,
                                  @NonNull final Throwable inner) {
        super(App.getAppContext().getString(R.string.error_authorization_failed,
                                            App.getAppContext().getString(site)), inner);
    }
}
