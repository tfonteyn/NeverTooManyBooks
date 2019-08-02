package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;

public interface FormattedMessageException {

    @NonNull
    String getFormattedMessage(@NonNull Context context);
}
