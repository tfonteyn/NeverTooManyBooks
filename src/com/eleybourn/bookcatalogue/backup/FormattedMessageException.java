package com.eleybourn.bookcatalogue.backup;

import android.content.res.Resources;

import androidx.annotation.NonNull;

public interface FormattedMessageException {

    @NonNull
    String getFormattedMessage(@NonNull Resources res);
}
