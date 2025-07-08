package msa.article.client;

import msa.article.client.response.SnowflakeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "snowflake-service", url = "${snowflake-service.url:http://localhost:8110}")
public interface SnowflakeServiceClient {

    @GetMapping("/snowflake")
    SnowflakeResponse getId(@RequestParam("host") String host);
}
