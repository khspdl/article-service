package msa.article.client;

import msa.article.client.request.SnowflakeRequest;
import msa.article.client.response.SnowflakeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "snowflake-service", url = "${snowflake-service.url}")
public interface SnowflakeServiceClient {

    @GetMapping("/snowflake")
    SnowflakeResponse getId(@RequestBody SnowflakeRequest request);
}
