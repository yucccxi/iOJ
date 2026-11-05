package me.iynotuo.judge.captcha;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.iynotuo.judge.captcha.dto.CaptchaCreateRequest;
import me.iynotuo.judge.captcha.dto.CaptchaVO;
import me.iynotuo.judge.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public CAPTCHA issuance endpoint. The answer is only embedded in the returned image; the response
 * must never be cached.
 */
@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CaptchaVO>> create(@Valid @RequestBody CaptchaCreateRequest request,
                                                         HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        CaptchaVO captcha = captchaService.issue(request.purpose(), clientIp);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(captcha));
    }
}
