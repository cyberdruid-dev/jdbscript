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

    private static final Pattern SITE_LINK = Pattern.compile("https://jdbscript\\.org/([^)`\\s]+)");

    private static final Pattern REPO_LINK = Pattern.compile(
            "https://(?:raw\\.githubusercontent\\.com/cyberdruid-dev/jdbscript/main"
                    + "|github\\.com/cyberdruid-dev/jdbscript/(?:tree|blob)/main)/([^)`\\s]+)");

    @Test
    public void every_repo_and_site_link_in_llms_txt_should_point_to_an_existing_file() throws Exception {
        String llmsTxt = Files.readString(LLMS_TXT);
        List<String> paths = new ArrayList<>();
        Matcher repoLinks = REPO_LINK.matcher(llmsTxt);
        while (repoLinks.find()) {
            paths.add(repoLinks.group(1));
        }
        Matcher siteLinks = SITE_LINK.matcher(llmsTxt);
        while (siteLinks.find()) {
            paths.add("site/public/" + siteLinks.group(1));
        }

        assertThat(paths).describedAs("links in " + LLMS_TXT).isNotEmpty();
        assertThat(paths).describedAs("paths missing from the repo, linked from " + LLMS_TXT)
                .filteredOn(path -> !Files.exists(Path.of(path)))
                .isEmpty();
    }
}
