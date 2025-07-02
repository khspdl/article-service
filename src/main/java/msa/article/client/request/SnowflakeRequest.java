package msa.article.client.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SnowflakeRequest {

    private String host;
    private String service;
}
