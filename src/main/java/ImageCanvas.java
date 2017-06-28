import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is used to distribute images over the network.
 * It implements the {@link Serializable} Iface and has a single transient
 * member variable that holds the image that will be sent over the network.
 */

public class ImageCanvas implements Serializable {
    transient List<BufferedImage> images;

    public ImageCanvas() {
        images = new LinkedList<>();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(images.size());
        for (BufferedImage eachImage : images) {
            ImageIO.write(eachImage, "png", out);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        final int imageCount = in.readInt();
        images = new ArrayList<>(imageCount);
        for (int i = 0; i < imageCount; i++) {
            images.add(ImageIO.read(in));
        }
    }

    public String toString() {
        return "BufferedImage";
    }
}