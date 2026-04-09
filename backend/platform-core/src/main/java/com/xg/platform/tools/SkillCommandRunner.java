package com.xg.platform.tools;

import java.nio.file.Path;
import java.util.Locale;

public enum SkillCommandRunner {
    PYTHON,
    POWERSHELL,
    BASH,
    CMD;

    public static SkillCommandRunner infer(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".py")) {
            return PYTHON;
        }
        if (fileName.endsWith(".ps1")) {
            return POWERSHELL;
        }
        if (fileName.endsWith(".sh")) {
            return BASH;
        }
        if (fileName.endsWith(".cmd") || fileName.endsWith(".bat")) {
            return CMD;
        }
        return null;
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
