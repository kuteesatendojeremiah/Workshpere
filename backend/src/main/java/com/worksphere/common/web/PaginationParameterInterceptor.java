package com.worksphere.common.web;

import com.worksphere.common.error.InvalidRequestParameterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects malformed {@code page} / {@code size} query parameters with a 400.
 *
 * <p>Spring Data's built-in resolver silently falls back to defaults for values like
 * {@code page=abc}, which hides client bugs and makes "garbage in" look like success. A size that
 * is merely too big is not rejected here - Spring clamps it to {@code max-page-size}
 * (see application.yml), so the response is always bounded.
 */
public class PaginationParameterInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        requireInteger(request, "page", 0);
        requireInteger(request, "size", 1);
        return true;
    }

    private void requireInteger(HttpServletRequest request, String name, int minimum) {
        String raw = request.getParameter(name);
        if (raw == null) {
            return;
        }
        try {
            if (Integer.parseInt(raw) < minimum) {
                throw new InvalidRequestParameterException(name + " must be at least " + minimum);
            }
        } catch (NumberFormatException e) {
            throw new InvalidRequestParameterException(name + " must be a whole number");
        }
    }
}
