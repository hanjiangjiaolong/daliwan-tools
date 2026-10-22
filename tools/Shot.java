import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

/** 抓屏幕右下角一小块（临时验证工具，不参与打包）。 */
public class Shot {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/verify/screen.png";
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle area = new Rectangle(bounds.x + bounds.width - 520, bounds.y + bounds.height - 360, 520, 360);
        BufferedImage image = new Robot().createScreenCapture(area);
        ImageIO.write(image, "png", new File(out));
        System.out.println("[shot] " + out);
    }
}
