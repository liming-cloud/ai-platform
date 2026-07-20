package com.aiplatform.identity.presentation.controller;

import com.aiplatform.identity.application.UserInvitationApplicationService;
import com.aiplatform.identity.presentation.dto.UserActivationRequest;
import com.aiplatform.identity.presentation.dto.UserActivationResponse;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/activate")
public class UserActivationController {
    private final UserInvitationApplicationService service;

    public UserActivationController(UserInvitationApplicationService service) {
        this.service = Objects.requireNonNull(service, "用户邀请应用服务不能为空");
    }

    @PostMapping
    public ApiResponse<UserActivationResponse> activate(@Valid @RequestBody UserActivationRequest request) {
        return ApiResponse.success(UserActivationResponse.from(service.activate(request.token(), request.password())));
    }
}
