package com.daliwan.tool.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文本比对：按行计算差异，采用 Myers 差分算法（O((N+M)D) 时间、O(N+M) 空间），
 * 比朴素的 LCS 动态规划更适合长文本。
 *
 * <p>输出是一串带类型的行：相同、仅左侧有（删除）、仅右侧有（新增）。
 * 左右分屏视图用 {@link #align(List)} 把这些行配成「一左一右」的行对，
 * 行内字符级高亮用 {@link #inlineDiff(String, String)}。
 */
public final class TextDiff {

    /** 行类型。 */
    public enum Type {
        EQUAL(" ", "相同"),
        INSERT("+", "新增"),
        DELETE("-", "删除");

        private final String marker;
        private final String label;

        Type(String marker, String label) {
            this.marker = marker;
            this.label = label;
        }

        /** 单字符前缀，便于纯文本输出。 */
        public String marker() {
            return marker;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 一行比对结果。
     *
     * @param type       类型
     * @param leftNumber 左侧行号（1 起算），新增行为 0
     * @param rightNumber 右侧行号（1 起算），删除行为 0
     * @param text       该行内容
     */
    public record Line(Type type, int leftNumber, int rightNumber, String text) {

        /** 带前缀的展示文本。 */
        public String toDisplay() {
            return type.marker() + " " + text;
        }

        /** 分屏视图用：取该侧的行号文本，无行号（补位）返回空串。 */
        public String sideNumber(boolean left) {
            int n = left ? leftNumber : rightNumber;
            return n == 0 ? "" : Integer.toString(n);
        }
    }

    /** 比对统计。 */
    public record Stats(int added, int deleted, int unchanged) {
        public int total() {
            return added + deleted + unchanged;
        }
    }

    /**
     * 左右分屏的一行：左、右各为一条比对结果，{@code null} 表示该侧留空（补位行）。
     *
     * <p>相等行两侧都非空且内容相同；删除行只有 {@code left}；新增行只有 {@code right}；
     * 「修改」表现为删除行与新增行被配进同一 {@code Row}，侧行内字符级高亮使用。
     */
    public record Row(Line left, Line right) {
    }

    /**
     * 行内差异：左右两行共用 {@code prefixLen} 的前缀与 {@code suffixLen} 的后缀，
     * 中间部分即为高亮片段（左 {@code leftMidLen} 字符，右 {@code rightMidLen} 字符）。
     */
    public record InlineDiff(int prefixLen, int leftMidLen, int rightMidLen, int suffixLen) {
    }

    private TextDiff() {
    }

    /**
     * 比较两段文本（按行）。
     *
     * @param left  左侧文本，null 视为空
     * @param right 右侧文本，null 视为空
     * @return 按阅读顺序排列的比对结果
     */
    public static List<Line> compare(String left, String right) {
        return compare(left, right, false);
    }

    /**
     * 比较两段文本，可选择忽略行内空白差异（行首行尾空白与连续空白压缩后比较，
     * 显示的仍是原始文本）。
     */
    public static List<Line> compare(String left, String right, boolean ignoreWhitespace) {
        List<String> a = splitLines(left);
        List<String> b = splitLines(right);
        List<String> aKeys = ignoreWhitespace ? normalize(a) : a;
        List<String> bKeys = ignoreWhitespace ? normalize(b) : b;
        List<Line> raw = myers(a, b, aKeys, bKeys);
        return renumber(raw);
    }

    /**
     * 把比对结果配成左右分屏的行对：相等行同行展示；变更块里，删除与新增数量相同
     * 时按位置逐行配对（视为「修改」），数量不同时按**相似度**配对（改了参数、加了
     * 一行这类场景会把真正对应的两行配上，而不是按出现顺序硬凑），多余行挂在补位行
     * 上，保证左右行数一致、上下对齐。
     */
    public static List<Row> align(List<Line> lines) {
        List<Row> rows = new ArrayList<>();
        List<Line> pendingDeletes = new ArrayList<>();
        List<Line> pendingInserts = new ArrayList<>();
        for (Line line : lines) {
            switch (line.type()) {
                case EQUAL -> {
                    flushBlock(rows, pendingDeletes, pendingInserts);
                    rows.add(new Row(line, line));
                }
                case DELETE -> pendingDeletes.add(line);
                case INSERT -> pendingInserts.add(line);
            }
        }
        flushBlock(rows, pendingDeletes, pendingInserts);
        return rows;
    }

    /**
     * 行内字符级差异：返回前后缀范围，两侧中间的片段即为需要高亮的部分。
     * 任一行为空时整条对端文本视为高亮片段。
     */
    public static InlineDiff inlineDiff(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        int limit = Math.min(a.length(), b.length());
        int prefix = 0;
        while (prefix < limit && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        int maxSuffix = limit - prefix;
        while (suffix < maxSuffix
                && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) {
            suffix++;
        }
        return new InlineDiff(prefix, a.length() - prefix - suffix,
                b.length() - prefix - suffix, suffix);
    }

    /** 统计新增 / 删除 / 未变行数。 */
    public static Stats stats(List<Line> lines) {
        int added = 0;
        int deleted = 0;
        int unchanged = 0;
        for (Line line : lines) {
            switch (line.type()) {
                case INSERT -> added++;
                case DELETE -> deleted++;
                default -> unchanged++;
            }
        }
        return new Stats(added, deleted, unchanged);
    }

    /** 把比对结果渲染成带 +/- 前缀的纯文本，便于复制。 */
    public static String toText(List<Line> lines) {
        StringBuilder out = new StringBuilder();
        for (Line line : lines) {
            out.append(line.toDisplay()).append('\n');
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- 实现

    static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        // 统一换行符后按行切分，末尾的换行不产生空行
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return List.of(normalized.split("\n", -1));
    }

    /** 忽略空白时的比较键：去掉全部空白字符。 */
    private static List<String> normalize(List<String> lines) {
        List<String> keys = new ArrayList<>(lines.size());
        for (String line : lines) {
            keys.add(line.replaceAll("\\s+", ""));
        }
        return keys;
    }

    /** 相似度配对的最低分（0~1，前后缀重合度）。 */
    private static final double PAIR_THRESHOLD = 0.3;

    /** 变更块超过该规模时跳过相似度配对（O(n·m) 动态规划），退回按序配对。 */
    private static final int MAX_PAIR_BLOCK = 80;

    /** Myers 参与比较的最大行数（两侧之和），超出后直接按整段替换展示。 */
    private static final int MAX_TOTAL_LINES = 6000;

    /** Myers 的最大编辑距离；差异比这还大时继续算下去既不划算也没有可读的结果。 */
    private static final int MAX_EDIT_DISTANCE = 1500;

    /** 把累积的删除/新增块配成行对后追加到结果。 */
    private static void flushBlock(List<Row> rows, List<Line> deletes, List<Line> inserts) {
        int n = deletes.size();
        int m = inserts.size();
        if (n > 0 && m > 0 && n != m) {
            List<Row> matched = pairBySimilarity(deletes, inserts);
            if (matched != null) {
                rows.addAll(matched);
                deletes.clear();
                inserts.clear();
                return;
            }
        }
        // 数量相同：按位置配对（一行改一行的「修改」）；找不到相似行时也退回按序
        int pairs = Math.min(n, m);
        for (int i = 0; i < pairs; i++) {
            rows.add(new Row(deletes.get(i), inserts.get(i)));
        }
        for (int i = pairs; i < n; i++) {
            rows.add(new Row(deletes.get(i), null));
        }
        for (int i = pairs; i < m; i++) {
            rows.add(new Row(null, inserts.get(i)));
        }
        deletes.clear();
        inserts.clear();
    }

    /**
     * 按相似度把删除行与新增行配成修改对（单调不交叉），多余行补位。
     *
     * <p>做法是在变更块上做一次小动态规划：状态 {@code best[i][j]} 表示
     * {@code deletes[i..]} 与 {@code inserts[j..]} 的最优配对总分，配对得分
     * {@code 1 + 相似度}，不配对得 0；只有相似度达到 {@link #PAIR_THRESHOLD}
     * 的组合才允许配对。单行改动（如 {@code throw ...} 只换了参数）会越过
     * 邻近的新增行找到真正对应的那一行。
     *
     * @return 配好的行对；没有任何相似行时返回 {@code null}，由调用方按序配对
     */
    private static List<Row> pairBySimilarity(List<Line> deletes, List<Line> inserts) {
        int n = deletes.size();
        int m = inserts.size();
        if (n > MAX_PAIR_BLOCK || m > MAX_PAIR_BLOCK) {
            return null;
        }
        boolean[][] eligible = new boolean[n][m];
        double[][] sim = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double score = similarity(deletes.get(i).text(), inserts.get(j).text());
                sim[i][j] = score;
                eligible[i][j] = score >= PAIR_THRESHOLD;
            }
        }
        double[][] best = new double[n + 1][m + 1];
        byte[][] pick = new byte[n + 1][m + 1];  // 1=配对 2=跳过删除 3=跳过新增
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                double pairScore = eligible[i][j] ? 1 + sim[i][j] + best[i + 1][j + 1] : -1;
                double skipDel = best[i + 1][j];
                double skipIns = best[i][j + 1];
                if (pairScore >= skipDel && pairScore >= skipIns) {
                    best[i][j] = pairScore;
                    pick[i][j] = 1;
                } else if (skipDel >= skipIns) {
                    best[i][j] = skipDel;
                    pick[i][j] = 2;
                } else {
                    best[i][j] = skipIns;
                    pick[i][j] = 3;
                }
            }
        }
        if (best[0][0] <= 0) {
            return null;
        }
        List<Row> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && pick[i][j] == 1) {
                out.add(new Row(deletes.get(i), inserts.get(j)));
                i++;
                j++;
            } else if (i < n && (j >= m || pick[i][j] == 2)) {
                out.add(new Row(deletes.get(i), null));
                i++;
            } else {
                out.add(new Row(null, inserts.get(j)));
                j++;
            }
        }
        return out;
    }

    /** 两行的相似度（0~1）：公共前后缀字符数占两行总长的比例，O(min(n,m))。 */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        int limit = Math.min(a.length(), b.length());
        int prefix = 0;
        while (prefix < limit && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        int maxSuffix = limit - prefix;
        while (suffix < maxSuffix
                && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) {
            suffix++;
        }
        return (2.0 * (prefix + suffix)) / (a.length() + b.length());
    }

    /** Myers 差分，返回未编号的行序列；行内容取自 {@code a}/{@code b}，比较用 {@code aKeys}/{@code bKeys}。 */
    private static List<Line> myers(List<String> a, List<String> b, List<String> aKeys, List<String> bKeys) {
        int n = a.size();
        int m = b.size();
        if (n == 0 && m == 0) {
            return List.of();
        }
        int max = n + m;
        // 规模太大时 Myers 的中间状态会吃掉几百 MB——每一轮 d 都要留一份
        // int[2*max+1] 的快照，6000 行差异悬殊的文本就要近 300MB。
        // 这时候逐行 diff 对用户也没意义，直接按「整段替换」展示。
        if (max > MAX_TOTAL_LINES) {
            return replaceAll(a, b);
        }
        int offset = max;
        int[] v = new int[2 * max + 1];
        List<int[]> trace = new ArrayList<>();

        int found = -1;
        outer:
        for (int d = 0; d <= max; d++) {
            if (d > MAX_EDIT_DISTANCE) {
                return replaceAll(a, b);
            }
            trace.add(v.clone());
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];
                } else {
                    x = v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && aKeys.get(x).equals(bKeys.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    found = d;
                    break outer;
                }
            }
        }

        List<Line> reversed = new ArrayList<>();
        int x = n;
        int y = m;
        for (int d = found; d > 0; d--) {
            int[] previous = trace.get(d);
            int k = x - y;
            int previousK;
            if (k == -d || (k != d && previous[offset + k - 1] < previous[offset + k + 1])) {
                previousK = k + 1;
            } else {
                previousK = k - 1;
            }
            int previousX = previous[offset + previousK];
            int previousY = previousX - previousK;

            while (x > previousX && y > previousY) {
                reversed.add(new Line(Type.EQUAL, x, y, a.get(x - 1)));
                x--;
                y--;
            }
            if (x == previousX) {
                reversed.add(new Line(Type.INSERT, 0, y, b.get(y - 1)));
            } else {
                reversed.add(new Line(Type.DELETE, x, 0, a.get(x - 1)));
            }
            x = previousX;
            y = previousY;
        }
        while (x > 0 && y > 0) {
            reversed.add(new Line(Type.EQUAL, x, y, a.get(x - 1)));
            x--;
            y--;
        }
        Collections.reverse(reversed);
        return reversed;
    }

    /** 「整段替换」：左侧整段删除、右侧整段新增，用于差异过大时的退化展示。 */
    private static List<Line> replaceAll(List<String> a, List<String> b) {
        List<Line> lines = new ArrayList<>(a.size() + b.size());
        for (int i = a.size(); i >= 1; i--) {
            lines.add(new Line(Type.DELETE, i, 0, a.get(i - 1)));
        }
        for (int i = 1; i <= b.size(); i++) {
            lines.add(new Line(Type.INSERT, 0, i, b.get(i - 1)));
        }
        return lines;
    }

    /** 重新编排行号：左侧编号只在非新增行递增，右侧编号只在非删除行递增。 */
    private static List<Line> renumber(List<Line> lines) {
        List<Line> result = new ArrayList<>(lines.size());
        int leftNumber = 0;
        int rightNumber = 0;
        for (Line line : lines) {
            int left = line.type() == Type.INSERT ? 0 : ++leftNumber;
            int right = line.type() == Type.DELETE ? 0 : ++rightNumber;
            result.add(new Line(line.type(), left, right, line.text()));
        }
        return result;
    }
}
