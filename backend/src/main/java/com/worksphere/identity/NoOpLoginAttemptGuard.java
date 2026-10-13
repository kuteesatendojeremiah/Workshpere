package com.worksphere.identity;

import org.springframework.stereotype.Component;

@Component
public class NoOpLoginAttemptGuard implements LoginAttemptGuard {

    @Override
    public void beforeAttempt(String email) {
    }

    @Override
    public void onFailure(String email) {
    }

    @Override
    public void onSuccess(String email) {
    }
}
