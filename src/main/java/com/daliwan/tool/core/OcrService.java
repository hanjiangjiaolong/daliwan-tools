package com.daliwan.tool.core;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.HardwareConfig;
import io.github.mymonstercat.ocr.config.ParamConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR 文字识别，基于内嵌的 RapidOCR（PaddleOCR 的 ONNX 移植，PP-OCRv4 模型）。
 *
 * <p>引擎动态库与模型都随 jar 分发，<b>不需要用户再装任何外部程序</b>（早期版本依赖
 * Tesseract，得用户自己下载安装、配语言包，中文效果还一般）。PP-OCRv4 的检测 +
 * 方向分类 + 识别三件套对中英文混排、截图、扫描件都比较稳。
 *
 * <p>两个使用上的约束：
 *
 * <ul>
 *     <li><b>引擎按 JVM 单例</b>：RapidOCR 一次进程只允许一种推理引擎与模型，
 *         首次 {@code runOcr} 时的配置即固定，所以这里固定用 V4，不提供运行时切换；</li>
 *     <li><b>推理不是线程安全的</b>：所有 {@code runOcr} 串行化，避免并发调用踩进
 *         同一个 native 上下文。界面侧本来也是一次只识别一张图，串行不影响体验。</li>
 * </ul>
 *
 * <p>首次识别要把 28 MB 模型解压到临时目录，会比后续慢几百毫秒；
 * 可以用 {@link #warmUp()} 在进页面时先把引擎拉起来，把这段等待挪到用户看图的时候。
 */
public final class OcrService {

    /** 固定使用的模型：V4 精度高于 V3，体积只大一点，CPU 上单次仍是百毫秒级。 */
    private static final Model MODEL = Model.ONNX_PPOCR_V4;

    private static final Object LOCK = new Object();

    /** 懒加载的引擎实例；volatile 保证其他线程能看到完整初始化后的对象。 */
    private static volatile InferenceEngine engine;

    private OcrService() {
    }

    /** 识别选项。 */
    public record Options(boolean detectAngle) {

        /** 默认选项：不额外做方向检测（多数截图/扫描件本来就是正的）。 */
        public static final Options DEFAULT = new Options(false);

        public static Options of(boolean detectAngle) {
            return detectAngle ? new Options(true) : DEFAULT;
        }
    }

    /**
     * 预先加载引擎（解压动态库 + 准备模型目录）。
     *
     * <p>失败也不算致命，真正的报错留到识别时再抛；这里只是把等待时间提前。
     *
     * @throws OcrException 引擎无法加载（通常是缺少 VC++ 运行库或临时目录不可写）
     */
    public static void warmUp() throws OcrException {
        engine();
    }

    /** 引擎是否已经就绪。 */
    public static boolean isReady() {
        return engine != null;
    }

    /** 返回引擎描述，用于界面展示。 */
    public static String describeEngine() {
        return "RapidOCR · PP-OCRv4（内置，无需安装）";
    }

    /**
     * 识别图片中的文字。
     *
     * @param image  图片文件
     * @param options 识别选项
     * @return 识别出的文本，按文本框从上到下组织，块之间用换行分隔
     * @throws OcrException 图片不存在、引擎加载失败或没识别出文字
     */
    public static String recognize(Path image, Options options) throws OcrException {
        if (image == null || !Files.isRegularFile(image)) {
            throw new OcrException("图片不存在：" + image);
        }
        ParamConfig param = ParamConfig.getDefaultConfig();
        boolean detectAngle = options != null && options.detectAngle();
        param.setDoAngle(detectAngle);
        param.setMostAngle(detectAngle);

        OcrResult result;
        synchronized (LOCK) {
            // native 推理上下文不是线程安全的，串行调用
            result = engine().runOcr(image.toAbsolutePath().toString(), param);
        }
        if (result == null) {
            throw new OcrException("识别未返回结果");
        }
        String text = result.getStrRes() == null ? "" : result.getStrRes().strip();
        if (text.isEmpty()) {
            throw new OcrException("未识别到文字，试试更清晰的图片，或勾选「自动纠正方向」");
        }
        return text;
    }

    /**
     * 识别并返回分块文本：一个文本框一行，顺序即引擎输出的从上到下、从左到右。
     *
     * <p>界面只要整体文本时用 {@link #recognize} 即可；分块结果便于将来做
     * 「按行复制」「框选定位」这类功能。
     */
    public static List<String> recognizeBlocks(Path image, Options options) throws OcrException {
        if (image == null || !Files.isRegularFile(image)) {
            throw new OcrException("图片不存在：" + image);
        }
        ParamConfig param = ParamConfig.getDefaultConfig();
        boolean detectAngle = options != null && options.detectAngle();
        param.setDoAngle(detectAngle);
        param.setMostAngle(detectAngle);

        OcrResult result;
        synchronized (LOCK) {
            result = engine().runOcr(image.toAbsolutePath().toString(), param);
        }
        if (result == null || result.getTextBlocks() == null) {
            throw new OcrException("识别未返回结果");
        }
        List<String> lines = new ArrayList<>();
        for (TextBlock block : result.getTextBlocks()) {
            String text = block.getText();
            if (text != null && !text.isBlank()) {
                lines.add(text.strip());
            }
        }
        if (lines.isEmpty()) {
            throw new OcrException("未识别到文字，试试更清晰的图片，或勾选「自动纠正方向」");
        }
        return lines;
    }

    /**
     * 取引擎实例，首次调用时加载。
     *
     * <p>RapidOCR 加载失败抛的是各种 {@link Throwable}（缺库时是
     * {@link UnsatisfiedLinkError}），所以这里兜 {@code Throwable} 统一转成
     * {@link OcrException}，界面上给的是人能看懂的原因而不是一串堆栈。
     */
    private static InferenceEngine engine() throws OcrException {
        InferenceEngine local = engine;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (engine == null) {
                try {
                    engine = InferenceEngine.getInstance(MODEL, HardwareConfig.getOnnxConfig());
                } catch (Throwable t) {
                    throw new OcrException("OCR 引擎加载失败：" + rootMessage(t)
                            + "（若为系统库缺失，安装 Visual C++ 运行库后重试）", t);
                }
            }
            return engine;
        }
    }

    private static String rootMessage(Throwable t) {
        String message = t.getMessage();
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        Throwable cause = t.getCause();
        return cause == null ? t.toString() : rootMessage(cause);
    }
}
