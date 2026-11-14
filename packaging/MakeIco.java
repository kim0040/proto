import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * [도구] PNG 아이콘들을 윈도우용 .ico 한 파일로 묶는다 (16/24/32/48/64/128/256, 각 칸은 PNG 압축).
 * 사용: java packaging/MakeIco.java <아이콘 PNG 폴더> <출력 .ico>
 * icon-N.png가 있으면 그대로 쓰고, 없는 크기(24)는 가장 가까운 큰 그림을 줄여서 만든다.
 */
public final class MakeIco {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private MakeIco() {
    }

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]);
        Path output = Path.of(args[1]);
        List<byte[]> images = new ArrayList<>();
        for (int size : SIZES) {
            images.add(png(image(source, size)));
        }
        Files.write(output, build(images));
    }

    /** 원본 PNG가 있으면 읽고, 없으면 256 그림을 부드럽게 줄인다 */
    private static BufferedImage image(Path source, int size) throws IOException {
        Path file = source.resolve("icon-" + size + ".png");
        if (Files.exists(file)) {
            return ImageIO.read(file.toFile());
        }
        BufferedImage big = ImageIO.read(source.resolve("icon-256.png").toFile());
        return shrink(big, size);
    }

    /** 반씩 줄이다가 마지막에 목표 크기로 맞춘다 (한 번에 줄이면 거칠다) */
    private static BufferedImage shrink(BufferedImage image, int size) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= size) {
            current = scale(current, current.getWidth() / 2);
        }
        return current.getWidth() == size ? current : scale(current, size);
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** ICONDIR(6바이트) + 칸마다 ICONDIRENTRY(16바이트) + PNG 데이터 */
    private static byte[] build(List<byte[]> images) {
        int total = 6 + 16 * images.size();
        for (byte[] data : images) {
            total += data.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
        int offset = 6 + 16 * images.size();
        for (int i = 0; i < images.size(); i++) {
            int size = SIZES[i];
            buffer.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size));
            buffer.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            buffer.putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        for (byte[] data : images) {
            buffer.put(data);
        }
        return buffer.array();
    }
}
