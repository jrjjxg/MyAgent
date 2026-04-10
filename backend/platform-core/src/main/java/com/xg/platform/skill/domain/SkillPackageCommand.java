package com.xg.platform.skill.domain;
import com.xg.platform.skill.runtime.SkillCommandRunner;

public record SkillPackageCommand(
        String commandId,
        String relativePath,
        SkillCommandRunner runner,
        boolean backgroundSuggested
) {
    public SkillPackageCommand {
        commandId = commandId == null ? "" : commandId.trim();
        relativePath = relativePath == null ? "" : relativePath.replace('\\', '/').trim();
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
    }
}
