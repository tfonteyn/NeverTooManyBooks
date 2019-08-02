package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public interface IsfdbResultsListener {

    void onGotISFDBBook(@Nullable Bundle bookData);

    void onGotISFDBEditions(@Nullable ArrayList<Editions.Edition> editions);
}
