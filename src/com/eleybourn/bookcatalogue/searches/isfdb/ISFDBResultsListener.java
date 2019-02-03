package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;

public interface ISFDBResultsListener {

    void onGotISFDBEditions(@NonNull List<String> editions);

    void onGotISFDBBook(@NonNull Bundle bookData);

}
