package app.service;

import app.model.Post;
import retrofit.http.GET;
import rx.Observable;


public interface AppNetService {

    String SERVICE_ENDPOINT = "https://alpha-api.app.net";

    @GET("/stream/0/posts/stream/global")
    Observable<Post> getPosts();

}
