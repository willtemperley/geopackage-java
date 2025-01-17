package mil.nga.geopackage.user;

import java.util.Date;

import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.db.DateConverter;

/**
 * User Row containing the values from a single Result Set row
 * 
 * @param <TColumn>
 *            column type
 * @param <TTable>
 *            table type
 * 
 * @author osbornb
 */
public abstract class UserRow<TColumn extends UserColumn, TTable extends UserTable<TColumn>>
		extends UserCoreRow<TColumn, TTable> {

	/**
	 * Constructor
	 * 
	 * @param table
	 *            table
	 * @param columns
	 *            columns
	 * @param columnTypes
	 *            column types
	 * @param values
	 *            values
	 * @since 3.5.0
	 */
	protected UserRow(TTable table, UserColumns<TColumn> columns,
			int[] columnTypes, Object[] values) {
		super(table, columns, columnTypes, values);
	}

	/**
	 * Constructor to create an empty row
	 * 
	 * @param table
	 *            table
	 */
	protected UserRow(TTable table) {
		super(table);
	}

	/**
	 * Copy Constructor
	 * 
	 * @param userRow
	 *            user row to copy
	 */
	protected UserRow(UserRow<TColumn, TTable> userRow) {
		super(userRow);
	}

	/**
	 * Convert the row to content values
	 * 
	 * @return content values
	 */
	public ContentValues toContentValues() {
		return toContentValues(true);
	}

	/**
	 * Convert the row to content values
	 * 
	 * @param includeNulls
	 *            include null values
	 * 
	 * @return content values
	 */
	public ContentValues toContentValues(boolean includeNulls) {

		ContentValues contentValues = new ContentValues();
		for (TColumn column : columns.getColumns()) {

			Object value = values[column.getIndex()];

			if (!column.isPrimaryKey()
					|| (value != null && columns.isPkModifiable())) {

				String columnName = column.getName();

				if (value != null) {
					columnToContentValue(contentValues, column, value);
				} else if (includeNulls) {
					contentValues.putNull(columnName);
				}

			}

		}

		if (contentValues.size() == 0) {
			for (TColumn column : columns.getColumns()) {
				if (!column.isPrimaryKey()) {
					contentValues.putNull(column.getName());
				}
			}
		}

		return contentValues;
	}

	/**
	 * Map the column to the content values
	 * 
	 * @param contentValues
	 *            content values
	 * @param column
	 *            column
	 * @param value
	 *            value
	 */
	protected void columnToContentValue(ContentValues contentValues,
			TColumn column, Object value) {

		String columnName = column.getName();

		if (value instanceof Number) {
			if (value instanceof Byte) {
				validateValue(column, value, Byte.class, Short.class,
						Integer.class, Long.class);
				contentValues.put(columnName, (Byte) value);
			} else if (value instanceof Short) {
				validateValue(column, value, Short.class, Integer.class,
						Long.class);
				contentValues.put(columnName, (Short) value);
			} else if (value instanceof Integer) {
				validateValue(column, value, Integer.class, Long.class,
						Byte.class, Short.class);
				contentValues.put(columnName, (Integer) value);
			} else if (value instanceof Long) {
				validateValue(column, value, Long.class, Double.class);
				contentValues.put(columnName, (Long) value);
			} else if (value instanceof Float) {
				validateValue(column, value, Float.class);
				contentValues.put(columnName, (Float) value);
			} else if (value instanceof Double) {
				validateValue(column, value, Double.class);
				contentValues.put(columnName, (Double) value);
			} else {
				throw new GeoPackageException("Unsupported Number type: "
						+ value.getClass().getSimpleName());
			}
		} else if (value instanceof String) {
			validateValue(column, value, String.class);
			String stringValue = (String) value;
			if (column.getMax() != null
					&& stringValue.length() > column.getMax()) {
				throw new GeoPackageException(
						"String is larger than the column max. Size: "
								+ stringValue.length() + ", Max: "
								+ column.getMax() + ", Column: " + columnName);
			}
			contentValues.put(columnName, stringValue);
		} else if (value instanceof byte[]) {
			validateValue(column, value, byte[].class);
			byte[] byteValue = (byte[]) value;
			if (column.getMax() != null && byteValue.length > column.getMax()) {
				throw new GeoPackageException(
						"Byte array is larger than the column max. Size: "
								+ byteValue.length + ", Max: " + column.getMax()
								+ ", Column: " + columnName);
			}
			contentValues.put(columnName, byteValue);
		} else if (value instanceof Boolean) {
			validateValue(column, value, Boolean.class);
			Boolean booleanValue = (Boolean) value;
			short shortBoolean = booleanValue ? (short) 1 : (short) 0;
			contentValues.put(columnName, shortBoolean);
		} else if (value instanceof Date) {
			validateValue(column, value, Date.class, String.class);
			Date dateValue = (Date) value;
			DateConverter converter = DateConverter
					.converter(column.getDataType());
			String dateString = converter.stringValue(dateValue);
			contentValues.put(columnName, dateString);
		} else {
			throw new GeoPackageException(
					"Unsupported update column value. column: " + columnName
							+ ", value: " + value);
		}
	}

}
