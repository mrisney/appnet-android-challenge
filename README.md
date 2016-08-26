### AppNet Android Challenge

#####Introduction
AppNet Fetch App showcases the use of Square's [Retrofit](http://square.github.io/retrofit/) and [RxJava](https://github.com/ReactiveX/RxJava/wiki) to make asynchronous HTTP Requests in Android Application. The App makes **HTTP GET** requests to the [App.Net API](https://alpha-api.app.net/stream/0/posts/stream/global) to retrieve **global postings** and **public postings**.

The FETCH button kicks off a series of HTTP requests to AppNet API. The HTTP requests are built via Retrofit. The calls are made asynchronously through RxJava. Notice that the cards are laid out in different order each time the button is pressed. You are seeing async threading at work! Each card is rendered when the result comes back from a GET request.

![alt tag](http://randomdotnext.com/content/images/2015/05/demo-github-android.gif)

#####The Setup
Let's take care of the dependency injection for retrofit and RxJava/RxAndroid,
I've also added  javax.annotation.processing, this package appears to be missing from Android :(
```java
dependencies {
  compile 'javax.annotation:javax.annotation-api:1.2'
  compile 'io.reactivex:rxjava:1.1.+'
  compile 'io.reactivex:rxandroid:0.23.+'
  compile 'com.squareup.retrofit:retrofit:1.9.+'
}
```

Important that the Android app permissions in AndroidManifest is set appropriately :
```java
<uses-permission android:name="android.permission.INTERNET" />
```

#####Retrofit Service/Model
Retrofit uses a Java interface as proxy for the REST API calls. All we have to do is to define the **@GET** method and the url/path. The following code makes a **GET** request to the Github URL and returns an [Observable] (https://github.com/ReactiveX/RxJava/wiki/Observable). The Observable object is used by RxJava to do stream processing (I'll explain this later).
```java
public interface AppNetService {
    String SERVICE_ENDPOINT = "https://alpha-api.app.net/";

    @GET("/stream/0/posts/stream/global")
    Observable<Post> getPosts();
}
```

AppNetService needs a RestAdapter implementation.I created a generic factory class to do the implementation:
```java
static <T> T createRetrofitService(final Class<T> clazz, final String endPoint) {
    final RestAdapter restAdapter = new RestAdapter.Builder()
            .setEndpoint(endPoint)
            .build();
    T service = restAdapter.create(clazz);

    return service;
}
```

The AppNet REST API returns the following JSON. Using Curl - we can get a JSON response :
`curl https://alpha-api.app.net/stream/0/posts/stream/global`
```java
{
"meta": {
"min_id": "70300753",
"code": 200,
"max_id": "70300773",
"more": true
},
"data": [
{
"created_at": "2016-08-21T22:42:45Z",
"num_stars": 0,
"num_replies": 0,
  //...truncated JSON
}
```
I defined the model in separate Java files, using the [jsonschema2pojo Gradle Plugin (https://mvnrepository.com/artifact/org.jsonschema2pojo/jsonschema2pojo-gradle-plugin) 

The field variables in the model are automatically parsed from the JSON response. So you don't need to worry about writing the parsing code. If you want to make modifications to the service, make sure that the variable names are exactly the same as API definition :
```java
public class Post {

    @Expose
    private Meta meta;
    @Expose
    private List<Datum> data = new ArrayList<Datum>();
}
```
Other than Java's boilerplate stuff (boo), the code is very concise and readable. If you have more than one endpoint you want to access, simply add it to your service interface at little additional cost!

#####RxJava Async Stream
The [Observable](http://reactivex.io/documentation/observable.html) object from our AppNet Service streams data when it becomes available. We need to have an Subscriber (sometimes called Observer) to watch for the data stream changes. Conceptually, the Subscriber subscribes to an Observable. The following block of code performs the entire process described.

```java
AppNetService service = ServiceFactory.createRetrofitService(AppNetService.class, AppNetService.SERVICE_ENDPOINT);
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
                       Log.d(TAG,"number of posts : "+ post.getData().size());
                       for (Datum datum : post.getData()) {
                           mItems.add(datum);
                       }
                       Log.d(TAG,"number of items : "+ mItems.size());

                       notifyDataSetChanged();
                   }
               });
```
The method getPosts() - returns the root level Post that APINet returns, this is then cast and made available to [Post] (https://github.com/mrisney/appnet-android-challenge/blob/master/app/src/main/java/app/model/Post.java) Object, which has [Metadata] (https://github.com/mrisney/appnet-android-challenge/blob/master/app/src/main/java/app/model/Meta.java) , and collection of [Datum] (https://github.com/mrisney/appnet-android-challenge/blob/master/app/src/main/java/app/model/Datum.java) objects : 
```java
AppNetService service = ServiceFactory.createRetrofitService(AppNetService.class, AppNetService.SERVICE_ENDPOINT);
service.getPosts()
```
AppNetService Interface has the getPost method which returns the Observable. 
I am using the Schedulers to repeat the call every 5 seconds, this is a subscription that is cast to and  Observable.interval(5, TimeUnit.SECONDS) of the subsequent REST call response.

```java
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
```
The reason to call them on the mainThread is that only the mainThread can update the UI. If you have data that do not need to be displayed immediately, you would not need to observe on main thread. The difference between observeOn and subscribeOn is best explained in this [stackoverflow](http://stackoverflow.com/questions/20451939/observeon-and-subscribeon-where-the-work-is-being-done).

```java
new Subscriber<Github>() {
    @Override
    public final void onCompleted() {
        // do nothing
    }

    @Override
    public final void onError(Throwable e) {
        Log.e("AppNet Errror : ", e.getMessage());
    }

    @Override
    public final void onNext(Post post) {
        mCardAdapter.addData(post);
    }
}
```
This Subscriber responds to the Observable's stream. onNext is called when the REST call receives data. In this AppNet example, there is a Post Object, that has a collection of Datum Objects. so it is only called once. When the REST response is a list, the code can be called each time an item is received. onComplete and onError behave exactly as the name implies.


#####
This pattern affords a non-blocking HTTP calls on Android. using  Square and ReactiveX!

<br>
#####Reference:
Code on github: [https://goo.gl/DGMF2F] <br>
Square Retrofit Doc: [http://goo.gl/UwksBu] <br>
RxJava Doc: [https://goo.gl/5AqMNi] <br>
Github API: [https://goo.gl/7nsdh0] <br>
CardView/RecycleView UI Reference: [http://goo.gl/stNj2J]
