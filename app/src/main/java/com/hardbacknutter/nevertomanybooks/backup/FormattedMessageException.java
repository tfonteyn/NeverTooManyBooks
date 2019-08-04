package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.App;

public interface FormattedMessageException {

    /**
     * Use {@link #getFormattedMessage} directly if possible.
     */
    default String getLocalizedMessage() {
        //TODO: should be using a user context.
        Context context = App.getAppContext();
        return getFormattedMessage(context);
    }

    @NonNull
    String getFormattedMessage(@NonNull Context context);
}
