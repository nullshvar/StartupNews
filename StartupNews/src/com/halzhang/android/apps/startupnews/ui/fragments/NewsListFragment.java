/**
 * Copyright (C) 2013 HalZhang
 */

package com.halzhang.android.apps.startupnews.ui.fragments;

import com.google.analytics.tracking.android.EasyTracker;
import com.halzhang.android.apps.startupnews.Constants.IntentAction;
import com.halzhang.android.apps.startupnews.R;
import com.halzhang.android.apps.startupnews.entity.SNFeed;
import com.halzhang.android.apps.startupnews.entity.SNNew;
import com.halzhang.android.apps.startupnews.parser.SNFeedParser;
import com.halzhang.android.apps.startupnews.snkit.JsoupFactory;
import com.halzhang.android.apps.startupnews.snkit.SNApi;
import com.halzhang.android.apps.startupnews.snkit.SessionManager;
import com.halzhang.android.apps.startupnews.ui.DiscussActivity;
import com.halzhang.android.apps.startupnews.ui.LoginActivity;
import com.halzhang.android.apps.startupnews.utils.ActivityUtils;
import com.halzhang.android.apps.startupnews.utils.AppUtils;
import com.halzhang.android.apps.startupnews.utils.DateUtils;
import com.halzhang.android.common.CDLog;
import com.halzhang.android.common.CDToast;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.HttpStatus;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * StartupNews
 * <p>
 * </p>
 * 
 * @author <a href="http://weibo.com/halzhang">Hal</a>
 * @version Mar 7, 2013
 */
public class NewsListFragment extends AbsBaseListFragment implements OnItemLongClickListener {

    private static final String LOG_TAG = NewsListFragment.class.getSimpleName();

    private NewsTask mNewsTask;

    private String mNewsURL;

    public static final String ARG_URL = "new_url";

    private SNFeed mSnFeed = new SNFeed();

    private NewsAdapter mAdapter;

    private JsoupFactory mJsoupFactory;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (IntentAction.ACTION_LOGIN.equals(action)) {
                String user = intent.getStringExtra(IntentAction.EXTRA_LOGIN_USER);
                if (!TextUtils.isEmpty(user)) {
                    if (mNewsTask != null) {
                        mNewsTask.cancel(true);
                        mNewsTask = null;
                    }
                    mNewsTask = new NewsTask(NewsTask.TYPE_REFRESH);
                    mNewsTask.execute(mNewsURL);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new NewsAdapter();
        Bundle args = getArguments();
        if (args != null) {
            mNewsURL = args.getString(ARG_URL);
        } else {
            mNewsURL = getString(R.string.host, "/news");
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(IntentAction.ACTION_LOGIN);
        getActivity().registerReceiver(mReceiver, filter);
        mJsoupFactory = JsoupFactory.getInstance(getActivity().getApplicationContext());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // getListView().setOnItemLongClickListener(this);
        registerForContextMenu(getListView());
        setListAdapter(mAdapter);
        if (mNewsTask == null && mAdapter.isEmpty()) {
            mNewsTask = new NewsTask(NewsTask.TYPE_REFRESH);
            mNewsTask.execute(mNewsURL);
            getPullToRefreshListView().setRefreshing(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNewsTask != null) {
            mNewsTask.cancel(true);
            mNewsTask = null;
        }
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.fragment_news, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterContextMenuInfo) item.getMenuInfo()).position;
        final SNNew snNew = (SNNew) mAdapter.getItem(position - 1);
        Log.i(LOG_TAG, snNew.toString());
        switch (item.getItemId()) {
            case R.id.menu_show_comment:
                EasyTracker.getTracker().sendEvent("ui_action", "context_item_selected",
                        "newslistfragment_menu_show_comment", 0L);
                openDiscuss(snNew);
                return true;
            case R.id.menu_show_article:
                EasyTracker.getTracker().sendEvent("ui_action", "context_item_selected",
                        "newslistfragment_menu_show_acticle", 0L);
                ActivityUtils.openArticle(getActivity(), snNew);
                return true;
            case R.id.menu_up_vote:
                EasyTracker.getTracker().sendEvent("ui_action", "context_item_selected",
                        "newslistfragment_menu_upvote", 0L);
                if (SessionManager.getInstance(getActivity()).isValid()) {
                    SNApi api = new SNApi(getActivity());
                    final String url = getString(R.string.vote_url, snNew.getPostID(),
                            SessionManager.getInstance(getActivity()).getSessionId(),
                            SessionManager.getInstance(getActivity()).getSessionUser());
                    api.upVote(url, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, String content) {
                            if (statusCode == HttpStatus.SC_OK && TextUtils.isEmpty(content)) {
                                CDToast.showToast(getActivity(), R.string.tip_vote_success);
                            } else {
                                EasyTracker.getTracker().sendEvent("ui_action_feedback",
                                        "upvote_feedback", content, 0L);
                                if (content.contains("mismatch")) {
                                    // 用户cookie无效
                                    startActivity(new Intent(getActivity(), LoginActivity.class));
                                    CDToast.showToast(getActivity(), R.string.tip_cookie_invalid);
                                } else {
                                    CDToast.showToast(getActivity(),
                                            getString(R.string.tip_vote_duplicate));
                                }
                            }
                        }

                        @Override
                        public void onFailure(Throwable error, String content) {
                            EasyTracker.getTracker().sendException("up vote error:" + content,
                                    error, false);
                            CDToast.showToast(getActivity(), getString(R.string.tip_vote_failure));
                        }
                    });
                } else {
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    startActivity(intent);
                }
                return true;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onPullDownListViewRefresh(PullToRefreshListView refreshListView) {
        super.onPullDownListViewRefresh(refreshListView);
        EasyTracker.getTracker().sendEvent("ui_action", "pull_down_list_view_refresh",
                "news_list_fragment_pull_down_list_view_refresh", 0L);
        if (mNewsTask != null) {
            return;
        }
        mNewsTask = new NewsTask(NewsTask.TYPE_REFRESH);
        mNewsTask.execute(mNewsURL);
    }

    @Override
    protected void onPullUpListViewRefresh(PullToRefreshListView refreshListView) {
        super.onPullDownListViewRefresh(refreshListView);
        EasyTracker.getTracker().sendEvent("ui_action", "pull_up_list_view_refresh",
                "news_list_fragment_pull_up_list_view_refresh", 0L);
        if (mNewsTask != null) {
            return;
        }
        if (TextUtils.isEmpty(mSnFeed.getMoreUrl())) {
            Toast.makeText(getActivity(), R.string.tip_last_page, Toast.LENGTH_SHORT).show();
            getPullToRefreshListView().onRefreshComplete();
        } else {
            mNewsTask = new NewsTask(NewsTask.TYPE_LOADMORE);
            mNewsTask.execute(mSnFeed.getMoreUrl());
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        EasyTracker.getTracker().sendEvent("ui_action", "list_item_click",
                "news_list_fragment_list_item_click", 0L);
        mAdapter.notifyDataSetChanged();
        SNNew entity = (SNNew) mAdapter.getItem(position - 1);
        ActivityUtils.openArticle(getActivity(), entity);
    }

    private void openDiscuss(SNNew snNew) {
        if (snNew == null) {
            return;
        }
        Intent intent = new Intent(getActivity(), DiscussActivity.class);
        intent.putExtra(DiscussActivity.ARG_SNNEW, snNew);
        intent.putExtra(DiscussActivity.ARG_DISCUSS_URL, snNew.getDiscussURL());
        startActivity(intent);
    }

    private class NewsTask extends AsyncTask<String, Void, Boolean> {

        public static final int TYPE_REFRESH = 1;

        public static final int TYPE_LOADMORE = 2;

        private int mType = 0;

        public NewsTask(int type) {
            mType = type;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                Connection conn = mJsoupFactory.newJsoupConnection(params[0]);
                if (conn == null) {
                    return false;
                }
                Document doc = conn.get();
                SNFeedParser parser = new SNFeedParser();
                SNFeed feed = parser.parseDocument(doc);
                if (mType == TYPE_REFRESH && mSnFeed.size() > 0) {
                    mSnFeed.clear();
                }
                mSnFeed.addNews(feed.getSnNews());
                mSnFeed.setMoreUrl(feed.getMoreUrl());
                return true;
            } catch (Exception e) {
                CDLog.w(LOG_TAG,"",e);
                EasyTracker.getTracker().sendException("NewsTask", e, false);
                return false;
            }

        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                onDataFirstLoadComplete();
                mAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_LONG).show();
            }
            mNewsTask = null;
            getPullToRefreshListView().getLoadingLayoutProxy().setLastUpdatedLabel(
                    DateUtils.getLastUpdateLabel(getActivity()));
            getPullToRefreshListView().onRefreshComplete();
            super.onPostExecute(result);
        }

        @Override
        protected void onCancelled() {
            getPullToRefreshListView().onRefreshComplete();
            mNewsTask = null;
            super.onCancelled();
        }

    }

    @Override
    public int getContentViewId() {
        return R.layout.ptr_list_layout;
    }

    private class NewsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mSnFeed.size();
        }

        @Override
        public Object getItem(int position) {
            return mSnFeed.getSnNews().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(getActivity()).inflate(R.layout.news_list_item,
                        null);
                holder.user = (TextView) convertView.findViewById(R.id.news_item_user);
                holder.createat = (TextView) convertView.findViewById(R.id.news_item_createat);
                holder.title = (TextView) convertView.findViewById(R.id.news_item_title);
                holder.subText = (TextView) convertView.findViewById(R.id.news_item_subtext);
                holder.domain = (TextView) convertView.findViewById(R.id.news_item_domain);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            SNNew entity = mSnFeed.getSnNews().get(position);
            holder.user.setText(entity.getUser().getId());
            holder.title.setText(entity.getTitle());
            holder.subText.setText(getString(R.string.news_subtext, entity.getPoints(),
                    entity.getCommentsCount()));
            holder.createat.setText(entity.getCreateat());
            holder.domain.setText(entity.getUrlDomain());
            int textColor = AppUtils.getMyApplication(getActivity()).isHistoryContains(
                    entity.getUrl()) ? Color.GRAY : Color.BLACK;
            holder.title.setTextColor(textColor);
            holder.subText.setTextColor(textColor);
            holder.domain.setTextColor(textColor);
            holder.createat.setTextColor(textColor);
            return convertView;
        }

        class ViewHolder {
            TextView user;

            TextView createat;

            TextView title;

            TextView subText;

            TextView domain;
        }

    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        EasyTracker.getTracker().sendEvent("ui_action", "list_item_long_click",
                "news_list_fragment_list_item_long_click", 0L);
        SNNew entity = (SNNew) mAdapter.getItem(position - 1);
        openDiscuss(entity);
        return true;
    }

}
