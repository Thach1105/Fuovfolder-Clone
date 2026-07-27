package com.fuoverflow.source.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.CheckScoreConfigResponse;
import com.fuoverflow.source.api.dto.CheckScoreResponse;
import com.fuoverflow.source.persistence.AppSettingEntity;
import com.fuoverflow.source.persistence.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class CheckScoreService {
    private static final String AUTHORIZE_KEY_SETTING = "ask4help.authorize_key";
    private static final String CHECK_SCORE_URL_SETTING = "ask4help.check_score_url";

    private final AppSettingRepository settings;
    private final RestClient restClient;
    private final String checkScoreUrl;

    public CheckScoreService(
            AppSettingRepository settings,
            RestClient.Builder restClientBuilder,
            @Value("${ask4help.check-score-url:https://api.ask-4-help.com/api/v2/api/check-score}") String checkScoreUrl) {
        this.settings = settings;
        this.restClient = restClientBuilder.build();
        this.checkScoreUrl = checkScoreUrl;
    }

    @Transactional(readOnly = true)
    public CheckScoreConfigResponse config() {
        return new CheckScoreConfigResponse(
                settings.existsById(AUTHORIZE_KEY_SETTING),
                settings.findById(CHECK_SCORE_URL_SETTING).map(AppSettingEntity::getValue).orElse(checkScoreUrl));
    }

    @Transactional
    public CheckScoreConfigResponse updateConfig(String authorizeKey, String checkScoreUrl) {
        if (authorizeKey != null && !authorizeKey.isBlank()) {
            String normalized = authorizeKey.trim();
            AppSettingEntity setting = settings.findById(AUTHORIZE_KEY_SETTING)
                    .orElseGet(() -> new AppSettingEntity(AUTHORIZE_KEY_SETTING, normalized));
            setting.setValue(normalized);
            settings.save(setting);
        }

        String normalizedUrl = checkScoreUrl.trim();
        AppSettingEntity urlSetting = settings.findById(CHECK_SCORE_URL_SETTING)
                .orElseGet(() -> new AppSettingEntity(CHECK_SCORE_URL_SETTING, normalizedUrl));
        urlSetting.setValue(normalizedUrl);
        settings.save(urlSetting);

        return new CheckScoreConfigResponse(settings.existsById(AUTHORIZE_KEY_SETTING), normalizedUrl);
    }

    @Transactional(readOnly = true)
    public CheckScoreResponse check(MultipartFile file) {
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

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        String targetUrl = settings.findById(CHECK_SCORE_URL_SETTING)
                .map(AppSettingEntity::getValue)
                .orElse(checkScoreUrl);

        Ask4HelpResponse response;
        try {
            response = restClient.post()
                    .uri(targetUrl)
                    .header("X-Authorize-Key", authorizeKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Ask4HelpResponse.class);
        } catch (RestClientException e) {
            throw new BadRequestException("CHECK_SCORE_FAILED", "Dịch vụ chấm điểm xử lý thất bại.");
        }

        if (response == null || response.data() == null) {
            throw new BadRequestException("CHECK_SCORE_BAD_RESPONSE", "Dịch vụ chấm điểm trả về dữ liệu không hợp lệ.");
        }
        return response.data();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Ask4HelpResponse(int status, String message, CheckScoreResponse data) {
    }
}
