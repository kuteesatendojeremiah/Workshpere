package com.worksphere.identity;

public record TokenPairResponse(String accessToken, String refreshToken, String tokenType) {

    public static TokenPairResponse of(String accessToken, String refreshToken) {
        return new TokenPairResponse(accessToken, refreshToken, "Bearer");
    }
}
