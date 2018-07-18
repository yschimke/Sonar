// Copyright 2004-present Facebook. All Rights Reserved.
package com.facebook.sonar.plugins.network.okhttp;

import com.facebook.sonar.plugins.network.NetworkSonarPlugin;
import okhttp3.Call;
import okhttp3.EventListener;

import java.util.Random;

public class SonarOkhttpInterceptor implements EventListener.Factory {
    public final NetworkSonarPlugin plugin;
    private final Random random = new Random();

    public SonarOkhttpInterceptor(NetworkSonarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EventListener create(Call call) {
        int randInt = random.nextInt(Integer.MAX_VALUE - 1) + 1;

        return new SonarOkhttpListener(plugin, call, randInt);
    }
}
