package com.algomeet.mediaservice.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileAccessEntry {

    @Field("userKey")
    private String userKey;
    
    
    @Field("refCount")
    private Integer refCount;

    /**
     * VIEW, DOWNLOAD, SHARE
     */
    @Field("permissions")
    private Set<FilePermission> permissions;
}
