package com.lbg.markets.surveillance.relay.sink;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@ApplicationScoped
@IfBuildProfile("prod")
public class GcsSink implements Sink {
    @Override
    public long write(String destPath, InputStream in, long offset, long length, Map<String, String> metadata) throws IOException {
        return 0;
    }
}
