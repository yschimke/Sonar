package com.facebook.sonar.plugins.network.okhttp;

import android.util.Log;
import com.facebook.sonar.plugins.network.NetworkReporter;
import com.facebook.sonar.plugins.network.NetworkSonarPlugin;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SonarOkhttpListener extends EventListener {
    private final NetworkSonarPlugin plugin;
    private final Call call;
    private int id;

    public SonarOkhttpListener(NetworkSonarPlugin plugin, Call call, int id) {
        this.plugin = plugin;
        this.call = call;
        this.id = id;
    }

    @Override
    public void callStart(Call call) {
        Request request = call.request();
        plugin.reportRequest(convertRequest(request, id));
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        super.responseHeadersEnd(call, response);
    }

    @Override
    public void callEnd(Call call) {

        plugin.reportResponse(responseInfo);
    }

    @Override
    public void callFailed(Call call, IOException ioe) {

        plugin.reportResponse(responseInfo);
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        plugin.reportRequest(convertRequest(request, id));
        Response response = chain.proceed(request);
        ResponseBody body = response.body();
        NetworkReporter.ResponseInfo responseInfo = convertResponse(response, body, id);
        plugin.reportResponse(responseInfo);
        // Creating new response as can't used response.body() more than once
        return response
                .newBuilder()
                .body(ResponseBody.create(body.contentType(), responseInfo.body))
                .build();
    }

    private NetworkReporter.RequestInfo convertRequest(Request request, int identifier) {
        List<NetworkReporter.Header> headers = convertHeader(request.headers());
        NetworkReporter.RequestInfo info = new NetworkReporter.RequestInfo();
        info.requestId = String.valueOf(identifier);
        info.timeStamp = System.currentTimeMillis();
        info.headers = headers;
        info.method = request.method();
        info.uri = request.url().toString();
        if (request.body() != null) {
            info.body = bodyToByteArray(request);
        }   

        return info;
    }

    private NetworkReporter.ResponseInfo convertResponse(Response response, ResponseBody body, int identifier) {
        List<NetworkReporter.Header> headers = convertHeader(response.headers());
        NetworkReporter.ResponseInfo info = new NetworkReporter.ResponseInfo();
        info.requestId = String.valueOf(identifier);
        info.timeStamp = response.receivedResponseAtMillis();
        info.statusCode = response.code();
        info.headers = headers;
        try {
            info.body = body.bytes();
        } catch (IOException e) {
            Log.e("Sonar", e.toString());
        }
        return info;
    }

    private List<NetworkReporter.Header> convertHeader(Headers headers) {
        List<NetworkReporter.Header> list = new ArrayList<>();

        Set<String> keys = headers.names();
        for (String key : keys) {
            list.add(new NetworkReporter.Header(key, headers.get(key)));
        }
        return list;
    }
}
