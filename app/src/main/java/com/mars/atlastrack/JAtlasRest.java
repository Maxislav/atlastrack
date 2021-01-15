package com.mars.atlastrack;

import android.util.Log;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class JAtlasRest {

    public interface WebService {
        @GET("/log")
        Call<String> log(@Query("id") String id);
    }
static int i;
    JAtlasRest() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://jquery.org")
                // .addConverterFactory(GsonConverterFactory.create())
                .build();

        WebService service = retrofit.create(WebService.class);
        Call<String> signin = service.log("000000000012");

        signin.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                Log.i("Response", response.body().toString());
                //Toast.makeText()
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        Log.d("onSuccess", response.body().toString());
                    } else {
                        Log.d("onEmptyResponse", "Returned empty response");//Toast.makeText(getContext(),"Nothing returned",Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {

            }
        });
    }


}