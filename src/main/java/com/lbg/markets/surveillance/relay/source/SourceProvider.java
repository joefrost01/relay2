package com.lbg.markets.surveillance.relay.source;

import com.lbg.markets.surveillance.relay.domain.Feed;
import com.lbg.markets.surveillance.relay.domain.FileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

public interface SourceProvider {
    Stream<FileDescriptor> list(Feed feed) throws IOException;

    InputStream open(FileDescriptor file, long offset) throws IOException;
}
