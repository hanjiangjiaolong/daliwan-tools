import javafx.application.Platform;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;

/** 探一下本机 JavaFX 怎么解析字体名：逻辑字体 monospace 到底落到哪个真实字体。 */
public class FontProbe {

    public static void main(String[] args) {
        Platform.startup(() -> {
            Font rich = Font.font("monospace", FontPosture.ITALIC, 13);
            System.out.println("RichTextFX 行号默认字体 -> " + rich.getFamily() + " " + rich.getSize());
            System.out.println("getDefault -> " + Font.getDefault().getFamily());
            for (String name : new String[]{
                    "Cascadia Mono", "Consolas", "Courier New", "Lucida Console",
                    "monospace", "sans-serif", "Microsoft YaHei UI"}) {
                Font font = Font.font(name, 13);
                System.out.println("  " + name + " -> " + font.getFamily());
            }
            for (String name : new String[]{"Cascadia Mono", "Consolas", "monospace"}) {
                Text text = new Text("0123456789");
                text.setFont(Font.font(name, 13));
                System.out.println("  数字宽度 " + name + " = " + text.getLayoutBounds().getWidth()
                        + "（实际字体 " + text.getFont().getFamily() + "）");
            }
            Platform.exit();
        });
    }
}
