package com.xg.platform.contracts.skill;

import java.util.Map;

public record UpdateSkillConfigRequest(
        Boolean enabled,
        String apiKey,
        Map<String, String> env
) {
}
