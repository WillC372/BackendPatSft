import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PreToolUse hook of the engineering team for the Java repositories (BackendPatSft, AIPatSft): blocks the git
 * commands that would change `main` or rewrite history. It does the same as proteger-main.mjs, which the frontend and
 * the database use, and tests/ProtegerMain.test.mjs of FrontendPatSft checks both with the same cases.
 * The team works in branches (mejora/..., calidad/...) and pull requests; merging (which deploys) is the owner's.
 */
public class ProtegerMain {

    static final Pattern GIT = Pattern.compile("\\bgit\\b");
    static final Pattern CD = Pattern.compile("^cd\\s+(\"([^\"]+)\"|'([^']+)'|(\\S+))");
    static final Pattern GIT_ARGS = Pattern.compile("\\bgit\\s+(.*)$", Pattern.DOTALL);
    static final Pattern OPTION_WITH_VALUE = Pattern.compile("^(-C|-c|--git-dir|--work-tree|--namespace)(?:\\s+|=)(\"[^\"]+\"|'[^']+'|\\S+)\\s+");
    static final Pattern FLAG = Pattern.compile("^(--[\\w-]+(?:=\\S+)?|-[pP])\\s+");
    static final Pattern SWITCH = Pattern.compile("^(?:switch|checkout)\\s+(?:(?:-[cCbB]|--create|--force-create)\\s+)?([^\\s-][^\\s]*)");

    static final Map<String, String> switched = new HashMap<>();

    public static void main(String[] args) throws IOException {
        String raw = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> input;
        try {
            Object parsed = new Json(raw.isBlank() ? "{}" : raw).value();
            input = parsed instanceof Map<?, ?> m ? m : Map.of();
        } catch (RuntimeException e) {
            return; // not something this hook understands
        }
        Object toolInput = input.get("tool_input");
        Object commandValue = toolInput instanceof Map<?, ?> m ? m.get("command") : null;
        String command = commandValue == null ? "" : commandValue.toString();
        if (!GIT.matcher(command).find()) return;

        Object cwd = input.get("cwd");
        Path dir = Path.of(cwd == null ? System.getProperty("user.dir") : cwd.toString());
        // Each part of the command (split by && ; ||) with the directory it runs in (following `cd`).
        for (String part : command.split("&&|\\|\\||;|\\n")) {
            part = part.strip();
            Matcher cd = CD.matcher(part);
            if (cd.find()) {
                String target = cd.group(2) != null ? cd.group(2) : cd.group(3) != null ? cd.group(3) : cd.group(4);
                String home = System.getenv().getOrDefault("HOME", "~");
                dir = dir.resolve(target.replaceFirst("^~(?=/|$)", Matcher.quoteReplacement(home))).normalize();
                continue;
            }
            Matcher git = GIT_ARGS.matcher(part);
            if (!git.find()) continue;
            // Global options before the subcommand (-C dir, -c key=value, --no-pager, --git-dir=...) are skipped; -C changes the folder.
            String rest = git.group(1);
            Path where = dir;
            while (true) {
                Matcher opt = OPTION_WITH_VALUE.matcher(rest);
                if (!opt.find()) {
                    opt = FLAG.matcher(rest);
                    if (!opt.find()) break;
                }
                if (opt.group(1).equals("-C")) where = where.resolve(opt.group(2).replaceAll("^[\"']|[\"']$", "")).normalize();
                rest = rest.substring(opt.end());
            }
            check(rest, where);
        }
    }

    static void check(String args, Path where) {
        Matcher sw = SWITCH.matcher(args);
        if (sw.find() && !args.matches("(?s)^checkout\\s+--\\s.*")) {
            switched.put(where.toString(), sw.group(1).replaceFirst("^origin/", ""));
            return;
        }
        if (find("^push\\b", args)) {
            if (find("(^|\\s)(-f|--force|--force-with-lease|--mirror|--delete|-d)(\\s|=|$)", args)) block("push forzado o que borra ramas");
            if (find("(^|[\\s:+])(refs/heads/)?(main|master)(\\s|$)", args)) block("push a main");
            List<String> refs = new ArrayList<>();
            for (String a : args.replaceFirst("^push\\s*", "").split("\\s+")) if (!a.isEmpty() && !a.startsWith("-")) refs.add(a);
            List<String> targets = refs.size() > 1 ? refs.subList(1, refs.size()) : List.of();
            if (targets.stream().anyMatch(r -> r.startsWith("+"))) block("push forzado o que borra ramas");
            // Without a branch, or with HEAD, the push goes to the branch you are on.
            if ((refs.size() < 2 || targets.stream().anyMatch(r -> r.matches("^HEAD(:.*)?$"))) && onMain(where)) block("push desde main");
        }
        if (find("^commit\\b", args) && onMain(where)) block("commit en main");
        if (find("^merge\\b", args) && onMain(where)) block("merge en main");
        if (find("^reset\\b.*--hard", args)) block("reset --hard");
        if (find("^branch\\b.*\\s-(D|d|-delete)\\b.*\\b(main|master)\\b", args)) block("borrar main");
        if (find("^rebase\\b", args) && onMain(where)) block("rebase de main");
    }

    static boolean find(String regex, String text) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(text).find();
    }

    static boolean onMain(Path where) {
        String branch = switched.containsKey(where.toString()) ? switched.get(where.toString()) : branchIn(where);
        return branch.equals("main") || branch.equals("master");
    }

    // A `git switch`/`checkout` earlier in the same command changes the branch the later parts run on.
    static String branchIn(Path dir) {
        try {
            Process p = new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "--abbrev-ref", "HEAD")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return p.waitFor() == 0 ? out : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    static void block(String why) {
        System.err.println("Bloqueado por el equipo de ingeniería: " + why
                + ". Trabaja en una rama mejora/H-XXX-... y abre un pull request; el merge a main lo hace el dueño.");
        System.exit(2);
    }

    /** A minimal JSON reader for the hook's input: objects, arrays, strings, numbers, booleans and null. */
    static final class Json {
        final String s;
        int i;

        Json(String s) {
            this.s = s;
        }

        Object value() {
            skip();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> map = new HashMap<>();
                i++;
                skip();
                if (s.charAt(i) == '}') { i++; return map; }
                while (true) {
                    skip();
                    String k = str();
                    skip();
                    i++; // :
                    map.put(k, value());
                    skip();
                    if (s.charAt(i++) == '}') return map;
                }
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                i++;
                skip();
                if (s.charAt(i) == ']') { i++; return list; }
                while (true) {
                    list.add(value());
                    skip();
                    if (s.charAt(i++) == ']') return list;
                }
            }
            if (c == '"') return str();
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
            return s.substring(start, i);
        }

        String str() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e);
                }
            }
        }

        void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
