package android.cunniao.cn.myapplication;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import io.reactivex.functions.Consumer;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Okio;
import okio.Sink;
import okio.Source;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;
import retrofit2.http.Streaming;


public class OtherActivity extends AppCompatActivity {

    private APIService mApiService;
    private Context mContext;
    private RxPermissions mRxPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);


        mContext = this;

        long maxCacheSize = 100 * 1024 * 1024;

        String filePath = getFilePath();
        Cache cache = new Cache(
                new File(filePath),
                maxCacheSize);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .cache(cache)

                .addNetworkInterceptor(new MyProgressInterceptor())
                .addNetworkInterceptor(new MyUploadProgressInterceptor())
                .addNetworkInterceptor(new NetCacheInterceptor())
                .addInterceptor(new OfflineCacheInterceptor())
                .connectTimeout(1000,TimeUnit.MILLISECONDS)
                .readTimeout(1000,TimeUnit.MILLISECONDS)
                .writeTimeout(1000,TimeUnit.MILLISECONDS)
                .build();

        String baseUrl = "http://192.168.191.1:8080/";
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        mApiService = retrofit.create(APIService.class);
        mRxPermissions = new RxPermissions(this);

    }

    /**
     * 有网时候的缓存
     */
    private class NetCacheInterceptor  implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            okhttp3.Response response = chain.proceed(request);
            int onlineCacheTime = 0;//在线的时候的缓存过期时间，如果想要不缓存，直接时间设置为0
            return response.newBuilder()
                    .header("Cache-Control", "public, max-age="+onlineCacheTime)
                    .removeHeader("Pragma")
                    .build();
        }
    };
    /**
     * 没有网时候的缓存
     */
    private class OfflineCacheInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!isNetworkConnected()) {
                int offlineCacheTime = 60;//离线的时候的缓存的过期时间
                request = request.newBuilder()
                        .cacheControl(new CacheControl
                                .Builder()
                                .maxStale(60,TimeUnit.SECONDS)
                                .onlyIfCached()
                                .build()
                        ) //两种方式结果是一样的，写法不同
//                        .header("Cache-Control", "public, only-if-cached, max-stale=" + offlineCacheTime)
                        .build();
            }
            return chain.proceed(request);
        }
    };

    private class MyCacheInterceptor implements Interceptor{

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {

            Request request = chain.request();
            if(isNetworkConnected()) {
                Log.d("--TAG--", "intercept: 有网" );
                return chain.proceed(request);

            } else {
                Log.d("--TAG--", "intercept: 无网" );

                Request newRequest = chain.request().newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control",
                                "only-if-cached,max-stale="
                                        + 10 * 60)
                        .build();
                return chain.proceed(newRequest);
            }
        }
    }

    /**
     * 判断是否有网络
     *
     * @return 返回值
     */
    public boolean isNetworkConnected() {

        Context context = OtherActivity.this;
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();

            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    public class CacheInterceptor implements Interceptor {
        @Override

        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            okhttp3.Response response = chain.proceed(request);
            Headers headers = response.headers();
            for (String s : headers.names()) {

                Log.d("--TAG--", "intercept: header " + s);
            }
            okhttp3.Response response1 = response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    //cache for 30 days
                    .header("Cache-Control", "max-age=" + 3600 * 24 * 30)
                    .build();
            Headers headers1 = response1.headers();
            for (String s : headers1.names()) {

                Log.d("--TAG--", "intercept: header " + s);
            }
            return response1;
        }
    }

    //监听下载进度
    ProgressListener progressListener = new ProgressListener() {

        @Override
        public void update(long bytesRead, long contentLength, boolean done) {

//            System.out.format("%d%% ===%b", (100 * bytesRead) / contentLength,done);

            String format = String.format("%d%%====%b", (100 * bytesRead) / contentLength, done);
            Log.e("--TAG--", "update: " + format);
        }
    };

    class MyProgressInterceptor implements Interceptor{

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {

            okhttp3.Response originalResponse = chain.proceed(chain.request());
            return originalResponse.newBuilder().body(
                    new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();

        }
    }

    class MyUploadProgressInterceptor implements Interceptor{

        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {

            Request request = chain.request();
            if(request.body() == null) {

                return chain.proceed(request);
            }
            Request request1 = request.newBuilder().post(new ProgressRequestBody(request.body())).build();
            okhttp3.Response proceed = chain.proceed(request1);
            return proceed;
        }
    }

    private static class ProgressRequestBody extends RequestBody{

        private RequestBody mRequestBody;

        public ProgressRequestBody(RequestBody requestBody) {
            mRequestBody = requestBody;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return mRequestBody.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return mRequestBody.contentLength();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {

            BufferedSink bufferedSink = Okio.buffer(getSink(sink));
            mRequestBody.writeTo(bufferedSink);
            bufferedSink.flush();
        }

        private Sink getSink(BufferedSink sink) throws IOException {
            return new ForwardingSink(sink){

                long total = contentLength();
                long sum = 0;
                @Override
                public void write(Buffer source, long byteCount) throws IOException {
                    super.write(source, byteCount);

                    sum = sum + byteCount;
                    Log.d(sum + "--TAG--" + Thread.currentThread().getName(), "write: " + total);
                    Log.e("--TAG--" + byteCount, "write: " + ((int)(sum * 100.0F / total)));

                }
            };
        }
    }
    /**
     * 添加进度监听的ResponseBody
     */
    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        public ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }


        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @Override
        public BufferedSource source() {

            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }



        private Source source(Source source) {

            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);

                    Log.d("--TAG--", "read: " + byteCount);
                    long length = bytesRead == -1 ? 0 : bytesRead;
                    totalBytesRead = totalBytesRead + length;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }


            };
        }
    }

    interface ProgressListener {

        /**
         * @param bytesRead     已下载字节数
         * @param contentLength 总字节数
         * @param done          是否下载完成
         */
        void update(long bytesRead, long contentLength, boolean done);
    }

    public void getInfo(View view) {

        Call<Person> personInfo = mApiService.getPersonInfo(100);
        personInfo.enqueue(new Callback<Person>() {
            @Override
            public void onResponse(Call<Person> call, Response<Person> response) {

                Log.d("--TAG--", "onResponse: " + Thread.currentThread().getName());
                Log.d("--TAG--", "onResponse: " + response.body());

                Log.d("--TAG--","network response = " + response.raw().networkResponse());
                Log.d("--TAG--","cache response = " + response.raw().cacheResponse());
            }

            @Override
            public void onFailure(Call<Person> call, Throwable t) {


            }
        });
    }

    public void postInfo(View view) {

        Call<Person> personInfo = mApiService.getPersonInfo("张三");
        personInfo.enqueue(new Callback<Person>() {
            @Override
            public void onResponse(Call<Person> call, Response<Person> response) {

                Log.d("--TAG--", "onResponse: " + Thread.currentThread().getName());
                Log.d("--TAG--", "onResponse: " + response.body());

                Log.d("--TAG--","network response = " + response.raw().networkResponse());
                Log.d("--TAG--","cache response = " + response.raw().cacheResponse());
            }

            @Override
            public void onFailure(Call<Person> call, Throwable t) {

            }
        });
    }

    public void downInfo(View view) {

        Call<ResponseBody> responseBodyCall = mApiService.downloadFile("defg");
        responseBodyCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, final Response<ResponseBody> response) {

                mRxPermissions
                        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .subscribe(new Consumer<Boolean>() {
                            @Override
                            public void accept(Boolean aBoolean) throws Exception {

                                if(aBoolean) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {

                                            ResponseBody body = response.body();
                                            String filesDir = getFilePath();
//                                            File file = new File(filesDir + File.separator + "测试222.doc");
                                            long fileSize = body.contentLength();
//
//                                            BufferedSource source = body.source();
//                                            long length = 0;
//                                            long total = 0;
//
//                                            try {
//                                                BufferedSink sink = Okio.buffer(Okio.sink(file));
//                                                Buffer buffer = sink.buffer();
//                                                while ((length = (source.read(buffer, 10 * 1024))) != -1){
//                                                    sink.emit();
//                                                    total = total + length;
//
//                                                    int progress = ((int)(total * 100.0 / fileSize));
//                                                    Log.d("--TAG111--", "run: " + progress + "%");
//                                                }
//
//                                            } catch (Exception e) {
//                                                e.printStackTrace();
//                                            }
//                                            source.close();
//                                            sink.close();


                                            File file1 = new File(filesDir + File.separator + "测试111.doc");

                                            InputStream inputStream = null;
                                            OutputStream outputStream = null;

                                            try {
                                                byte[] fileReader = new byte[4096];

                                                long fileSizeDownloaded = 0;

                                                inputStream = body.byteStream();
                                                outputStream = new FileOutputStream(file1);

                                                while (true) {
                                                    int read = inputStream.read(fileReader);

                                                    if (read == -1) {
                                                        break;
                                                    }

                                                    outputStream.write(fileReader, 0, read);

                                                    fileSizeDownloaded += read;

                                                    Log.d("--TAG--", "file download: " + fileSizeDownloaded + " of " + fileSize);
                                                }

                                                outputStream.flush();
                                                Log.d("--TAG--", "onResponse: " + "成功");

                                            } catch (IOException e) {

                                                e.printStackTrace();
                                                Log.d("--TAG--", "onResponse: " + "失败");

                                            } finally {
                                                try {
                                                    if (inputStream != null) {
                                                        inputStream.close();
                                                    }
                                                    if (outputStream != null) {
                                                        outputStream.close();
                                                    }
                                                } catch (IOException e){
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    }).start();
                                } else {

                                    Toast.makeText(OtherActivity.this, "拒接了读写权限", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });
    }

    public void upInfo(View view) {

        String filesDir = getFilePath();
        File file = new File(filesDir + File.separator + "测试.doc");

//        String fileNameByTimeStamp = FormatUtil.getTimeStamp() + ".jpg";
//        File file = new File(imgPath);
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.create(requestFile);
//        MultipartBody.Part body = MultipartBody.Part.createFormData("app_user_header", fileNameByTimeStamp, requestFile);

        Call<ResponseBody> responseBodyCall = mApiService.uploadFile("defg",body);
        responseBodyCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d("--TAG--", "onResponse: " + response.body());
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

            }
        });

    }

    interface APIService{

        @GET("param")
        Call<Person> getPersonInfo(@Query("age") int age);

        @POST("param")
        Call<Person> getPersonInfo(@Query("name") String name);

        //大文件时要加不然会OOM
        @Streaming
        @GET("customer")
        Call<ResponseBody> downloadFile(@Query("abc") String abc);

        @Multipart
        @POST("customer")
        Call<ResponseBody> uploadFile(@Query("def") String def,@Part MultipartBody.Part file);
    }

    public String getFilePath() {

        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {

            cachePath = mContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS).getPath();
//            cachePath = Environment.getExternalStorageDirectory().getPath() + File.separator + "myDownload";
        } else {

            cachePath = mContext.getFilesDir().getPath();
        }
        if (!new File(cachePath).mkdir()) {
            Log.d("--TAG--", "getFilePath: " + "不存在且创建");
        } else {

            Log.d("--TAG--", "getFilePath: " + "已存在");
        }

        return cachePath;
    }

    class Person{

        String name;
        int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Override
        public String toString() {
            return "Person{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }
}
