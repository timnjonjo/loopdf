// dto/PagedResponse.java
package com.loopdfs.rdas.dtos;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Generic paginated envelope returned by all list endpoints.
 */
@Value
@Builder
public class PagedResponse<T> {
    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean last;
}