package com.algomeet.chatservice.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
public class MediaItem {
	@Field("isEncrypted")
    private Boolean encrypted = false;  // true if media content is ciphertext
	
	@Field("mediaId")
    private String mediaId;
	
    @Field("url")
    private String url;

    @Field("mimeType")
    private String mimeType; // e.g., image/jpeg

    @Field("fileName")
    private String fileName;

    @Field("size")
    private Long size; // bytes

    @Field("duration")
    private Long duration; // ms or sec

    @Field("thumbnailUrl")
    private String thumbnailUrl;
}
