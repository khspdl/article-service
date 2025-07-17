package msa.article.client.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnowflakeResponse {
    private String host;
    private String service;
    private Long snowflakeId;
    private String error;
}
