package com.enterprise.blog.search.repository;

import com.enterprise.blog.search.document.BlogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlogSearchRepository extends ElasticsearchRepository<BlogDocument, String> {

    Page<BlogDocument> findByCategory(String category, Pageable pageable);

    Page<BlogDocument> findByTagsContaining(String tag, Pageable pageable);
}
