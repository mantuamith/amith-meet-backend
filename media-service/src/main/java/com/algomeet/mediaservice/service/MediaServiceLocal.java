package com.algomeet.mediaservice.service;

import java.nio.file.Path;

public interface MediaServiceLocal extends MediaService{   
    Path read(String userkEy, String filename);
}
