package com.wiley.cms.cochrane.utils;

import java.awt.Color;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.tes.util.Logger;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.SVGConstants;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @author <a href='mailto:osoletskay@wiley.com'>Olga Soletskaya</a>
 * @version 14.06.12
 */
public class ImageUtil {

    public static final int THUMBNAIL_WIDTH = 100;
    public static final int THUMBNAIL_HEIGHT = 100;
    public static final int THUMBNAIL_DIFF_PERCENT = 20;

    private static final int HUNDRED_PERCENT = 100;

    private static final Logger LOG = Logger.getLogger(ImageUtil.class);
    private static IRepository rp = RepositoryFactory.getRepository();

    private static final String IM_CONVERT = "convert ";
    private static final String IM_FILTER = " -filter ";
    private static final String IM_RESIZE = " -resize ";

    private static final TranscodingHints SVG_TRANSCODING_HINTS = new TranscodingHints() {{
            put(ImageTranscoder.KEY_WIDTH, (float) THUMBNAIL_WIDTH);
            put(ImageTranscoder.KEY_HEIGHT, (float) THUMBNAIL_HEIGHT);
            put(ImageTranscoder.KEY_DOM_IMPLEMENTATION, SVGDOMImplementation.getDOMImplementation());
            put(ImageTranscoder.KEY_DOCUMENT_ELEMENT_NAMESPACE_URI, SVGConstants.SVG_NAMESPACE_URI);
            put(ImageTranscoder.KEY_DOCUMENT_ELEMENT, SVGConstants.SVG_SVG_TAG);
            put(ImageTranscoder.KEY_BACKGROUND_COLOR, Color.WHITE);
            put(ImageTranscoder.KEY_XML_PARSER_VALIDATING, false);
        }};

    private ImageUtil() {
    }

    public static boolean canResize(int curWidth, int curHeight, int width, int height, int diffPercent) {
        return Math.abs(curWidth * HUNDRED_PERCENT / curHeight - width * HUNDRED_PERCENT / height) <= diffPercent;
    }

    public static void writeNormal(BufferedImage img, String normalType, String normalPath) throws IOException {
        rp.putImage(img, normalType, normalPath);
    }

    public static void makeThumbnailResize(String imagePath, String thumbnailDir, String thumbnailName,
        int width, int height, String imPath, String imFilter) throws Exception {

        StringBuilder sb = new StringBuilder(imPath);
        String realThumbnailDir = prepareIM(imagePath, thumbnailDir, imFilter, sb);

        sb.append(IM_RESIZE).append(width).append("x").append(height).append(
            "! ").append(realThumbnailDir).append(FilePathCreator.SEPARATOR).append(thumbnailName);

        ProcessHelper.execCommand(sb.toString(), null);
    }

    public static void makeThumbnailCrop(String imagePath, String thumbnailDir, String thumbnailName,
        int width, int height, String imPath, String imFilter) throws Exception {

        StringBuilder sb = new StringBuilder(imPath);
        String realThumbnailDir = prepareIM(imagePath, thumbnailDir, imFilter, sb);

        sb.append(" -thumbnail ").append(width).append("x").append(height).append(
            " -bordercolor white -border ").append(Math.max(width, height)).append(
            " -gravity center -crop ").append(width).append("x").append(height).append("+0+0 +repage ").append(
                realThumbnailDir).append(FilePathCreator.SEPARATOR).append(thumbnailName);

        ProcessHelper.execCommand(sb.toString(), null);
    }

    public static void makeThumbnailExtent(String imagePath, String thumbnailDir, String thumbnailName,
        int width, int height, String imPath, String imFilter) throws Exception {

        StringBuilder sb = new StringBuilder(imPath);
        String realThumbnailDir = prepareIM(imagePath, thumbnailDir, imFilter, sb);

        sb.append(IM_RESIZE).append(width).append("x").append(height).append(
            " -gravity center -extent ").append(width).append("x").append(height).append(
                " ").append(realThumbnailDir).append(FilePathCreator.SEPARATOR).append(thumbnailName);

        File procDir = new File(imPath);
        ProcessHelper.execCommand(sb.toString(), procDir);
    }

    public static void writeThumbnail(BufferedImage img, String thumbnailType, String thumbnailPath,
        int startWidth, int startHeight, int diffPercent) throws IOException, URISyntaxException {

        int width = img.getWidth();
        int height = img.getHeight();

        ThumbnailParams params =  new ThumbnailParams(diffPercent);
        params.calculateThumbnailSize(startWidth, startHeight, width, height);

        //todo why?
        int xSubSampling = width / params.thumbnailWidth;
        int ySubSampling = height / params.thumbnailHeight;

        if (xSubSampling == 0 || ySubSampling == 0) {
            writeNormal(img, thumbnailType, thumbnailPath);
            return;
        }

        //long start = System.currentTimeMillis();
        writeThumbnail(img, thumbnailType, thumbnailPath, startWidth, startHeight, params);
        //LOG.error("writeThumbnail2 " + (System.currentTimeMillis() - start));
    }


    public static void writeThumbnail(BufferedImage img, String thumbnailType, String thumbnailPath,
        int thumbnailWidth, int thumbnailHeight, int xSubSampling, int ySubSampling)
        throws IOException, URISyntaxException {

        Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(thumbnailType);
        if (iter.hasNext()) {

            ImageWriter writer = iter.next();
            ImageWriteParam iwp = writer.getDefaultWriteParam();
            iwp.setSourceSubsampling(xSubSampling, ySubSampling, 0, 0);
            iwp.setSourceRegion(new Rectangle(thumbnailWidth * xSubSampling, thumbnailHeight * ySubSampling));
            ImageOutputStream output = rp.getImageOutputStream(thumbnailPath);
            writer.setOutput(output);

            IIOImage iioImage = new IIOImage(img, null, null);
            writer.write(null, iioImage, iwp);
            if (output != null) {
                output.close();
            }
        }
    }

    private static void writeThumbnail(BufferedImage img, String thumbnailType, String thumbnailPath,
        int startWidth, int startHeight, ThumbnailParams p) throws IOException, URISyntaxException {

        Image im = Toolkit.getDefaultToolkit().createImage(img.getSource());
        im = im.getScaledInstance(p.thumbnailWidth, p.thumbnailHeight, Image.SCALE_AREA_AVERAGING);

        if (p.offset > 0) {

            Color cl = img.getGraphics().getColor();
            img.getGraphics().setColor(Color.WHITE);

            if (p.thumbnailWidth > p.thumbnailHeight) {
                img.getGraphics().drawImage(im, 0, p.offset, p.thumbnailWidth, p.thumbnailHeight, null);
                img.getGraphics().fillRect(0, 0, startWidth, p.offset);
                img.getGraphics().fillRect(0, p.offset + p.thumbnailHeight, startWidth, p.offset);
            } else {
                img.getGraphics().drawImage(im, p.offset, 0, p.thumbnailWidth, p.thumbnailHeight, null);
                img.getGraphics().fillRect(0, 0, p.offset, startHeight);
                img.getGraphics().fillRect(p.offset + p.thumbnailWidth, 0, p.offset, startHeight);
            }
            img.getGraphics().setColor(cl);
        } else {
            img.getGraphics().drawImage(im, 0 , 0, null);
        }

        writeThumbnail2(img, thumbnailType, thumbnailPath, startWidth, startHeight);
    }

    private static void writeThumbnail2(BufferedImage img, String thumbnailType, String thumbnailPath,
        int thumbnailWidth, int thumbnailHeight) throws IOException, URISyntaxException {

        Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(thumbnailType);
        if (iter.hasNext()) {
            ImageWriter writer = iter.next();
            ImageWriteParam iwp = writer.getDefaultWriteParam();

            iwp.setSourceRegion(new Rectangle(thumbnailWidth, thumbnailHeight));

            ImageOutputStream output = rp.getImageOutputStream(thumbnailPath);
            writer.setOutput(output);

            IIOImage iioImage = new IIOImage(img, null, null);
            writer.write(null, iioImage, iwp);
            if (output != null) {
                output.close();
            }
        }
    }

    public static BufferedImage loadImage(byte[] imgBytes, java.util.List<Exception> eList) {
        BufferedImage img = null;
        Iterator<ImageReader> readerIt = getImageReader(imgBytes);
        while (readerIt.hasNext()) {
            try {
                ImageReader reader = readerIt.next();
                img = reader.read(0);
                reader = null;
                break;
            } catch (Exception e) {
                eList.add(e);
            }
        }
        return img;
    }

    private static String prepareIM(String imagePath, String thumbnailDir, String imFilter, StringBuilder sb) {

        sb.append(IM_CONVERT).append(rp.getRealFilePath(imagePath));

        if (imFilter != null && imFilter.length() > 0) {
            sb.append(IM_FILTER).append(imFilter);
        }

        String realThumbnailDir = rp.getRealFilePath(thumbnailDir);
        File tDir = new File(realThumbnailDir);
        if (!tDir.exists()) {
            tDir.mkdir();
        }

        return realThumbnailDir;
    }

    public static Iterator<ImageReader> getImageReader(final byte[] image) {
        ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image));

        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }

        IIORegistry theRegistry = IIORegistry.getDefaultInstance();

        try {
            final Iterator<ImageReaderSpi> iter = theRegistry.getServiceProviders(ImageReaderSpi.class,
                    new CanDecodeInputFilter(input),
                    true);

            return new ImageReaderIterator(iter, image);

        } catch (IllegalArgumentException e) {
            LOG.error(e, e);
        }

        return new Iterator<ImageReader>() {
            public boolean hasNext() {
                return false;
            }

            public ImageReader next() {
                return null;
            }

            public void remove() {
            }
        };
    }

    public static BufferedImage loadSvgImage(byte[] imageBytes, List<Exception> eList) {
        SVGTranscoder transcoder = new SVGTranscoder();
        transcoder.setTranscodingHints(SVG_TRANSCODING_HINTS);
        try {
            transcoder.transcode(new TranscoderInput(new ByteArrayInputStream(imageBytes)), null);
            return transcoder.getImage();
        } catch (Exception e) {
            eList.add(e);
            return null;
        }
    }

    static class CanDecodeInputFilter implements ServiceRegistry.Filter {
        Object input;

        public CanDecodeInputFilter(Object input) {
            this.input = input;
        }

        public boolean filter(Object elt) {
            try {
                ImageReaderSpi spi = (ImageReaderSpi) elt;
                ImageInputStream stream = null;
                if (input instanceof ImageInputStream) {
                    stream = (ImageInputStream) input;
                }
                if (stream != null) {
                    stream.mark();
                }
                boolean canDecode = spi.canDecodeInput(input);
                if (stream != null) {
                    stream.reset();
                }

                return canDecode;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static class ThumbnailParams {

        int firstThumbnailSize;
        int secondThumbnailSize;
        int offset = 0;
        int thumbnailWidth;
        int thumbnailHeight;
        int diffPercent;

        ThumbnailParams(int diffPercent) {
            this.diffPercent = diffPercent;
        }

        void calculateThumbnailSize(int startWidth, int startHeight, int width, int height) {

            if (width > height) {

                calculateThumbnailSize(startWidth, startHeight, height);
                thumbnailWidth = firstThumbnailSize;
                thumbnailHeight = secondThumbnailSize;
            } else {

                calculateThumbnailSize(startHeight,  startWidth, width);
                thumbnailWidth = secondThumbnailSize;
                thumbnailHeight = firstThumbnailSize;
            }
        }

        void calculateThumbnailSize(int startFirstSize, int startSecondSize, int realSize) {

            firstThumbnailSize = startFirstSize;
            secondThumbnailSize = realSize * startFirstSize / firstThumbnailSize;

            if (secondThumbnailSize > startSecondSize) {
                secondThumbnailSize = startSecondSize;
            } else {
                int diff = startSecondSize - secondThumbnailSize;
                if (diff * diffPercent / startSecondSize > diffPercent) {

                    offset = diff / 2;
                    secondThumbnailSize += startSecondSize - (offset * 2 + secondThumbnailSize);
                } else {
                    secondThumbnailSize = startSecondSize;
                }
            }
        }
    }

    private static class ImageReaderIterator implements Iterator<ImageReader> {
        private final Iterator<ImageReaderSpi> iter;
        private final byte[] image;

        public ImageReaderIterator(Iterator<ImageReaderSpi> iter, byte[] image) {
            this.iter = iter;
            this.image = image;
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public ImageReader next() {
            ImageReaderSpi spi = iter.next();
            ImageReader ir;
            try {
                ir = spi.createReaderInstance();
                ir.setInput(new MemoryCacheImageInputStream(
                        new ByteArrayInputStream(image)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return ir;
        }

        public void remove() {
            iter.remove();
        }
    }

    /** Just SVG Thumbnails Transcoder */
    private static class SVGTranscoder extends ImageTranscoder {
        private BufferedImage image = null;

        @Override
        public BufferedImage createImage(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            return image;
        }

        public BufferedImage getImage() {
            return image;
        }

        @Override
        public void writeImage(BufferedImage bufferedImage, TranscoderOutput out) {
        }
    }
}
