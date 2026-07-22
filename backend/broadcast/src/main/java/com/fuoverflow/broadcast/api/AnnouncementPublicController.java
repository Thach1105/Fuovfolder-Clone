package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementPublicController {

    private final AnnouncementService announcementService;

    public AnnouncementPublicController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/active")
    public ApiResponse<List<AnnouncementActiveResponse>> getActive() {
        return ApiResponse.ok(announcementService.getActiveForUsers());
    }
}
