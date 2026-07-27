package com.fuoverflow.source.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.voucher.VoucherRedemptionPort;
import com.fuoverflow.source.api.dto.CheckScoreConfigResponse;
import com.fuoverflow.source.api.dto.CheckScoreResponse;
import com.fuoverflow.source.api.dto.CheckScoreSubjectsResponse;
import com.fuoverflow.source.persistence.AppSettingEntity;
import com.fuoverflow.source.persistence.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CheckScoreService {
    private static final int CHECK_SCORE_PRICE_POINTS = 29_000;
    private static final String AUTHORIZE_KEY_SETTING = "ask4help.authorize_key";
    private static final String XSRF_COOKIE_SETTING = "ask4help.xsrf_cookie";
    private static final String CHECK_SCORE_URL_SETTING = "ask4help.check_score_url";

    private final AppSettingRepository settings;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PointsWalletService walletService;
    private final VoucherRedemptionPort voucherService;
    private final String checkScoreUrl;
    private final String subjectsUrl;

    public CheckScoreService(
            AppSettingRepository settings,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            PointsWalletService walletService,
            VoucherRedemptionPort voucherService,
            @Value("${ask4help.check-score-url:https://api.ask-4-help.com/api/v2/api/check-score}") String checkScoreUrl,
            @Value("${ask4help.subjects-url:https://api.ask-4-help.com/api/v2/api/subjects}") String subjectsUrl) {
        this.settings = settings;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.walletService = walletService;
        this.voucherService = voucherService;
        this.checkScoreUrl = checkScoreUrl;
        this.subjectsUrl = subjectsUrl;
    }

    @Transactional(readOnly = true)
    public CheckScoreConfigResponse config() {
        return new CheckScoreConfigResponse(
                settings.existsById(AUTHORIZE_KEY_SETTING),
                settings.existsById(XSRF_COOKIE_SETTING),
                settings.findById(AUTHORIZE_KEY_SETTING).map(AppSettingEntity::getValue).orElse(""),
                settings.findById(XSRF_COOKIE_SETTING).map(AppSettingEntity::getValue).orElse(""),
                settings.findById(CHECK_SCORE_URL_SETTING).map(AppSettingEntity::getValue).orElse(checkScoreUrl));
    }

    @Transactional
    public CheckScoreConfigResponse updateConfig(String authorizeKey, String xsrfCookie, String checkScoreUrl) {
        if (authorizeKey != null && !authorizeKey.isBlank()) {
            String normalized = authorizeKey.trim();
            AppSettingEntity setting = settings.findById(AUTHORIZE_KEY_SETTING)
                    .orElseGet(() -> new AppSettingEntity(AUTHORIZE_KEY_SETTING, normalized));
            setting.setValue(normalized);
            settings.save(setting);
        }

        if (xsrfCookie != null && !xsrfCookie.isBlank()) {
            String normalizedCookie = xsrfCookie.trim();
            AppSettingEntity setting = settings.findById(XSRF_COOKIE_SETTING)
                    .orElseGet(() -> new AppSettingEntity(XSRF_COOKIE_SETTING, normalizedCookie));
            setting.setValue(normalizedCookie);
            settings.save(setting);
        }

        String normalizedUrl = checkScoreUrl.trim();
        AppSettingEntity urlSetting = settings.findById(CHECK_SCORE_URL_SETTING)
                .orElseGet(() -> new AppSettingEntity(CHECK_SCORE_URL_SETTING, normalizedUrl));
        urlSetting.setValue(normalizedUrl);
        settings.save(urlSetting);

        return new CheckScoreConfigResponse(
                settings.existsById(AUTHORIZE_KEY_SETTING),
                settings.existsById(XSRF_COOKIE_SETTING),
                settings.findById(AUTHORIZE_KEY_SETTING).map(AppSettingEntity::getValue).orElse(""),
                settings.findById(XSRF_COOKIE_SETTING).map(AppSettingEntity::getValue).orElse(""),
                normalizedUrl);
    }

    @Transactional
    public CheckScoreResponse check(UUID userId, MultipartFile file, String voucherCode) {
        String authorizeKey = settings.findById(AUTHORIZE_KEY_SETTING)
                .map(AppSettingEntity::getValue)
                .orElseThrow(() -> new NotFoundException("CHECK_SCORE_KEY_MISSING", "Chưa cấu hình token chấm điểm."));

        if (file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "Vui lòng chọn file cần chấm điểm.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("INVALID_FILE", "Không đọc được file tải lên.");
        }

        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipartBody = buildMultipartBody(
                boundary,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "submission.dat",
                bytes);

        String targetUrl = settings.findById(CHECK_SCORE_URL_SETTING)
                .map(AppSettingEntity::getValue)
                .orElse(checkScoreUrl);
        String xsrfCookie = settings.findById(XSRF_COOKIE_SETTING)
                .map(AppSettingEntity::getValue)
                .orElse(null);

        String responseBody;
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(targetUrl)
                    .accept(MediaType.ALL)
                    .header(HttpHeaders.USER_AGENT, "PostmanRuntime/7.53.0")
                    .header("X-Authorize-Key", authorizeKey)
                    .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
                    .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary));
            if (xsrfCookie != null && !xsrfCookie.isBlank()) {
                request = request.header(HttpHeaders.COOKIE, xsrfCookie);
            }
            responseBody = request.body(multipartBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new BadRequestException("CHECK_SCORE_FAILED", "Dịch vụ chấm điểm xử lý thất bại.");
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new BadRequestException("CHECK_SCORE_BAD_RESPONSE", "Dịch vụ chấm điểm trả về dữ liệu không hợp lệ.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isObject()) {
                throw new BadRequestException("CHECK_SCORE_BAD_RESPONSE", "Dịch vụ chấm điểm trả về dữ liệu không hợp lệ.");
            }
            CheckScoreResponse externalResult = new CheckScoreResponse(
                    text(data, "totalQuestions", "total_questions", "totalquestions"),
                    text(data, "score"),
                    text(data, "subject"),
                    text(data, "correctAnswers", "correct_answers", "correctanswers"),
                    text(data, "charged"),
                    CHECK_SCORE_PRICE_POINTS,
                    0,
                    CHECK_SCORE_PRICE_POINTS);
            if (externalResult.score() == null || externalResult.correctAnswers() == null || externalResult.totalQuestions() == null) {
                throw new BadRequestException("CHECK_SCORE_EMPTY_RESULT", "Dịch vụ chấm điểm chưa trả về kết quả. Kiểm tra lại token, cookie và URL.");
            }
            UUID transactionId = UUID.randomUUID();
            int chargedPoints = CHECK_SCORE_PRICE_POINTS;
            int discountPoints = 0;
            if (voucherCode != null && !voucherCode.isBlank()) {
                var voucherResult = voucherService.redeem(
                        voucherCode, userId, "check_score", transactionId, CHECK_SCORE_PRICE_POINTS);
                chargedPoints = voucherResult.finalPoints();
                discountPoints = voucherResult.discountPoints();
            }
            if (chargedPoints > 0) {
                walletService.debit(
                        userId,
                        chargedPoints,
                        "Check điểm: " + externalResult.subject(),
                        PointsWalletService.SOURCE_CHECK_SCORE,
                        transactionId);
            }
            return new CheckScoreResponse(
                    externalResult.totalQuestions(),
                    externalResult.score(),
                    externalResult.subject(),
                    externalResult.correctAnswers(),
                    externalResult.charged(),
                    CHECK_SCORE_PRICE_POINTS,
                    discountPoints,
                    chargedPoints);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("CHECK_SCORE_BAD_RESPONSE", "Dịch vụ chấm điểm trả về dữ liệu không hợp lệ.");
        }
    }

    @Transactional(readOnly = true)
    public CheckScoreSubjectsResponse subjects() {
        String xsrfCookie = settings.findById(XSRF_COOKIE_SETTING)
                .map(AppSettingEntity::getValue)
                .orElse(null);

        String responseBody;
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(subjectsUrl)
                    .accept(MediaType.ALL)
                    .header(HttpHeaders.USER_AGENT, "PostmanRuntime/7.53.0")
                    .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
            if (xsrfCookie != null && !xsrfCookie.isBlank()) {
                request = request.header(HttpHeaders.COOKIE, xsrfCookie);
            }
            responseBody = request.retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new BadRequestException("CHECK_SCORE_SUBJECTS_FAILED", "Không tải được danh sách môn có thể check điểm.");
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new BadRequestException("CHECK_SCORE_SUBJECTS_BAD_RESPONSE", "Dịch vụ trả về danh sách môn không hợp lệ.");
        }

        try {
            JsonNode data = objectMapper.readTree(responseBody).path("data");
            JsonNode itemsNode = data.path("items");
            if (!itemsNode.isArray()) {
                throw new BadRequestException("CHECK_SCORE_SUBJECTS_BAD_RESPONSE", "Dịch vụ trả về danh sách môn không hợp lệ.");
            }
            List<String> items = new ArrayList<>();
            itemsNode.forEach(item -> items.add(item.asText()));
            return new CheckScoreSubjectsResponse(
                    items,
                    data.path("total").asInt(items.size()),
                    data.path("page").asInt(1),
                    data.path("limit").asInt(items.size()),
                    data.path("hasPrevious").asBoolean(false),
                    data.path("hasNext").asBoolean(false),
                    data.path("totalPages").asInt(1));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("CHECK_SCORE_SUBJECTS_BAD_RESPONSE", "Dịch vụ trả về danh sách môn không hợp lệ.");
        }
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private byte[] buildMultipartBody(String boundary, String filename, byte[] fileBytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + sanitizeFilename(filename) + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("INVALID_FILE", "Không đọc được file tải lên.");
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replace("\\", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
