package com.fuoverflow.forum.application;

import com.fuoverflow.common.forum.PollOptionWriter;
import com.fuoverflow.forum.persistence.PollOptionEntity;
import com.fuoverflow.forum.persistence.PollOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ForumPollOptionWriter implements PollOptionWriter {
    private final PollOptionRepository pollOptionRepository;

    public ForumPollOptionWriter(PollOptionRepository pollOptionRepository) {
        this.pollOptionRepository = pollOptionRepository;
    }

    @Override
    @Transactional
    public void write(UUID threadId, List<String> labels) {
        Instant now = Instant.now();
        int order = 0;
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            pollOptionRepository.save(PollOptionEntity.create(
                    UUID.randomUUID(), threadId, label.trim(), order++, now));
        }
    }
}
