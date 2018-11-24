package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;
import android.support.annotation.NonNull;

import java.util.List;

public interface ISFDBResultsListener {

    void onGotISFDBEditions(final @NonNull List<String> editions);
    void onGotISFDBBook(final @NonNull Bundle bookData);

}
