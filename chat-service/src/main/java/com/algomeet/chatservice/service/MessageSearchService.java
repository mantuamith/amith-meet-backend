package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.msgsearch.SearchMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageTextSearchDao;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageSearchService {

    private final MessageTextSearchDao searchDao;
    private final MessageMapper messageMapper;

    public List<SearchMessageResponse> search(String viewer,
                                              String otherUser,
                                              String q,
                                              int page,
                                              int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<MessageDocument> docs = searchDao.searchVisibleByText(viewer, otherUser, q, pageable);

        return docs.stream().map(d -> {
            Double score = null;
            // score is attached by Mongo driver as a meta-field; Spring maps it into the raw Document if needed.
            // If you need it, you can re-read via MongoTemplate or store in doc via a transient field.
            // For simplicity, leave score = null or extend MessageDocument with a @Transient Double score.
            MessageResponse mr = messageMapper.toResponse(d);
            return SearchMessageResponse.builder()
                    .message(mr)
                    .score(score)
                    .snippet(mr.getText()) // basic; you can add highlight later
                    .build();
        }).toList();
    }
}
