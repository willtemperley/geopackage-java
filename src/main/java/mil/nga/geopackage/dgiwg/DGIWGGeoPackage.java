package mil.nga.geopackage.dgiwg;

import java.io.File;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageImpl;
import mil.nga.geopackage.db.GeoPackageConnection;

/**
 * DGIWG (Defence Geospatial Information Working Group) GeoPackage
 * implementation
 * 
 * @author osbornb
 * @since 6.1.2
 */
public class DGIWGGeoPackage extends GeoPackageImpl {

	/**
	 * DGIWG File Name
	 */
	private GeoPackageFileName fileName;

	/**
	 * Constructor
	 * 
	 * @param geoPackage
	 *            GeoPackage
	 */
	public DGIWGGeoPackage(GeoPackage geoPackage) {
		super(geoPackage.getName(), geoPackage.getPath(),
				geoPackage.getConnection());
		this.fileName = new GeoPackageFileName(geoPackage.getPath());
	}

	/**
	 * Constructor
	 * 
	 * @param fileName
	 *            DGIWG file name
	 * @param geoPackage
	 *            GeoPackage
	 */
	public DGIWGGeoPackage(GeoPackageFileName fileName, GeoPackage geoPackage) {
		super(geoPackage.getName(), geoPackage.getPath(),
				geoPackage.getConnection());
		this.fileName = fileName;
	}

	/**
	 * Constructor
	 *
	 * @param name
	 *            GeoPackage name
	 * @param file
	 *            GeoPackage file
	 * @param database
	 *            connection
	 */
	protected DGIWGGeoPackage(String name, File file,
			GeoPackageConnection database) {
		super(name, file, database);
		this.fileName = new GeoPackageFileName(file);
	}

	/**
	 * Get the DGIWG file name
	 * 
	 * @return DGIWG file name
	 */
	public GeoPackageFileName getFileName() {
		return fileName;
	}

}
