package com.algomeet.chatservice.client.decoder;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;

public class FeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new FeignException.NotFound("Not Found", response.request(), null, null);
            case 403 -> new FeignException.Forbidden("Forbidden", response.request(), null, null);
            default -> new FeignException.InternalServerError("Server Error", response.request(), null, null);
        };
    }
}