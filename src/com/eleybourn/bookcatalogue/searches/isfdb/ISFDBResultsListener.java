package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface ISFDBResultsListener {

    void onGotISFDBEditions(@Nullable List<String> editions);

    void onGotISFDBBook(@Nullable Bundle bookData);
}
