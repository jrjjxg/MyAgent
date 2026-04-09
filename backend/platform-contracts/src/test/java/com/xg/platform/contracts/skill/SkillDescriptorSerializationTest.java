package com.xg.platform.contracts.skill;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDescriptorSerializationTest {

    @Test
    void supportsJavaSerializationForGraphCheckpointState() throws Exception {
        SkillDescriptor descriptor = new SkillDescriptor(
                "skill-1",
                "local/skill-1",
                "Skill description",
                "Skill summary",
                "https://example.com/skill-1",
                "node",
                List.of("API_KEY"),
                List.of("mentions skill"),
                List.of("shell_command"),
                List.of("shell_command", "read_file"),
                List.of("references/guide.md"),
                List.of("docs"),
                List.of("npm run skill"),
                true,
                false,
                "default",
                "load_skill",
                "subagent",
                true,
                "workspace",
                "/skills/skill-1",
                "ready",
                ""
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(descriptor);
        }

        SkillDescriptor restored;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(output.toByteArray()))) {
            restored = (SkillDescriptor) objectInputStream.readObject();
        }

        assertThat(restored).isEqualTo(descriptor);
    }
}
