package com.azizbekian.example.manager;

import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewStub;
import android.widget.ProgressBar;

import com.azizbekian.example.adapter.SearchAdapter;
import com.azizbekian.example.entity.SearchItem;
import com.azizbekian.example.listener.BottomReachedScrollListener;
import com.azizbekian.example.rest.TraktTvApi;
import com.azizbekian.example.ui.fragment.MainFragment;
import com.azizbekian.example.utils.AnimationUtils;
import com.azizbekian.example.utils.ConnectivityUtils;

import java.util.ArrayList;
import java.util.List;

import com.azizbekian.example.R;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

import static android.graphics.PorterDuff.Mode.MULTIPLY;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static com.azizbekian.example.MyApplication.getAppComponent;
import static com.azizbekian.example.misc.Constants.ANIM_DURATION_FADE;
import static com.azizbekian.example.misc.Constants.TYPE_MOVIE;

/**
 * This class handles searching functionality.
 * Be aware, it keeps references to context, you have to null-ify this object when it's not needed.
 * <p>
 * Created on April 02, 2016.
 *
 * @author Andranik Azizbekian (azizbekyanandranik@gmail.com)
 */
public class SearchHelper {

    TraktTvApi.Search traktTvSearchApi;

    private MainFragment mHostFragment;
    private View mSearchEmptyView;
    private View root;
    private ProgressBar mProgressBar;

    private SearchAdapter mSearchAdapter;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final List<SearchItem> EMPTY_LIST = new ArrayList<>();
    private Call<List<SearchItem>> sCurrentSearchCall;
    private BottomReachedScrollListener mBottomReachedListener;

    private int mPageCounter = 2;

    private static final int IDLE = 0;
    private static final int FETCHING = 1;
    private static int sMode = IDLE;

    public SearchHelper(MainFragment hostFragment, TraktTvApi.Search traktTvSearchApi) {
        this.mHostFragment = hostFragment;
        this.traktTvSearchApi = traktTvSearchApi;
        View view = mHostFragment.getView();
        if (null != view) {
            root = ((ViewStub) view.findViewById(R.id.viewstub_search)).inflate();
            // at this point our layout is hidden and hasn't been given a chance to measure it's size
            // we need to manually measure it to get rid of reveal animation first time issue
            root.measure(makeMeasureSpec(view.getWidth(), EXACTLY), makeMeasureSpec(view.getHeight(), EXACTLY));

            mProgressBar = (ProgressBar) root.findViewById(R.id.search_progress_indicator);
            mProgressBar.getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(mHostFragment.getContext(), R.color.lightGreen300), MULTIPLY);
            mSearchEmptyView = root.findViewById(R.id.search_empty_view);

            RecyclerView mSearchRecyclerView = (RecyclerView) root.findViewById(R.id.searchRecyclerView);
            LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(root.getContext());
            mSearchRecyclerView.setLayoutManager(mLinearLayoutManager);
            mSearchRecyclerView.setHasFixedSize(true);
            mSearchAdapter = new SearchAdapter(this, mHostFragment, EMPTY_LIST, getAppComponent().getPicasso());
            mSearchRecyclerView.setAdapter(mSearchAdapter);

            mBottomReachedListener = new BottomReachedScrollListener(mLinearLayoutManager, this::loadSearchResult);
            mSearchRecyclerView.addOnScrollListener(mBottomReachedListener);
        }
    }

    /**
     * Shows or hides empty view, depending on {@code mMovieAdapter}'s size.
     */
    private void toggleEmptyView() {
        if (!mSearchAdapter.isEmpty()) {
            mSearchEmptyView.animate().alpha(0.0f).setDuration(ANIM_DURATION_FADE).withEndAction(() -> {
                if (null != mSearchEmptyView) {
                    mSearchEmptyView.setVisibility(View.GONE);
                    mSearchEmptyView.setAlpha(1.0f);
                }
            });
        } else mSearchEmptyView.setVisibility(View.VISIBLE);
    }

    public void setSearchResult(List<SearchItem> result) {
        mSearchAdapter.setItems(result);
        toggleEmptyView();
        mBottomReachedListener.reset();
    }

    public void addSearchResult(List<SearchItem> result) {
        mSearchAdapter.addItems(result);
        toggleEmptyView();
    }

    public void resetData() {
        cancelSearch();
        mPageCounter = 2;
        setSearchResult(EMPTY_LIST);
    }

    /**
     * Performs request to fetch data from server.
     */
    private void loadSearchResult() {
        // if the content is being loaded - ignore this request
        if (sMode == IDLE) {
            final boolean isAdapterEmpty = mSearchAdapter.isEmpty();
            if (!isAdapterEmpty) sMode = FETCHING;

            if (ConnectivityUtils.isNetworkAvailable(mHostFragment.getContext())) {
                sMode = isAdapterEmpty ? IDLE : FETCHING;
                if (isAdapterEmpty) toggleProgressBar(true);
                String query = mHostFragment.getQuery();
                cancelSearch();
                sCurrentSearchCall = traktTvSearchApi.searchMovies(query, TYPE_MOVIE, mPageCounter);
                sCurrentSearchCall.enqueue(new Callback<List<SearchItem>>() {
                    @Override
                    public void onResponse(Call<List<SearchItem>> call, Response<List<SearchItem>> response) {
                        sMode = IDLE;
                        toggleProgressBar(false);
                        List<SearchItem> searchItems = response.body();
                        if (searchItems == null) {
                            // something went wrong, show empty list
                            setSearchResult(EMPTY_LIST);
                        } else {
                            // successfully downloaded, can increment paging
                            ++mPageCounter;
                            addSearchResult(searchItems);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<SearchItem>> call, Throwable t) {
                        sMode = IDLE;
                        if (isAdapterEmpty) toggleProgressBar(false);
                        // an error occurred, show empty list
                        setSearchResult(EMPTY_LIST);
                    }
                });
            } else {
                mHostFragment.showSnackbar(true);
            }
        }
    }

    /**
     * Control's {@code progressBar}'s visibility state.
     *
     * @param show if true, sets {@code progressBar}'s visibility to {@code View.VISIBLE}, else - animates to {@code View.GONE}.
     */
    public void toggleProgressBar(boolean show) {
        if (!show) {
            mProgressBar.animate().alpha(0.0f).setDuration(300).withEndAction(() -> {
                if (null != mProgressBar) {
                    mProgressBar.setVisibility(View.GONE);
                    mProgressBar.setAlpha(1.0f);
                }
            });
        } else {
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Indicating whether currently the data is being fetched from server.
     *
     * @return true if data is being loaded, false otherwise.
     */
    public boolean isContentLoading() {
        return sMode == FETCHING;
    }

    /**
     * When {@link MainFragment#searchView} content is being changed, request is being sent to server in order to fetch appropriate search data.
     *
     * @param observable Filtered observable. If the stream has reached this point, then the call should be sent.
     * @return Subscription to hold on, in order to unsubscribe later.
     */
    public Subscription onSearchTextChange(Observable<CharSequence> observable) {
        return observable.map(charSequence -> {
            sMode = FETCHING;
            mPageCounter = 2;
            mUiHandler.post(() -> toggleProgressBar(true));
            cancelSearch();
            sCurrentSearchCall = traktTvSearchApi.searchMovies(charSequence.toString(), TYPE_MOVIE, 1);
            return sCurrentSearchCall;
        })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Call<List<SearchItem>>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        sMode = IDLE;
                    }

                    @Override
                    public void onNext(Call<List<SearchItem>> listCall) {
                        listCall.enqueue(new Callback<List<SearchItem>>() {
                            @Override
                            public void onResponse(Call<List<SearchItem>> call, Response<List<SearchItem>> response) {
                                sMode = IDLE;
                                toggleProgressBar(false);
                                List<SearchItem> searchItems = response.body();
                                setSearchResult(searchItems == null ? EMPTY_LIST : searchItems);
                            }

                            @Override
                            public void onFailure(Call<List<SearchItem>> call, Throwable t) {
                                sMode = IDLE;
                                toggleProgressBar(false);
                                // an error occurred, show empty list
                                setSearchResult(EMPTY_LIST);
                            }
                        });
                    }
                });
    }

    /**
     * Cancel current search call.
     */
    public void cancelSearch() {
        if (null != sCurrentSearchCall) sCurrentSearchCall.cancel();
    }

    /**
     * Opens search view with appropriate animation applied.
     *
     * @param show boolean, indicating whether searchview should be opened or close. If true - opens search view, closes otherwise.
     */
    public void animateSearchView(boolean show) {

        if (show) {
            AnimationUtils.animateSearchClick(root, root.getMeasuredHeight(), root.getTop(), true);
        } else {
            // we do not want all data displayed when user presses search again
            resetData();
            AnimationUtils.animateSearchClick(root, root.getRight(), root.getTop(), false);
        }
    }


}