package org.jdbscript;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class LlmsTxtLinksTest {

    private static final Path LLMS_TXT = Path.of("site/public/llms.txt");

    private static final Pattern REPO_LINK = Pattern.compile(
            "https://(?:raw\\.githubusercontent\\.com/cyberdruid-dev/jdbscript/main"
                    + "|github\\.com/cyberdruid-dev/jdbscript/(?:tree|blob)/main)/([^)\\s]+)");

    @Test
    public void every_repo_link_in_llms_txt_should_point_to_an_existing_file() throws Exception {
        Matcher matcher = REPO_LINK.matcher(Files.readString(LLMS_TXT));
        List<String> paths = new ArrayList<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }

        assertThat(paths).describedAs("repo links in " + LLMS_TXT).isNotEmpty();
        assertThat(paths).describedAs("paths missing from the repo, linked from " + LLMS_TXT)
                .filteredOn(path -> !Files.exists(Path.of(path)))
                .isEmpty();
    }
}
