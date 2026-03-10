package com.enterprise.blog.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.enterprise.blog.search.document.BlogDocument;
import com.enterprise.blog.search.repository.BlogSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final BlogSearchRepository blogSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public Page<BlogDocument> search(String query, String category, List<String> tags, Pageable pageable) {
        if (!StringUtils.hasText(query) && !StringUtils.hasText(category) && (tags == null || tags.isEmpty())) {
            return blogSearchRepository.findAll(pageable);
        }

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(query)) {
            MultiMatchQuery multiMatch = MultiMatchQuery.of(m -> m
                    .fields("title^3", "excerpt^2", "content")
                    .query(query)
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO"));
            boolQuery.must(Query.of(q -> q.multiMatch(multiMatch)));
        }

        if (StringUtils.hasText(category)) {
            boolQuery.filter(Query.of(q -> q.term(t -> t.field("category").value(category))));
        }

        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                boolQuery.filter(Query.of(q -> q.term(t -> t.field("tags").value(tag))));
            }
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()))
                .withPageable(pageable)
                .build();

        SearchHits<BlogDocument> hits = elasticsearchOperations.search(nativeQuery, BlogDocument.class);
        List<BlogDocument> results = hits.stream().map(SearchHit::getContent).toList();

        return new PageImpl<>(results, pageable, hits.getTotalHits());
    }

    public void indexPost(BlogDocument document) {
        log.info("Indexing blog post: {}", document.getId());
        blogSearchRepository.save(document);
    }

    public void deletePost(String postId) {
        log.info("Removing blog post from index: {}", postId);
        blogSearchRepository.deleteById(postId);
    }
}
