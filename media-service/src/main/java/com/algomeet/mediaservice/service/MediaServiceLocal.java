package com.algomeet.mediaservice.service;

import java.nio.file.Path;

public interface MediaServiceLocal extends MediaService{   
    Path download(String userkEy, String filename);
}
