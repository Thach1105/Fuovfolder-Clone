package com.fuoverflow.common.forum;

import java.util.List;
import java.util.UUID;

public interface PollOptionWriter {
    void write(UUID threadId, List<String> labels);
}
