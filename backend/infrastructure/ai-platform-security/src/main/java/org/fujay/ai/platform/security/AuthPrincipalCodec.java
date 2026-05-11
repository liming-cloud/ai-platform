package org.fujay.ai.platform.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.springframework.stereotype.Component;

@Component
public class AuthPrincipalCodec {

    public String encode(AuthPrincipal principal) {
        return encodePart(principal.actorType())
                + "."
                + encodePart(String.valueOf(principal.tenantId().value()))
                + "."
                + encodePart(String.valueOf(principal.actorId()));
    }

    public AuthPrincipal decode(String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split("\\.");
        if (parts.length != 3) {
            throw new PlatformException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return new AuthPrincipal(
                    new TenantId(Long.parseLong(decodePart(parts[1]))),
                    decodePart(parts[0]),
                    Long.parseLong(decodePart(parts[2])));
        } catch (NumberFormatException ex) {
            throw new PlatformException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
