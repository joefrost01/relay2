package com.lbg.markets.surveillance.relay.sink;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface Sink {
    long write(String destPath, InputStream in, long offset, long length, Map<String, String> metadata)
            throws IOException;
}
