package org.bitwisemadness.infinitedungeon.announce;

import java.util.Map;

/**
 * How a config-driven announcement text becomes a player-visible message.
 *
 * <p>Pure logic, so the wording rules are unit-testable without a game
 * runtime. Tokens are written {@code {name}} in the config text and replaced
 * by the supplied values; a token with no value stays as written, so a
 * feature can add placeholders without breaking older configs.</p>
 */
public final class AnnouncementText {

    private AnnouncementText() {
    }

    /**
     * Renders one announcement text, replacing each {@code {token}} with its
     * value.
     *
     * @param template the config text
     * @param tokens   the values to substitute, keyed without the braces
     */
    public static String render(String template, Map<String, String> tokens) {
        if (template == null) {
            return "";
        }
        String rendered = template;
        if (tokens != null) {
            for (Map.Entry<String, String> token : tokens.entrySet()) {
                if (token.getKey() == null) {
                    continue;
                }
                rendered = rendered.replace("{" + token.getKey() + "}",
                        token.getValue() == null ? "" : token.getValue());
            }
        }
        return rendered;
    }
}
