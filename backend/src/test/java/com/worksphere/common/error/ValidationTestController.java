package com.worksphere.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint used to exercise {@link GlobalExceptionHandler}'s validation path. */
@RestController
class ValidationTestController {

    @PostMapping("/test/validate")
    public void validate(@Valid @RequestBody Payload payload) {
    }

    record Payload(@NotBlank(message = "name must not be blank") String name) {
    }
}
