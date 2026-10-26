package com.daliwan.tool.core;

/**
 * 去掉 JSON 上多出来的一层转义。
 *
 * <p>接口日志、数据库字段、把 JSON 塞进字符串再打印的场景里，内容常被再转义一次：
 * 引号写成 {@code \"}，反斜杠写成 {@code \\}，有的外面还裹一层引号
 * （因为它是「一个字符串」而不是「一个对象」）。这种文本直接格式化只会报语法错误，
 * 手工删反斜杠又容易漏、容易把正常内容改坏。
 *
 * <p>这里做两件事：
 * <ol>
 *   <li><b>剥包裹</b>：整段是一个完整字符串字面量（首尾一对引号、内部引号都被转义）
 *       时，剥掉外层引号并解开里面的转义。日志里常见一层套一层，最多剥
 *       {@value #MAX_PASSES} 层——再深就不是包裹，而是数据本身了。</li>
 *   <li><b>解残留转义</b>：外层引号已经被别处剥掉、只剩内部反斜杠的数据
 *       （如 {@code [{\"id\":1}]}），直接把转义序列解成字符。</li>
 * </ol>
 *
 * <p>只在「原文本身不是合法 JSON」时才动它：正常 JSON 里也可能出现
 * {@code {"a":"he said \"hi\""}} 这种合法的转义引号，一刀切把 {@code \"} 换成
 * {@code "} 会把它改成非法内容。第一步不受这条限制——完整的字符串字面量本来就不该
 * 被当成文档，剥开是它唯一合理的解释。
 */
public final class JsonUnescape {

    /** 最多剥几层包裹：日志里常见 2~3 层。 */
    private static final int MAX_PASSES = 5;

    private JsonUnescape() {
    }

    /**
     * 去转义的结果。
     *
     * @param text    处理后的文本
     * @param levels  剥掉 / 解开的层数，0 表示没动
     * @param changed 内容是否发生了变化
     */
    public record Result(String text, int levels, boolean changed) {
    }

    /** 去转义；不需要处理时原样返回。 */
    public static Result unescape(String text) {
        if (text == null) {
            return new Result("", 0, false);
        }
        String original = text;
        String current = text.trim();
        int levels = 0;

        // 1) 一层层剥掉外面包裹的引号
        while (levels < MAX_PASSES && isQuotedLiteral(current)) {
            String inner = decode(current, 1, current.length() - 1).trim();
            if (inner.isEmpty() || inner.equals(current)) {
                break;
            }
            current = inner;
            levels++;
        }

        // 2) 外层引号没了、只剩内部转义的数据：原文本不合法时才动，避免改坏正常 JSON
        if (!isValid(current)) {
            String decoded = decode(current, 0, current.length()).trim();
            if (!decoded.equals(current)) {
                current = decoded;
                if (levels == 0) {
                    levels = 1;
                }
            }
        }
        return new Result(current, levels, !current.equals(original));
    }

    /** 只要还原后的文本。 */
    public static String apply(String text) {
        return unescape(text).text();
    }

    /**
     * 是否是一个完整的字符串字面量：首尾一对引号，中间没有「没被转义的引号」。
     *
     * <p>末尾落单的反斜杠不算（{@code "abc\"} 里的结尾引号是被转义的，字面量没闭合）。
     */
    static boolean isQuotedLiteral(String text) {
        if (text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
            return false;
        }
        boolean escaped = false;
        for (int i = 1; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return false;
            }
        }
        return !escaped;
    }

    /**
     * 解区间内的转义序列，区间外的字符不动。
     *
     * <p>认得 JSON 规定的那几种：引号、反斜杠、斜杠、退格、换页、换行、回车、制表符，
     * 以及「反斜杠 u 加四位十六进制」。
     * 认不出来的（比如 Windows 路径里反斜杠后面跟着 users）原样留下，
     * 不去猜——改错比不改更糟。
     */
    static String decode(String text, int from, int to) {
        StringBuilder out = new StringBuilder(Math.max(16, to - from));
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= to) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> i += decodeUnicode(text, i, to, out);
                default -> out.append(c).append(next);
            }
        }
        return out.toString();
    }

    /**
     * 解「反斜杠 u 加四位十六进制」；不足四位或不是十六进制时原样留下。
     *
     * @return 额外吃掉几个字符（解出来是 4，没解出来是 0）
     */
    private static int decodeUnicode(String text, int uIndex, int to, StringBuilder out) {
        int end = uIndex + 5;
        if (end > to || !isHex(text.substring(uIndex + 1, end))) {
            out.append('\\').append('u');
            return 0;
        }
        out.append((char) Integer.parseInt(text.substring(uIndex + 1, end), 16));
        return 4;
    }

    private static boolean isHex(String text) {
        if (text.length() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValid(String text) {
        if (text.isBlank()) {
            return false;
        }
        try {
            JsonFormatter.validate(text);
            return true;
        } catch (JsonFormatException | RuntimeException notJson) {
            return false;
        }
    }
}
