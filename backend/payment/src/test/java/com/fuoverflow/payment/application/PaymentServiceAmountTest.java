package com.fuoverflow.payment.application;

import com.fuoverflow.payment.api.dto.CreatePaymentLinkRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceAmountTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createPaymentLinkRequest_shouldRejectFractionalVndAmount() {
        CreatePaymentLinkRequest request = new CreatePaymentLinkRequest(
                new BigDecimal("1000.50"),
                "http://localhost/return",
                "http://localhost/cancel",
                "nap diem"
        );

        Set<ConstraintViolation<CreatePaymentLinkRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount must be a whole-number VND value");
    }

    @Test
    void createPaymentLinkRequest_shouldRejectTooSmallAmount() {
        CreatePaymentLinkRequest request = new CreatePaymentLinkRequest(
                new BigDecimal("999"),
                "http://localhost/return",
                "http://localhost/cancel",
                "nap diem"
        );

        Set<ConstraintViolation<CreatePaymentLinkRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("amount must be >= 1000 VND");
    }

    @Test
    void createPaymentLinkRequest_shouldAcceptWholeNumberVndAmount() {
        CreatePaymentLinkRequest request = new CreatePaymentLinkRequest(
                new BigDecimal("1000"),
                "http://localhost/return",
                "http://localhost/cancel",
                "nap diem"
        );

        Set<ConstraintViolation<CreatePaymentLinkRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
