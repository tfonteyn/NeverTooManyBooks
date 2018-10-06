package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;

import java.util.List;

public interface HandlesISFDB {

    void onGotISFDBEditions(@NonNull final List<String> editions);
    void onGotISFDBBook(@NonNull final Bundle book);

}
