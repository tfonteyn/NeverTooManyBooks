package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public interface ISFDBResultsListener {

    void onGotISFDBEditions(@Nullable ArrayList<String> editions);

    void onGotISFDBBook(@Nullable Bundle bookData);
}
