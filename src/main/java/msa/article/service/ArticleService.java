package msa.article.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import msa.article.client.SnowflakeServiceClient;
import msa.article.client.request.SnowflakeRequest;
import msa.article.client.response.SnowflakeResponse;
import msa.article.entity.Article;
import msa.article.entity.BoardArticleCount;
import msa.article.repository.ArticleRepository;
import msa.article.repository.BoardArticleCountRepository;
import msa.article.service.request.ArticleCreateRequest;
import msa.article.service.request.ArticleUpdateRequest;
import msa.article.service.response.ArticlePageResponse;
import msa.article.service.response.ArticleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {

    private final SnowflakeServiceClient snowflakeServiceClient;
    private final ArticleRepository articleRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;

    @Transactional
    public ArticleResponse create(ArticleCreateRequest request) throws UnknownHostException {

        Article article = articleRepository.save(
                Article.create(getSnowflakeId(),
                        request.getTitle(),
                        request.getContent(),
                        request.getBoardId(),
                        request.getWriterId()
                )
        );

        int result = boardArticleCountRepository.increase(request.getBoardId());
        if(result == 0) {
            boardArticleCountRepository.save(
                    BoardArticleCount.init(request.getBoardId(), 1L)
            );
        }

        return ArticleResponse.from(article);
    }

    @Retry(name = "snowflake", fallbackMethod = "fallbackId")
    @CircuitBreaker(name = "snowflake", fallbackMethod = "fallbackId")
    private Long getSnowflakeId() throws UnknownHostException {
        String ip = InetAddress.getLocalHost().getHostAddress();

        SnowflakeRequest snowflakeRequest = SnowflakeRequest.builder()
                .service("article-service")
                .host(ip)
                .build();

        log.info("Before call snowflake service");
        SnowflakeResponse response = snowflakeServiceClient.getId(snowflakeRequest);
        log.info("After call snowflake service");

        return response.getSnowflakeId();
    }

    private Long fallbackId(Throwable throwable) {
        log.error("SnowflakeID作成失敗", throwable);
        throw new IllegalStateException("Failed to get snowflake id", throwable);
    }

    @Transactional
    public ArticleResponse update(Long articleId, ArticleUpdateRequest request) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        article.update(request.getTitle(), request.getContent());

        return ArticleResponse.from(article);
    }

    public ArticleResponse read(Long articleId) {
        return ArticleResponse.from(articleRepository.findById(articleId).orElseThrow());
    }

    @Transactional
    public void delete(Long articleId) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        articleRepository.delete(article);
        boardArticleCountRepository.decrease(article.getBoardId());
    }

    public ArticlePageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticlePageResponse.of(
                articleRepository.findAll(boardId, (page - 1) * pageSize, pageSize)
                        .stream().map(ArticleResponse::from)
                        .toList(),
                articleRepository.count(boardId,
                        PageLimitCalculator.calculatePageLimit(page, pageSize, 10L))
        );
    }

    public List<ArticleResponse> readAllInfiniteScroll(Long boardId, Long pageSize, Long lastArticleId) {
        List<Article> articles = lastArticleId == null ?
                articleRepository.findAllInfiniteScroll(boardId, pageSize) :
                articleRepository.findAllInfiniteScroll(boardId, pageSize, lastArticleId);

        return articles.stream()
                .map(ArticleResponse::from)
                .toList();
    }

    public Long count(Long boardId) {
        return boardArticleCountRepository.findById(boardId)
                .map(BoardArticleCount::getArticleCount)
                .orElse(0L);
    }
}
