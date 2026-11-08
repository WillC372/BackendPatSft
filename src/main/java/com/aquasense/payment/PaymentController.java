package com.aquasense.payment;

import com.aquasense.security.CurrentUser;
import com.aquasense.security.RateLimiter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService service;
    private final RateLimiter rateLimiter;

    public PaymentController(PaymentService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/plan")
    public PaymentDtos.PlanStatus plan() {
        return service.status(CurrentUser.id());
    }

    @PostMapping("/checkout")
    public PaymentDtos.CheckoutResponse checkout() {
        rateLimiter.check("checkout:" + CurrentUser.id());
        return service.checkout(CurrentUser.id());
    }

    @PostMapping("/verify")
    public PaymentDtos.PaymentView verify(@Valid @RequestBody PaymentDtos.VerifyRequest request) {
        rateLimiter.check("verify-payment:" + CurrentUser.id());
        return service.verify(CurrentUser.id(), request.referenceCode());
    }

    @GetMapping
    public List<PaymentDtos.PaymentView> history() {
        return service.history(CurrentUser.id());
    }

    /** PayU confirmation page (server to server, x-www-form-urlencoded). Public: the signature is validated. */
    @PostMapping(value = "/payu/confirmation", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> confirmation(@RequestParam Map<String, String> params) {
        return service.confirm(params) ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }
}
