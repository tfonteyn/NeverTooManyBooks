/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #activeTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#getEngineId()} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements Cancellable {

    private static final String ERROR_UNKNOWN_TASK = "Unknown task=";

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";
    /** The data returned from the search can contain this key with error messages. */
    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    private static final AtomicInteger TASK_ID = new AtomicInteger();

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    protected final MutableLiveData<LiveDataEvent<TaskProgress>>
            searchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorFinished = new MutableLiveData<>();


    /**
     * List of Tasks being managed by *this* object.
     * key: taskId
     */
    private final Map<Integer, SearchTask> activeTasks = new HashMap<>();

    /** Flag indicating we're shutting down. */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, SearchResult> resultsByEngineId = new EnumMap<>(EngineId.class);

    /** Accumulates any errors from <strong>individual</strong> search tasks. */
    private final Map<EngineId, Throwable> errorsByEngineId = new EnumMap<>(EngineId.class);

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, TaskProgress> progressByEngineId = new EnumMap<>(EngineId.class);

    /** Caches all created engines for reuse. */
    private final Map<EngineId, SearchEngine> engineCache = new EnumMap<>(EngineId.class);
    // There is a SINGLE/shared listener for ALL tasks!
    private final TaskListener<Book> searchTaskListener = new SearchTaskListener();
    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both active and disabled sites.
     */
    private List<Site> allSites;
    /** Base message for progress updates. */
    @Nullable
    private String baseMessage;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean waitingForIsbnOrCode;
    /** Original ISBN text for search. */
    @NonNull
    private String isbnSearchText = "";
    /** {@code true} for strict ISBN checking, {@code false} for allowing generic codes. */
    private boolean strictIsbn = true;
    /**
     * Created by {@link #prepareSearch()} from {@link #isbnSearchText}.
     * NonNull afterwards.
     */
    private ISBN isbn;
    /** Site external id for search. */
    @Nullable
    private Map<EngineId, String> externalIdSearchText;
    /** Original author for search. */
    @NonNull
    private String authorSearchText = "";
    /** Original title for search. */
    @NonNull
    private String titleSearchText = "";
    /** Original publisher for search. */
    @NonNull
    private String publisherSearchText = "";
    /** Whether of not to fetch thumbnails. */
    @Nullable
    private boolean[] fetchCover;

    /** DEBUG timer. */
    private long searchStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksEndTime;

    /** Cached string resource. */
    private String listElementPrefixString;

    private ResultsAccumulator resultsAccumulator;

    /**
     * Process the message and start another task if required.
     *
     * @param taskId of task; this is the engine id.
     * @param result of a search;
     *               Will never be {@code null} for successful searches.
     *               MAY be {@code null} for cancelled searches.
     *               WILL be {@code null} for failed searches.
     */
    private synchronized void onSearchTaskFinished(final int taskId,
                                                   @Nullable final Book result) {

        final SearchTask searchTask;

        // Remove the finished task from our list
        synchronized (activeTasks) {
            searchTask = activeTasks.remove(taskId);
        }
        Objects.requireNonNull(searchTask, () -> ERROR_UNKNOWN_TASK + taskId);

        final EngineId engineId = searchTask.getSearchEngine().getEngineId();

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.SEARCH_COORDINATOR
                                  || DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS)) {
            debugEnteredOnSearchTaskFinished(engineId);
        }


        // ALWAYS store, even when null!
        // Presence of the site/task id in the map is an indication that the site was processed
        synchronized (resultsByEngineId) {
            resultsByEngineId.put(engineId, new SearchResult(engineId, searchTask.getSearchBy(),
                                                             result));
        }

        // clear obsolete progress status
        synchronized (progressByEngineId) {
            progressByEngineId.remove(engineId);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Start new search(es) as needed/allowed.
        boolean searchStarted = false;
        if (!cancelRequested.get()) {
            //  update our listener with the current progress status
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
            }

            if (waitingForIsbnOrCode) {
                if (result != null && result.hasIsbn()) {
                    waitingForIsbnOrCode = false;
                    // Replace the search text with the (we hope) exact ISBN/code
                    // Worst case, explicitly use an empty string
                    //noinspection DataFlowIssue
                    isbnSearchText = result.getString(DBKey.BOOK_ISBN, "");

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                        LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                                    "waitingForExactCode",
                                                    "isbn=" + isbnSearchText);
                    }

                    // Start the remaining searches, even if they have run before.
                    // They will redo the search WITH the ISBN/code.
                    searchStarted = startSearch(context);
                } else {
                    // sequentially start the next search which has not run yet.
                    searchStarted = startNextSearch(context);
                }
            }
        }

        // any searches still running or did we get cancelled?
        final boolean stopSearching;
        synchronized (activeTasks) {
            // if we didn't start a new search (which might not be active yet!),
            // and there are no previous searches still running
            // (or we got cancelled) then we are done.
            stopSearching = !searchStarted && (activeTasks.isEmpty() || cancelRequested.get());
        }

        if (stopSearching) {
            final long processTime = System.nanoTime();

            final Book book = accumulateResults(context);
            final String searchErrors = accumulateErrors(context);
            if (searchErrors != null && !searchErrors.isEmpty()) {
                book.putString(BKEY_SEARCH_ERROR, searchErrors);
            }


            final LiveDataEvent<Book> message = LiveDataEvent.of(book);
            if (cancelRequested.get()) {
                searchCoordinatorCancelled.setValue(message);
            } else {
                searchCoordinatorFinished.setValue(message);
            }

            if (BuildConfig.DEBUG && (DEBUG_SWITCHES.SEARCH_COORDINATOR
                                      || DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS)) {
                debugExitOnSearchTaskFinished(context, processTime, searchErrors);
            }
        }
    }

    private synchronized void onSearchTaskFailed(final int taskId,
                                                 @Nullable final Throwable e) {
        synchronized (errorsByEngineId) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(taskId),
                                                  () -> ERROR_UNKNOWN_TASK + taskId)
                                  .getSearchEngine().getEngineId();
            }
            // Always store, even if the Exception is null
            errorsByEngineId.put(engineId, e);
        }
        onSearchTaskFinished(taskId, null);
    }

    private synchronized void onSearchProgress(@NonNull final TaskProgress message) {
        synchronized (progressByEngineId) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(message.taskId),
                                                  () -> ERROR_UNKNOWN_TASK + message.taskId)
                                  .getSearchEngine().getEngineId();
            }
            progressByEngineId.put(engineId, message);
        }
        // forward the accumulated progress
        synchronized (searchCoordinatorProgress) {
            searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
        }
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return searchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     *
     * @return book data
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchFinished() {
        return searchCoordinatorFinished;
    }

    /**
     * The result if the user cancelled the search.
     *
     * @return book data found so far
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchCancelled() {
        return searchCoordinatorCancelled;
    }

    /**
     * Cancel all searches.
     */
    public void cancel() {
        cancelRequested.set(true);
        synchronized (activeTasks) {
            activeTasks.values().forEach(SearchTask::cancel);
        }
    }

    @Override
    protected void onCleared() {
        cancel();
    }

    public void cancelTask(@SuppressWarnings("unused") @IdRes final int taskId) {
        // reminder: this object, the SearchCoordinator is a pseudo task
        // we're only using "cancelTask" to conform with other usage
        cancel();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (resultsAccumulator == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                searchTasksStartTime = new EnumMap<>(EngineId.class);
                searchTasksEndTime = new EnumMap<>(EngineId.class);
            }

            allSites = Site.Type.Data.getSites();

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            final Locale systemLocale = serviceLocator.getSystemLocaleList().get(0);

            resultsAccumulator = new ResultsAccumulator(context, systemLocale,
                                                        serviceLocator::getLanguages);

            listElementPrefixString = context.getString(R.string.list_element);

            if (args != null) {
                fetchCover = new boolean[]{
                        serviceLocator.isFieldEnabled(DBKey.COVER[0]),
                        serviceLocator.isFieldEnabled(DBKey.COVER[1])
                };

                isbnSearchText = args.getString(DBKey.BOOK_ISBN, "");

                titleSearchText = args.getString(DBKey.TITLE, "");

                authorSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, "");

                publisherSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, "");
            }
        }
    }

    /**
     * Called when all is said and done. Accumulate data from {@link #allSites}.
     *
     * @param context Current context
     *
     * @return the accumulated book data bundle
     */
    @NonNull
    private Book accumulateResults(@NonNull final Context context) {

        final Book book = new Book();
        final List<EngineId> sitesInOrder;

        // Determine the set of sites for which we have results in the order the search completed
        final Set<EngineId> completedOrder = determineCompletedOrder();

        // Now convert the 'completed' order to the 'best' order
        if (isbn.isValid(strictIsbn)) {
            // When searching by ISBN, determine the best order use the site-data found.
            sitesInOrder = determineBestOrder(completedOrder);
            // Add the ISBN we initially searched for.
            // This avoids overwriting with a potentially different isbn from the sites
            book.putString(DBKey.BOOK_ISBN, isbnSearchText);
        } else {
            // We did not have an ISBN as a search criteria; use the default order
            sitesInOrder = new ArrayList<>(completedOrder);
        }

        // Filter the results so we end up with the non-null and 'present' results only,
        // and convert the list to an ORDERED map with SearchEngine/Data pairs
        //noinspection DataFlowIssue
        final List<Pair<Locale, Book>> results = sitesInOrder
                .stream()
                .map(resultsByEngineId::get)
                .filter(Objects::nonNull)
                .filter(result -> result.getResult().isPresent())
                .map(searchResult -> new Pair<>(
                        engineCache.get(searchResult.getEngineId()).getLocale(context),
                        searchResult.getResult().get()))
                .collect(Collectors.toList());

        // Merge the data we have in the order as decided upon above.
        // no synchronized needed, at this point all other threads have finished.
        resultsAccumulator.process(context, results, book);

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbnStr = book.getString(DBKey.BOOK_ISBN, null);
        if (isbnStr == null || isbnStr.isEmpty()) {
            book.putString(DBKey.BOOK_ISBN, isbnSearchText);
        }

        // If we did not get a title, use the one we originally searched for.
        final String title = book.getString(DBKey.TITLE, null);
        if (title == null || title.isEmpty()) {
            book.putString(DBKey.TITLE, titleSearchText);
        }

        return book;
    }

    /**
     * Determine the set of sites for which we have results.
     *
     * @return sites ordered by completion time.
     */
    @NonNull
    private Set<EngineId> determineCompletedOrder() {
        final Set<EngineId> completedOrder = allSites
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2023-04-21: bugfix: We MUST merge with engine keys for which results were found.
        // We need to do this when the user was for example searching by native-web-id
        // on a site which was NOT on the active list.
        // We use this somewhat convoluted method to make sure the priority order is kept.
        // Keep in mind that the results are NOT ordered by 'best order' YET, but in the order
        // in which the engines completed the searches.
        completedOrder.addAll(resultsByEngineId.keySet());
        return completedOrder;
    }


    /**
     * Determine the order in which to apply the results from the list of sites.
     * <p>
     * The order will be based on the site order as set by the user AND the actual results.
     *
     * @param activeEngines all engines we searched
     *
     * @return the list of sites in the 'best' order for further processing
     */
    @NonNull
    private List<EngineId> determineBestOrder(@NonNull final Set<EngineId> activeEngines) {
        final List<EngineId> sitesInOrder = new ArrayList<>();
        final Collection<EngineId> sitesWithoutIsbn = new ArrayList<>();

        activeEngines.forEach(engineId -> {
            // no synchronized needed, at this point all other threads have finished.
            final SearchResult siteData = resultsByEngineId.get(engineId);
            if (siteData != null) {
                siteData.getResult().ifPresent(result -> {

                    if (siteData.getSearchBy() == SearchEngine.SearchBy.ExternalId
                        && !strictIsbn) {
                        // We searched by website id and didn't insist on an exact ISBN
                        // so we SHOULD be pretty sure about the data...
                        sitesInOrder.add(engineId);

                    } else if (result.contains(DBKey.BOOK_ISBN)) {
                        // We did a general search with an ISBN; check if it matches
                        final String isbnFound = result.getString(DBKey.BOOK_ISBN, null);
                        if (isbnFound != null && !isbnFound.isEmpty()
                            && isbn.equals(new ISBN(isbnFound, strictIsbn))) {
                            sitesInOrder.add(engineId);
                        } else {
                            // The ISBN found does not match the ISBN we searched for;
                            // 2023-05-30: don't just skip; add it to the less reliables
                            sitesWithoutIsbn.add(engineId);
                        }

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                            LoggerFactory.getLogger().d(TAG, "accumulateResults",
                                                        "isbn=" + isbn,
                                                        "isbnFound=" + isbnFound);
                        }
                    } else {
                        // The result did not have an ISBN at all.
                        sitesWithoutIsbn.add(engineId);
                    }
                });
            }
        });

        // finally add the less reliable ones at the end of the list.
        sitesInOrder.addAll(sitesWithoutIsbn);
        return sitesInOrder;
    }

    @Override
    public boolean isCancelled() {
        return cancelRequested.get();
    }

    /**
     * Search the given engine with the site specific book id.
     *
     * @param engineId             to use
     * @param externalIdSearchText to search for
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByExternalId(@NonNull final EngineId engineId,
                                      @NonNull final String externalIdSearchText) {
        if (externalIdSearchText.isEmpty()) {
            throw new IllegalArgumentException("externalIdSearchText.isEmpty()");
        }

        // remove all other criteria (this is CRUCIAL)
        clearSearchCriteria();

        this.externalIdSearchText = new EnumMap<>(EngineId.class);
        this.externalIdSearchText.put(engineId, externalIdSearchText);

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        prepareSearch();
        return startSearch(context, engineId);
    }

    /**
     * Creates {@link TaskProgress} with the global/total progress of all tasks.
     *
     * @return instance
     */
    @NonNull
    private TaskProgress accumulateProgress() {

        int progressMax = 0;
        int progressCount = 0;

        final StringJoiner sb = new StringJoiner("\n");
        // Start with the base message if we have one.
        if (baseMessage != null && !baseMessage.isEmpty()) {
            sb.add(baseMessage);
        }

        synchronized (progressByEngineId) {
            if (!progressByEngineId.isEmpty()) {
                // Append each task message
                progressByEngineId
                        .values()
                        .stream()
                        .map(msg -> String.format(listElementPrefixString, msg.text))
                        .forEach(sb::add);

                // Accumulate the current & max values for each active task.
                for (final TaskProgress taskProgress : progressByEngineId.values()) {
                    progressMax += taskProgress.maxPosition;
                    progressCount += taskProgress.position;
                }
            }
        }

        return new TaskProgress(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                progressMax, progressCount, null);
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     */
    private void prepareSearch() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
                throw new IllegalStateException("Network should be checked before starting search");
            }

            if (isSearchActive()) {
                throw new IllegalStateException("a search is already running");
            }

            // Note we don't care about publisher.
            if (authorSearchText.isEmpty()
                && titleSearchText.isEmpty()
                && isbnSearchText.isEmpty()
                && (externalIdSearchText == null || externalIdSearchText.isEmpty())) {
                throw new IllegalArgumentException("empty criteria");
            }
        }

        // reset flags
        waitingForIsbnOrCode = false;
        cancelRequested.set(false);

        // no synchronized needed, at this point there are no other threads
        resultsByEngineId.clear();
        errorsByEngineId.clear();

        isbn = new ISBN(isbnSearchText, strictIsbn);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "prepareSearch",
                                        "externalIdSearchText=" + externalIdSearchText
                                        + "|isbnSearchText=" + isbnSearchText
                                        + "|isbn=" + isbn
                                        + "|strictIsbn=" + strictIsbn
                                        + "|authorSearchText=" + authorSearchText
                                        + "|titleSearchText=" + titleSearchText
                                        + "|publisherSearchText=" + publisherSearchText);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.clear();
            searchTasksEndTime.clear();
        }
    }

    /**
     * Check if a search task is already running.
     *
     * @return {@code true} if there is
     */
    public boolean isSearchActive() {
        synchronized (activeTasks) {
            return !activeTasks.isEmpty();
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with all sites <strong>active and disabled</strong>
     */
    @NonNull
    public List<Site> getSiteList() {
        return allSites;
    }

    /**
     * Override the initial list. Can contain active and disabled equally.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final List<Site> sites) {
        allSites = sites;
    }

    /**
     * Start specified site search.
     *
     * @param context  Current context
     * @param engineId to search
     *
     * @return {@code true} if the search was started.
     */
    private boolean startSearch(@NonNull final Context context,
                                @NonNull final EngineId engineId) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final SearchEngineConfig config = engineId.getConfig();
        // Sanity check; should not happen when we get here... flw
        if (config == null) {
            return false;
        }

        SearchEngine searchEngine = engineCache.get(engineId);
        if (searchEngine == null) {
            searchEngine = engineId.createSearchEngine(context);
            engineCache.put(engineId, searchEngine);
        } else {
            searchEngine.reset();
        }

        final SearchTask task = new SearchTask(context, TASK_ID.getAndIncrement(),
                                               searchEngine, searchTaskListener);
        task.setExecutor(ASyncExecutor.MAIN);

        task.setFetchCovers(fetchCover);

        // check for a external id matching the site.
        String externalId = null;
        if (externalIdSearchText != null
            && !externalIdSearchText.isEmpty()) {
            if (externalIdSearchText.get(engineId) != null) {
                externalId = externalIdSearchText.get(engineId);
            }
        }

        if (externalId != null && !externalId.isEmpty()
            && engineId.supports(SearchEngine.SearchBy.ExternalId)) {
            task.setSearchBy(SearchEngine.SearchBy.ExternalId);
            task.setExternalId(externalId);

        } else if (isbn.isValid(true)
                   && engineId.supports(SearchEngine.SearchBy.Isbn)) {
            task.setSearchBy(SearchEngine.SearchBy.Isbn);
            task.setIsbn(isbn);

        } else if (isbn.isValid(false)
                   && engineId.supports(SearchEngine.SearchBy.Barcode)) {
            task.setSearchBy(SearchEngine.SearchBy.Barcode);
            task.setIsbn(isbn);

        } else if (engineId.supports(SearchEngine.SearchBy.Text)) {
            task.setSearchBy(SearchEngine.SearchBy.Text);
            task.setIsbn(isbn);
            task.setAuthor(authorSearchText);
            task.setTitle(titleSearchText);
            task.setPublisher(publisherSearchText);

        } else {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.put(task.getSearchEngine().getEngineId(), System.nanoTime());
        }

        synchronized (activeTasks) {
            activeTasks.put(task.getTaskId(), task);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startSearch",
                                        "searchEngine=" + config.getEngineId().getPreferenceKey());
        }

        task.startSearch();
        return true;
    }

    /**
     * Search criteria.
     *
     * @param externalIds one or more ID's
     *                    The key is the engine id,
     *                    The value us the value of the external domain for that engine
     */
    protected void setExternalIds(@Nullable final Map<EngineId, String> externalIds) {
        externalIdSearchText = externalIds;
    }

    /**
     * Indicate we want a thumbnail.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    protected void setFetchCover(@Nullable final boolean[] fetchCovers) {
        fetchCover = fetchCovers;
    }

    /**
     * Clear all search criteria.
     */
    public void clearSearchCriteria() {
        externalIdSearchText = null;
        isbnSearchText = "";
        authorSearchText = "";
        titleSearchText = "";
        publisherSearchText = "";
    }

    /**
     * Start <strong>all</strong>> searches, which have not been run yet, in parallel.
     *
     * @param context Current context
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearch(@NonNull final Context context) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        boolean atLeastOneStarted = false;
        final List<EngineId> activeEngines = allSites.stream()
                                                     .filter(Site::isActive)
                                                     .map(Site::getEngineId)
                                                     .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            synchronized (resultsByEngineId) {
                if (!resultsByEngineId.containsKey(engineId)) {
                    if (startSearch(context, engineId)) {
                        atLeastOneStarted = true;
                    }
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start a single task.
     *
     * @param context Current context
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNextSearch(@NonNull final Context context) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final List<EngineId> activeEngines = allSites.stream()
                                                     .filter(Site::isActive)
                                                     .map(Site::getEngineId)
                                                     .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            synchronized (resultsByEngineId) {
                if (!resultsByEngineId.containsKey(engineId)) {
                    final boolean started = startSearch(context, engineId);
                    if (started) {
                        return true;
                    }
                    // else, loop to next site
                }
            }
        }
        return false;
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional publisher)
     * until we find an ISBN/code or until we searched all sites.
     * Once/if an ISBN/code is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN/code.
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean search() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        prepareSearch();

        // If we have one or more ID's
        if (externalIdSearchText != null && !externalIdSearchText.isEmpty()
            // or we have a valid code
            || isbn.isValid(strictIsbn)) {

            // then start a concurrent search
            waitingForIsbnOrCode = false;
            return startSearch(context);

        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            waitingForIsbnOrCode = true;
            return startNextSearch(context);
        }
    }

    @NonNull
    public String getIsbnSearchText() {
        return isbnSearchText;
    }

    /**
     * Search criteria.
     *
     * @param isbnSearchText to search for
     */
    public void setIsbnSearchText(@NonNull final String isbnSearchText) {
        this.isbnSearchText = isbnSearchText;
    }

    private void debugEnteredOnSearchTaskFinished(@NonNull final EngineId engineId) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksEndTime.put(engineId, System.nanoTime());
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                        "finished=" + engineId.getPreferenceKey());

            synchronized (activeTasks) {
                for (final SearchTask task : activeTasks.values()) {
                    LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                                "running="
                                                + task.getSearchEngine().getEngineId()
                                                      .getPreferenceKey());
                }
            }
        }
    }

    public boolean isStrictIsbn() {
        return strictIsbn;
    }

    /**
     * Search criteria.
     *
     * @param strictIsbn Flag: set to {@code false} to allow invalid isbn numbers
     *                   to be passed to the searches
     */
    public void setStrictIsbn(final boolean strictIsbn) {
        this.strictIsbn = strictIsbn;
    }

    @NonNull
    public String getAuthorSearchText() {
        return authorSearchText;
    }

    /**
     * Search criteria.
     *
     * @param authorSearchText to search for
     */
    public void setAuthorSearchText(@NonNull final String authorSearchText) {
        this.authorSearchText = authorSearchText;
    }

    @NonNull
    public String getTitleSearchText() {
        return titleSearchText;
    }

    /**
     * Search criteria.
     *
     * @param titleSearchText to search for
     */
    public void setTitleSearchText(@NonNull final String titleSearchText) {
        this.titleSearchText = titleSearchText;
    }

    @NonNull
    public String getPublisherSearchText() {
        return publisherSearchText;
    }

    /**
     * Search criteria.
     *
     * @param publisherSearchText to search for
     */
    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        this.publisherSearchText = publisherSearchText;
    }

    /**
     * Called when all is said and done. Collects all individual website errors (if any)
     * into a single user-formatted message.
     *
     * @param context Current context
     *
     * @return the error message
     */
    @Nullable
    private String accumulateErrors(@NonNull final Context context) {
        // no synchronized needed, at this point all other threads have finished.
        if (!errorsByEngineId.isEmpty()) {
            final String msg = errorsByEngineId
                    .values()
                    .stream()
                    .map(exception -> ExMsg
                            .map(context, exception)
                            .orElseGet(() -> {
                                // generic network related IOException message
                                if (exception instanceof IOException) {
                                    return context.getString(R.string.error_search_failed_network);
                                }
                                // generic unknown message
                                return context.getString(R.string.error_unexpected);
                            }))
                    .collect(Collectors.joining("\n"));

            errorsByEngineId.clear();
            return msg;
        }
        return null;
    }

    private void debugExitOnSearchTaskFinished(@NonNull final Context context,
                                               final long processTime,
                                               @Nullable final String searchErrors) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                        "cancelled=" + cancelRequested.get(),
                                        "searchErrors=" + searchErrors);
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            for (final Map.Entry<EngineId, Long> entry : searchTasksStartTime.entrySet()) {
                final EngineId engineId = entry.getKey();
                final String engineName = engineId.getPreferenceKey();

                final long start = entry.getValue();
                final Long end = searchTasksEndTime.get(engineId);

                if (end != null) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "onSearchTaskFinished",
                                    String.format(Locale.ENGLISH, "engine=%20s:%10d ms",
                                                  engineName,
                                                  (end - start) / NANO_TO_MILLIS));
                } else {
                    LoggerFactory.getLogger()
                                 .d(TAG, "onSearchTaskFinished",
                                    String.format(Locale.ENGLISH, "engine=%20s|never finished",
                                                  engineName));
                }
            }

            LoggerFactory.getLogger()
                         .d(TAG, "onSearchTaskFinished",
                            String.format(Locale.ENGLISH, "total search time: %10d ms",
                                          (processTime - searchStartTime)
                                          / NANO_TO_MILLIS));

            LoggerFactory.getLogger()
                         .d(TAG, "onSearchTaskFinished",
                            String.format(Locale.ENGLISH, "processing time: %10d ms",
                                          (System.nanoTime() - processTime)
                                          / NANO_TO_MILLIS));
        }
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
        this.baseMessage = baseMessage;
    }

    /**
     * Value class encapsulating
     * where a result came from + how the search was done + the result itself.
     * The result itself can be {@code null} if nothing was found.
     */
    static class SearchResult {

        @Nullable
        private final Book result;
        @NonNull
        private final EngineId engineId;
        @NonNull
        private final SearchEngine.SearchBy searchBy;

        SearchResult(@NonNull final EngineId engineId,
                     @NonNull final SearchEngine.SearchBy searchBy,
                     @Nullable final Book result) {
            this.engineId = engineId;
            this.searchBy = searchBy;
            this.result = result;
        }

        @NonNull
        EngineId getEngineId() {
            return engineId;
        }

        @NonNull
        SearchEngine.SearchBy getSearchBy() {
            return searchBy;
        }

        @NonNull
        Optional<Book> getResult() {
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        }
    }

    /**
     * Listener for <strong>individual</strong> search tasks.
     * There MUST only be ONE instance of this listener, shared between all tasks!
     * It serves as a translation layer between the standard TaskListener
     * and what the SearchCoordinator needs.
     * <p>
     * Dev. Note: and it's an inner class because if we simply create it as an anonymous class
     * as we normally would/should, or ...
     * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
     * Each time we format the code, methods and variables jump around.
     * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
     */
    private class SearchTaskListener
            implements TaskListener<Book> {

        @Override
        public void onProgress(@NonNull final TaskProgress message) {
            onSearchProgress(message);
        }

        @Override
        public void onFinished(final int taskId,
                               @Nullable final Book result) {
            // The result MUST NOT be null
            onSearchTaskFinished(taskId, Objects.requireNonNull(result, "result"));
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final Book result) {
            onSearchTaskFinished(taskId, result);
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Throwable e) {
            onSearchTaskFailed(taskId, e);
        }
    }
}
