package de.terrestris.shogun2.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.terrestris.shogun2.dao.ImageFileDao;
import de.terrestris.shogun2.model.ImageFile;

/**
 * Service class for the {@link ImageFile} model.
 *
 * @author Daniel Koch
 * @author Johannes Weskamm
 *
 */
@Service("imageFileService")
public class ImageFileService<E extends ImageFile, D extends ImageFileDao<E>>
		extends FileService<E, D> {

	/**
	 * Default constructor, which calls the type-constructor
	 */
	@SuppressWarnings("unchecked")
	public ImageFileService() {
		this((Class<E>) ImageFile.class);
	}

	/**
	 * Constructor that sets the concrete entity class for the service.
	 * Subclasses MUST call this constructor.
	 */
	protected ImageFileService(Class<E> entityClass) {
		super(entityClass);
	}

	/**
	 * We have to use {@link Qualifier} to define the correct dao here.
	 * Otherwise, spring can not decide which dao has to be autowired here
	 * as there are multiple candidates.
	 */
	@Override
	@Autowired
	@Qualifier("imageFileDao")
	public void setDao(D dao) {
		this.dao = dao;
	}

	/**
	 * Method persists a given Image as a bytearray in the database
	 *
	 * @param file
	 * @param resize
	 * @param imageSize
	 * @return
	 * @throws Exception
	 */
	public E uploadImage(MultipartFile file, boolean createThumbnail, Integer dimensions)
			throws Exception {

		InputStream is = null;
		ByteArrayInputStream bais = null;
		E imageToPersist = null;

		try {
			is = file.getInputStream();
			byte[] imageByteArray = IOUtils.toByteArray(is);

			// create a new instance (generic)
			imageToPersist = getEntityClass().newInstance();

			// create a thumbnail if requested
			if (createThumbnail) {
				byte[] thumbnail = scaleImage(
					imageByteArray,
					FilenameUtils.getExtension(file.getOriginalFilename()),
					dimensions);
				imageToPersist.setThumbnail(thumbnail);
			}

			// set binary image data
			imageToPersist.setFile(imageByteArray);

			// detect dimensions
			bais = new ByteArrayInputStream(imageByteArray);

			BufferedImage bimg = ImageIO.read(bais);

			// set basic image properties
			imageToPersist.setWidth(bimg.getWidth());
			imageToPersist.setHeight(bimg.getHeight());
			imageToPersist.setFileType(file.getContentType());
			imageToPersist.setFileName(file.getOriginalFilename());

			// persist the image
			dao.saveOrUpdate((E) imageToPersist);

		} catch(Exception e) {
			throw new Exception("Could not create the Image in DB: "
					+ e.getMessage());
		} finally {
			IOUtils.closeQuietly(is);
			IOUtils.closeQuietly(bais);
		}

		return imageToPersist;
	}

	/**
	 * Scales an image by the given dimensions
	 *
	 * @param is
	 * @param outputFormat
	 * @param outputSize
	 * @return
	 * @throws Exception
	 */
	public static byte[] scaleImage(byte[] imageBytes, String outputFormat,
			Integer outputSize) throws Exception {

		InputStream is = null;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] imageInBytes = null;
		BufferedImage image = null;
		BufferedImage resizedImage = null;

		try {
			is = new ByteArrayInputStream(imageBytes);
			image = ImageIO.read(is);
			resizedImage = Scalr.resize(image, outputSize);
			ImageIO.write(resizedImage, outputFormat, baos);
			imageInBytes = baos.toByteArray();
		} catch(Exception e) {
			throw new Exception("Error on resizing an image: " + e.getMessage());
		} finally {
			IOUtils.closeQuietly(is);
			IOUtils.closeQuietly(baos);
			if (image != null) {
				image.flush();
			}
			if (resizedImage != null) {
				resizedImage.flush();
			}
		}
		return imageInBytes;
	}

}
