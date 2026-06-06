package com.fuoverflow.user.api.dto;

import java.util.List;
import java.util.Map;

public record PermissionCatalogResponse(Map<String, List<PermissionItemResponse>> modules) {
}
