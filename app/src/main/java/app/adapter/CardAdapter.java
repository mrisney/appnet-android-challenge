package app.adapter;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.risney.appnet.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import app.model.Datum;
import app.model.Post;
import app.service.AppNetService;
import app.service.ServiceFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

// http://stackoverflow.com/questions/31291406/using-rxjava-inside-recyclerview-adapter
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {
    private static final String TAG = "CardAdapter";
    Subscription subscribe;
    List<Datum> mItems;
    private AtomicLong lastTick = new AtomicLong(0L);

    AppNetService service;

    public CardAdapter() {
        super();
        mItems = new ArrayList<Datum>();
        service = ServiceFactory.createRetrofitService(AppNetService.class, AppNetService.SERVICE_ENDPOINT);
    }

    public void addData(Datum datum) {
        if (mItems.size()==0) {
            mItems.clear();
            notifyDataSetChanged();
        }
        mItems.add(datum);
        notifyItemChanged(0);
        Log.d(TAG,"number of posts in mItems : "+mItems.size());
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void startFetching() {

        subscribe = Observable.interval(5, TimeUnit.SECONDS)
                .map(tick -> lastTick.getAndIncrement())
                .flatMap(posts -> service.getPosts())
                .doOnError(err -> Log.e(TAG, "Error retrieving messages" + err))
                .retry()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Post>() {
                        @Override public void onCompleted() { }
                    @Override public void onError(Throwable e) { }
                    @Override
                    public void onNext(Post post) {
                        for (Datum datum : post.getData()) {
                            addData(datum);
                        }
                        Log.d(TAG,"number of items : "+ mItems.size());
                    }
                });
    }

    public void stopFetching() {
        if(subscribe != null && !subscribe.isUnsubscribed())
            subscribe.unsubscribe();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.recycler_view, viewGroup, false);
        ViewHolder viewHolder = new ViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int i) {
        Datum datum = mItems.get(i);
        Log.d(TAG,"adding to viewholder item : "+ i);

        viewHolder.created.setText(datum.getCreatedAt());
        viewHolder.gist.setText(datum.getText());
        viewHolder.user.setText(datum.getUser().getName().toString());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        public TextView created;
        public TextView gist;
        public TextView user;

        public ViewHolder(View itemView) {
            super(itemView);
            created = (TextView) itemView.findViewById(R.id.created);
            gist = (TextView) itemView.findViewById(R.id.gist);
            user = (TextView) itemView.findViewById(R.id.user);
        }
    }
}