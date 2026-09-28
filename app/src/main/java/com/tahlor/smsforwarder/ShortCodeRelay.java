package com.tahlor.smsforwarder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShortCodeRelay {
    static final long WINDOW_MS = 5L * 60L * 1000L;
    private static final Pattern COMMAND = Pattern.compile(
            "^\\s*\\[([+]?[-() 0-9.]{3,24})\\]\\s*(.*?)\\s*$",
            Pattern.DOTALL);

    private static final Pattern CMD_PREFIX =
            Pattern.compile("^(?:\\[CMD\\]|CMD)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MGMT_HELP =
            Pattern.compile("(?i)^(HELP|COMMANDS|INFO)$");
    private static final Pattern MGMT_STATUS =
            Pattern.compile("(?i)^STATUS$");
    private static final Pattern MGMT_LIST =
            Pattern.compile("(?i)^(LIST|RULES|CONFIG)$");
    private static final Pattern MGMT_ALLOW =
            Pattern.compile("(?i)^(ALLOW|AUTHORIZE|WHITE)\\s+(.+)$");
    private static final Pattern MGMT_UNALLOW =
            Pattern.compile("(?i)^(UNALLOW|UNAUTHORIZE|DEL(?:ETE)?\\s+ALLOW)\\s+(.+)$");
    private static final Pattern MGMT_BLOCK =
            Pattern.compile("(?i)^(BLOCK|BLACK)\\s+(.+)$");
    private static final Pattern MGMT_UNBLOCK =
            Pattern.compile("(?i)^(UNBLOCK|DEL(?:ETE)?\\s+BLOCK)\\s+(.+)$");
    private static final Pattern MGMT_MODE =
            Pattern.compile("(?i)^(?:MODE|AUTO|DEFAULT)\\s+(.+)$");
    private static final Pattern MGMT_AUTH =
            Pattern.compile("(?i)^(?:AUTH|SECURITY)\\s+(.+)$");
    private static final Pattern MGMT_PREFER =
            Pattern.compile("(?i)^(?:PREFER|AUTOFORWARD)\\s+(.+)$");
    private static final Pattern MGMT_UNPREFER =
            Pattern.compile("(?i)^(?:UNPREFER|NOAUTOFORWARD)\\s+(.+)$");

    private ShortCodeRelay() {}

    static Command parseCommand(String body) {
        if (body == null) return null;
        Matcher matcher = COMMAND.matcher(body);
        if (!matcher.matches()) return null;
        String destination = normalizeDestination(matcher.group(1));
        if (destination.isEmpty()) return null;
        return new Command(destination, matcher.group(2).trim());
    }

    static ManagementCommand parseManagementCommand(String body) {
        if (body == null) return null;
        String s = body.trim();
        if (s.isEmpty()) return null;

        Matcher prefixMatcher = CMD_PREFIX.matcher(s);
        if (prefixMatcher.find()) s = s.substring(prefixMatcher.end()).trim();

        if (MGMT_HELP.matcher(s).matches()) {
            return new ManagementCommand(ManagementAction.HELP, "");
        }
        if (MGMT_STATUS.matcher(s).matches()) {
            return new ManagementCommand(ManagementAction.STATUS, "");
        }
        if (MGMT_LIST.matcher(s).matches()) {
            return new ManagementCommand(ManagementAction.LIST, "");
        }

        Matcher matcher = MGMT_ALLOW.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.ALLOW, matcher.group(2).trim());
        }
        matcher = MGMT_UNALLOW.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.UNALLOW, matcher.group(2).trim());
        }
        matcher = MGMT_BLOCK.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.BLOCK, matcher.group(2).trim());
        }
        matcher = MGMT_UNBLOCK.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.UNBLOCK, matcher.group(2).trim());
        }
        matcher = MGMT_MODE.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.MODE, matcher.group(1).trim());
        }
        matcher = MGMT_AUTH.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.AUTH, matcher.group(1).trim());
        }
        matcher = MGMT_PREFER.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.PREFER, matcher.group(1).trim());
        }
        matcher = MGMT_UNPREFER.matcher(s);
        if (matcher.matches()) {
            return new ManagementCommand(ManagementAction.UNPREFER, matcher.group(1).trim());
        }
        return null;
    }

    static PhoneProfile findRegisteredProfile(List<PhoneProfile> profiles, String sender) {
        if (profiles == null) return null;
        for (PhoneProfile profile : profiles) {
            if (profile != null && sameAddress(sender, profile.number)) return profile;
        }
        return null;
    }

    static String normalizeDestination(String value) {
        String digits = digitsOnly(value);
        return digits.length() >= 3 && digits.length() <= 15 ? digits : "";
    }

    static boolean isRoutableDestination(String value) {
        return !normalizeDestination(value).isEmpty();
    }

    static boolean isShortCode(String value) {
        String digits = normalizeDestination(value);
        return digits.length() >= 3 && digits.length() <= 6;
    }

    static String formatDestination(String destination) {
        String digits = digitsOnly(destination);
        if (digits.length() == 6) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        return destination == null ? "" : destination;
    }

    static String formatShortCode(String shortCode) {
        return formatDestination(shortCode);
    }

    static String formatSenderForDisplay(String sender) {
        String digits = digitsOnly(sender);
        if (digits.length() == 11 && digits.startsWith("1")) {
            return "+1 " + digits.substring(1, 4) + "-" + digits.substring(4, 7)
                    + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6)
                    + "-" + digits.substring(6);
        }
        if (digits.length() == 6) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        if (digits.length() == 5) {
            return digits.substring(0, 2) + "-" + digits.substring(2);
        }
        if (digits.length() == 4) {
            return digits.substring(0, 2) + "-" + digits.substring(2);
        }
        return sender == null ? "" : sender;
    }

    static boolean sameAddress(String first, String second) {
        String a = digitsOnly(first);
        String b = digitsOnly(second);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() == 11 && a.startsWith("1") && b.length() == 10) {
            return a.substring(1).equals(b);
        }
        if (b.length() == 11 && b.startsWith("1") && a.length() == 10) {
            return b.substring(1).equals(a);
        }
        return false;
    }

    static boolean senderMatchesDestination(String sender, String destination) {
        return sameAddress(sender, destination);
    }

    static boolean senderIsShortCode(String sender, String shortCode) {
        return isShortCode(shortCode) && digitsOnly(sender).equals(digitsOnly(shortCode));
    }

    private static String digitsOnly(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }

    enum ManagementAction {
        ALLOW,
        UNALLOW,
        BLOCK,
        UNBLOCK,
        MODE,
        AUTH,
        PREFER,
        UNPREFER,
        LIST,
        HELP,
        STATUS
    }

    static final class ManagementCommand {
        final ManagementAction action;
        final String argument;

        ManagementCommand(ManagementAction action, String argument) {
            this.action = action;
            this.argument = argument == null ? "" : argument.trim();
        }
    }

    static final class Command {
        final String destination;
        final String payload;

        Command(String destination, String payload) {
            this.destination = destination;
            this.payload = payload;
        }
    }
}
