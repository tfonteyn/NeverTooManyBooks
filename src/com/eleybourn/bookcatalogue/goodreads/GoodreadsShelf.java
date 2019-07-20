package com.eleybourn.bookcatalogue.goodreads;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.goodreads.api.ShelvesListApiHandler;

public class GoodreadsShelf {

    @NonNull
    private final Bundle mBundle;

    public GoodreadsShelf(@NonNull final Bundle bundle) {
        mBundle = bundle;
    }

    @NonNull
    public String getName() {
        //noinspection ConstantConditions
        return mBundle.getString(ShelvesListApiHandler.ShelvesField.NAME);
    }

    boolean isExclusive() {
        return mBundle.getBoolean(ShelvesListApiHandler.ShelvesField.EXCLUSIVE);
    }
}
